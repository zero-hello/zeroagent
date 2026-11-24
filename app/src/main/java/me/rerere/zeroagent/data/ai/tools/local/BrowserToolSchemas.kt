package me.rerere.zeroagent.data.ai.tools.local

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema

/** Request shapes for the browser tools. Execution stays in BrowserTools. */
internal object BrowserToolSchemas {
    fun open(): InputSchema =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "The full URL to navigate to (https://...)")
                })
            },
            required = listOf("url"),
        )

    fun currentUrl(): InputSchema = InputSchema.Obj(properties = buildJsonObject { })

    fun screenshot(): InputSchema =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("full_page", buildJsonObject {
                    put("type", "boolean")
                    put("description", "If true, attempt to capture the entire scroll height (currently no-op; viewport-only)")
                })
            },
        )

    fun getText(): InputSchema = getTextSchema(defaultMax = 8000)

    fun getDom(): InputSchema = selectorAndMaxCharsSchema(defaultMax = 4000, required = false)

    fun getLinks(): InputSchema =
        InputSchema.Obj(properties = buildJsonObject {
            put("selector", buildJsonObject {
                put("type", "string")
                put("description", "Root selector to search inside (default 'body')")
            })
        })

    fun back(): InputSchema = InputSchema.Obj(properties = buildJsonObject { })

    fun forward(): InputSchema = InputSchema.Obj(properties = buildJsonObject { })

    fun waitFor(): InputSchema =
        InputSchema.Obj(properties = buildJsonObject {
            put("selector", buildJsonObject {
                put("type", "string")
                put("description", "CSS selector to wait for")
            })
            put("timeout_ms", buildJsonObject {
                put("type", "integer")
                put("description", "Max wait in ms (default 10000, capped at the configured per-tool timeout)")
            })
            put("contains_text", buildJsonObject {
                put("type", "string")
                put("description", "Optional — wait until an element matching the selector contains this text (case-sensitive substring). Ignored for state=detached/hidden.")
            })
            put("state", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray { add("attached"); add("detached"); add("visible"); add("hidden") })
                put("description", "Target state to wait for (default 'attached', the original presence-in-DOM behavior)")
            })
        }, required = listOf("selector"))

    fun click(): InputSchema = selectorWithFullSchema("CSS selector to click")

    fun type(): InputSchema =
        InputSchema.Obj(properties = buildJsonObject {
            put("selector", buildJsonObject { put("type","string"); put("description","CSS selector of the input") })
            put("text", buildJsonObject { put("type","string"); put("description","Text to type") })
            put("clear", buildJsonObject { put("type","boolean"); put("description","Clear the field first (default true)") })
            put("full", buildJsonObject { put("type","boolean"); put("description","If true, return the action envelope without the page-text diff (default false)") })
        }, required = listOf("selector","text"))

    fun scroll(): InputSchema =
        InputSchema.Obj(properties = buildJsonObject {
            put("direction", buildJsonObject {
                put("type","string")
                put("enum", buildJsonArray { add("up"); add("down"); add("top"); add("bottom") })
            })
            put("amount", buildJsonObject { put("type","integer"); put("description","Scroll distance in px (default 600)") })
        }, required = listOf("direction"))

    fun submit(): InputSchema = selectorWithFullSchema("CSS selector of a submit button or any element inside the target form")

    fun select(): InputSchema =
        InputSchema.Obj(properties = buildJsonObject {
            put("selector", buildJsonObject { put("type","string"); put("description","CSS selector of the <select>") })
            put("value", buildJsonObject { put("type","string"); put("description","The option value to set") })
            put("full", buildJsonObject { put("type","boolean"); put("description","If true, return the action envelope without the page-text diff (default false)") })
        }, required = listOf("selector","value"))

    fun pressKey(): InputSchema =
        InputSchema.Obj(properties = buildJsonObject {
            put("key", buildJsonObject {
                put("type","string")
                put("description","KeyboardEvent.key value (e.g. 'Enter', 'Escape', 'ArrowDown')")
            })
            put("full", buildJsonObject { put("type","boolean"); put("description","If true, return the action envelope without the page-text diff (default false)") })
        }, required = listOf("key"))

    fun evalJs(): InputSchema =
        InputSchema.Obj(properties = buildJsonObject {
            put("code", buildJsonObject {
                put("type","string")
                put("description","JavaScript to evaluate. The string returned by the WebView is the value of the last expression, JSON-encoded.")
            })
        }, required = listOf("code"))

    fun clickAndRead(): InputSchema =
        InputSchema.Obj(properties = buildJsonObject {
            put("selector", buildJsonObject {
                put("type","string")
                put("description","CSS selector to click")
            })
            put("extract_mode", buildJsonObject {
                put("type","string")
                put("enum", buildJsonArray { add("diff"); add("auto"); add("readability"); add("raw") })
                put("description","diff (default) returns a before/after diff; auto/readability/raw return the extracted page text")
            })
            put("max_chars", buildJsonObject {
                put("type","integer")
                put("description","Caps the returned text length (default 4000)")
            })
        }, required = listOf("selector"))

    fun done(): InputSchema =
        InputSchema.Obj(properties = buildJsonObject {
            put("summary", buildJsonObject {
                put("type","string")
                put("description","One-sentence summary of what was accomplished")
            })
            put("result_url", buildJsonObject {
                put("type","string")
                put("description","Optional URL the user should look at")
            })
        }, required = listOf("summary"))
}

private fun selectorOnlySchema(description: String): InputSchema = InputSchema.Obj(
    properties = buildJsonObject {
        put("selector", buildJsonObject {
            put("type", "string")
            put("description", description)
        })
    },
    required = listOf("selector"),
)

/**
 * selector + optional full flag — used by the state-changing tools (click / submit)
 * that gain the diff-after-action toggle. selector remains required; `full` defaults
 * to false (i.e. return diff). The naming matches the spec verbatim.
 */
private fun selectorWithFullSchema(description: String): InputSchema = InputSchema.Obj(
    properties = buildJsonObject {
        put("selector", buildJsonObject {
            put("type", "string")
            put("description", description)
        })
        put("full", buildJsonObject {
            put("type", "boolean")
            put("description", "If true, return the action envelope without the page-text diff (default false)")
        })
    },
    required = listOf("selector"),
)

private fun selectorAndMaxCharsSchema(defaultMax: Int, required: Boolean): InputSchema = InputSchema.Obj(
    properties = buildJsonObject {
        put("selector", buildJsonObject {
            put("type", "string")
            put("description", "CSS selector (default 'body')")
        })
        put("max_chars", buildJsonObject {
            put("type", "integer")
            put("description", "Truncation cap (default $defaultMax)")
        })
    },
    required = if (required) listOf("selector") else null,
)

/**
 * Schema for browser_get_text. Adds the [extract_mode] enum (auto / readability /
 * raw) to the standard selector + max_chars surface. Defaults are documented inline
 * so the LLM doesn't need to hunt through the spec to know the fallback behaviour.
 */
private fun getTextSchema(defaultMax: Int): InputSchema = InputSchema.Obj(
    properties = buildJsonObject {
        put("selector", buildJsonObject {
            put("type", "string")
            put("description", "Optional CSS selector — when set, overrides Readability and reads the selector's innerText directly")
        })
        put("max_chars", buildJsonObject {
            put("type", "integer")
            put("description", "Truncation cap (default $defaultMax)")
        })
        put("extract_mode", buildJsonObject {
            put("type", "string")
            put("enum", buildJsonArray { add("auto"); add("readability"); add("raw") })
            put("description", "auto (default) tries Readability then falls back; readability forces it; raw uses selector-based innerText")
        })
    },
)
