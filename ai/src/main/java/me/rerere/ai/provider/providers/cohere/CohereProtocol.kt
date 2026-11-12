package me.rerere.ai.provider.providers.cohere

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.*
import me.rerere.ai.ui.*

/** Native Cohere v2 wire format, independent of Chat Completions. */
internal object CohereProtocol {
    fun request(messages: List<UIMessage>, params: TextGenerationParams, stream: Boolean): JsonObject = buildJsonObject {
        put("model", params.model.modelId)
        put("stream", stream)
        putJsonArray("messages") {
            messages.forEach { message ->
                require(message.parts.none { it is UIMessagePart.Image || it is UIMessagePart.Audio || it is UIMessagePart.Video || it is UIMessagePart.Document }) {
                    "此 Cohere 連線目前只支援文字輸入，請切換支援附件的供應商。"
                }
                val text = message.toText()
                val tools = message.parts.filterIsInstance<UIMessagePart.Tool>()
                if (text.isNotBlank() || tools.isNotEmpty()) add(buildJsonObject {
                    put("role", when (message.role) {
                        MessageRole.SYSTEM -> "system"
                        MessageRole.USER -> "user"
                        else -> "assistant"
                    })
                    if (text.isNotBlank()) put("content", text)
                    if (tools.isNotEmpty()) putJsonArray("tool_calls") {
                        tools.forEach { tool -> add(buildJsonObject {
                            put("id", tool.toolCallId)
                            put("type", "function")
                            putJsonObject("function") { put("name", tool.toolName); put("arguments", tool.input.ifBlank { "{}" }) }
                        }) }
                    }
                })
                tools.filter { it.isExecuted }.forEach { tool -> add(buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", tool.toolCallId)
                    put("content", tool.output.joinToString("\n") { part ->
                        when (part) {
                            is UIMessagePart.Text -> part.text
                            else -> "[此工具的非文字輸出無法傳給目前的 Cohere 模型]"
                        }
                    })
                }) }
            }
        }
        params.temperature?.let { put("temperature", it) }
        params.topP?.let { put("p", it) }
        params.topK?.let { put("k", it) }
        params.maxTokens?.let { put("max_tokens", it) }
        if (ModelAbility.TOOL in params.model.abilities && params.tools.isNotEmpty()) putJsonArray("tools") {
            params.tools.forEach { tool -> add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", tool.name); put("description", tool.description)
                    put("parameters", buildJsonObject {
                        put("type", "object")
                        val schema = tool.parameters() as? InputSchema.Obj
                        put("properties", schema?.properties ?: JsonObject(emptyMap()))
                        schema?.required?.let { put("required", JsonArray(it.map(::JsonPrimitive))) }
                    })
                }
            }) }
        }
    }

    fun usage(value: JsonObject?): TokenUsage? {
        val tokens = value?.get("tokens") as? JsonObject ?: return null
        val input = tokens["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val output = tokens["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        return TokenUsage(promptTokens = input, completionTokens = output, totalTokens = input + output)
    }

    fun response(value: JsonObject, model: Model): TextGenerationResult {
        val message = value["message"]?.jsonObject ?: error("Cohere 沒有回傳訊息。")
        val parts = mutableListOf<UIMessagePart>()
        (message["content"] as? JsonArray)?.forEach { item ->
            val obj = item.jsonObject
            if (obj["type"]?.jsonPrimitive?.content == "text") parts += UIMessagePart.Text(obj["text"]?.jsonPrimitive?.content.orEmpty())
        }
        (message["tool_calls"] as? JsonArray)?.forEach { item ->
            val tool = item.jsonObject
            val function = tool["function"]?.jsonObject ?: error("Cohere 工具呼叫缺少 function。")
            parts += UIMessagePart.Tool(tool["id"]!!.jsonPrimitive.content, function["name"]!!.jsonPrimitive.content,
                function["arguments"]?.let { if (it is JsonPrimitive) it.content else it.toString() } ?: "{}")
        }
        val reason = value["finish_reason"]?.jsonPrimitive?.contentOrNull
        require(reason !in setOf("ERROR", "TIMEOUT")) { "Cohere 回覆未完成：$reason" }
        return TextGenerationResult(value["id"]?.jsonPrimitive?.content.orEmpty(), model.modelId,
            UIMessage(role = MessageRole.ASSISTANT, parts = parts, modelId = model.id), reason, usage(value["usage"] as? JsonObject))
    }
}

/** Each stream owns its event indexes; parallel chats never share decoder state. */
internal class CohereStreamDecoder(private val model: String) {
    private val toolIds = mutableMapOf<Int, String>()
    private val textIds = mutableSetOf<Int>()
    var finished = false
        private set
    fun decode(event: JsonObject): List<StreamChunk> = buildList {
        val type = event["type"]?.jsonPrimitive?.content.orEmpty()
        val index = event["index"]?.jsonPrimitive?.intOrNull ?: 0
        val delta = event["delta"] as? JsonObject
        val message = delta?.get("message") as? JsonObject
        val content = message?.get("content") as? JsonObject
        val tool = message?.get("tool_calls") as? JsonObject
        when (type) {
            "content-start" -> {
                if (textIds.add(index)) add(StreamChunk.TextStart("cohere-text-$index"))
                content?.get("text")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let { add(StreamChunk.TextDelta("cohere-text-$index", it)) }
            }
            "content-delta" -> {
                val text = content?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (textIds.add(index)) add(StreamChunk.TextStart("cohere-text-$index"))
                if (text.isNotEmpty()) add(StreamChunk.TextDelta("cohere-text-$index", text))
            }
            "content-end" -> if (textIds.remove(index)) add(StreamChunk.TextEnd("cohere-text-$index"))
            "tool-call-start" -> {
                val id = tool?.get("id")?.jsonPrimitive?.contentOrNull ?: error("Cohere 工具呼叫缺少 ID。")
                toolIds[index] = id
                val function = tool["function"] as? JsonObject
                add(StreamChunk.ToolCallStart(id, function?.get("name")?.jsonPrimitive?.content.orEmpty()))
                function?.get("arguments")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let { add(StreamChunk.ToolCallDelta(id, inputDelta = it)) }
            }
            "tool-call-delta" -> {
                val id = toolIds[index] ?: error("Cohere 工具片段缺少起始事件。")
                val function = tool?.get("function") as? JsonObject
                add(StreamChunk.ToolCallDelta(id, inputDelta = function?.get("arguments")?.jsonPrimitive?.contentOrNull.orEmpty()))
            }
            "tool-call-end" -> toolIds.remove(index)?.let { add(StreamChunk.ToolCallEnd(it)) }
            "message-end" -> {
                val reason = delta?.get("finish_reason")?.jsonPrimitive?.contentOrNull
                require(reason !in setOf("ERROR", "TIMEOUT") && delta?.get("error")?.jsonPrimitive?.contentOrNull.isNullOrBlank()) { "Cohere 串流回覆未完成：${reason.orEmpty()}" }
                textIds.forEach { add(StreamChunk.TextEnd("cohere-text-$it")) }; textIds.clear()
                toolIds.values.forEach { add(StreamChunk.ToolCallEnd(it)) }; toolIds.clear()
                CohereProtocol.usage(delta?.get("usage") as? JsonObject)?.let { add(StreamChunk.Usage(it)) }
                add(StreamChunk.Finish(finishReason = reason, model = model))
                finished = true
            }
            "error" -> error("Cohere 串流回傳錯誤，請檢查帳戶及模型設定。")
        }
    }
}
