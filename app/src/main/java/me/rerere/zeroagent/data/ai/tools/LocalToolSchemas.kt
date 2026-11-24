package me.rerere.zeroagent.data.ai.tools

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema

/** Request shapes for built-in tools supplied directly by LocalTools. */
internal object LocalToolSchemas {
    fun evalJavaScript(): InputSchema =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("code", buildJsonObject {
                    put("type", "string")
                    put("description", "The JavaScript code to execute")
                })
            },
            required = listOf("code")
        )

    fun deviceTime(): InputSchema =
        InputSchema.Obj(
            properties = buildJsonObject { }
        )

    fun clipboard(): InputSchema =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put(
                        "enum",
                        kotlinx.serialization.json.buildJsonArray {
                            add("read")
                            add("write")
                        }
                    )
                    put("description", "Operation to perform: read or write")
                })
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to write to the clipboard (required for write)")
                })
            },
            required = listOf("action")
        )

    fun speak(): InputSchema =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "The text to speak aloud")
                })
            },
            required = listOf("text")
        )

    fun askUser(): InputSchema =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("questions", buildJsonObject {
                    put("type", "array")
                    put("description", "List of questions to ask the user")
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("id", buildJsonObject {
                                put("type", "string")
                                put("description", "Unique identifier for this question")
                            })
                            put("question", buildJsonObject {
                                put("type", "string")
                                put("description", "The question text to display to the user")
                            })
                            put("options", buildJsonObject {
                                put("type", "array")
                                put(
                                    "description",
                                    "Optional list of suggested options for the user to choose from"
                                )
                                put("items", buildJsonObject {
                                    put("type", "string")
                                })
                            })
                            put("selection_type", buildJsonObject {
                                put("type", "string")
                                put(
                                    "enum",
                                    kotlinx.serialization.json.buildJsonArray {
                                        add("text")
                                        add("single")
                                        add("multi")
                                    }
                                )
                                put(
                                    "description",
                                    "Answer type: text (free text input, default), single (select exactly one option), multi (select one or more options)"
                                )
                            })
                        })
                        put("required", kotlinx.serialization.json.buildJsonArray {
                            add("id")
                            add("question")
                        })
                    })
                })
            },
            required = listOf("questions")
        )
}
