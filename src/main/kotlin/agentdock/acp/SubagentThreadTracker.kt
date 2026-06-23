package agentdock.acp

import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

private val SUBAGENT_LOG = logger<SubagentThreadRegistry>()
private val OUTPUT_TEXT_KEYS = listOf("output", "formatted_output", "aggregated_output", "stdout", "stderr", "message", "content")

internal data class SubagentThread(
    val id: String,
    val agentName: String,
    var title: String,
    var status: String,
    var output: String?
)

internal class SubagentThreadRegistry {
    private val detector: SubagentDetector = CompositeSubagentDetector(
        listOf(GenericSubagentDetector, OpenCodeSubagentDetector, CodexSubagentDetector)
    )
    private val threads = ConcurrentHashMap<String, SubagentThread>()

    fun onToolCall(toolCallId: String, rawJson: String): List<SubagentThread> {
        val toolCall = parseToolCall(rawJson) ?: run {
            SUBAGENT_LOG.info("Subagent tracker ignored unparsable tool call id=$toolCallId raw=${rawJson.take(500)}")
            return snapshot()
        }
        val event = detector.detect(toolCall, allowHeuristics = false)
        logDetection("tool_call", toolCallId, toolCall, event)
        event ?: return snapshot()
        register(toolCallId, event)
        return snapshot()
    }

    fun onToolCallUpdate(toolCallId: String, rawJson: String): List<SubagentThread> {
        val toolCall = parseToolCall(rawJson)
        val thread = threads[toolCallId] ?: run {
            val event = toolCall?.let { detector.detect(it, allowHeuristics = true) }
            if (toolCall != null) {
                logDetection("tool_call_update_new", toolCallId, toolCall, event)
            }
            event ?: return snapshot()
            register(toolCallId, event)
            threads[toolCallId] ?: return snapshot()
        }

        val status = toolCall?.status
        if (status != null) {
            thread.status = mapStatus(status)
        }
        val updated = toolCall?.let { detector.detect(it, allowHeuristics = true) }
        if (updated != null && updated.title.isNotBlank() && updated.title != "task") {
            thread.title = updated.title
        }
        val output = toolCall?.output
        if (!output.isNullOrBlank()) {
            thread.output = output
        }
        if (toolCall != null) {
            logDetection("tool_call_update_existing", toolCallId, toolCall, updated)
        }
        return snapshot()
    }

    fun clear() {
        threads.clear()
    }

    fun snapshot(): List<SubagentThread> = threads.values.toList().sortedBy { it.id }

    private fun register(toolCallId: String, event: DetectedSubagent) {
        SUBAGENT_LOG.info("Subagent tracker registered id=$toolCallId agent=${event.agentName} title=${event.title}")
        threads[toolCallId] = SubagentThread(
            id = toolCallId,
            agentName = event.agentName,
            title = event.title,
            status = "running",
            output = null
        )
    }

    private fun logDetection(stage: String, toolCallId: String, toolCall: ParsedToolCall, event: DetectedSubagent?) {
        SUBAGENT_LOG.info(
            "Subagent detector $stage id=$toolCallId kind=${toolCall.kind} tool=${toolCall.tool} title=${toolCall.title} " +
                "status=${toolCall.status.orEmpty()} rawInputKeys=${toolCall.rawInput.keys.joinToString()} " +
                "hasPrompt=${!toolCall.prompt.isNullOrBlank()} agentPath=${toolCall.agentPath.orEmpty()} " +
                "hasOutput=${!toolCall.output.isNullOrBlank()} detected=${event != null} " +
                "agent=${event?.agentName.orEmpty()} detectedTitle=${event?.title.orEmpty()}"
        )
    }
}

private data class ParsedToolCall(
    val kind: String,
    val tool: String,
    val title: String,
    val status: String?,
    val rawInput: Map<String, String>,
    val output: String?,
    val prompt: String?,
    val agentPath: String?
)

private data class DetectedSubagent(val agentName: String, val title: String)

private interface SubagentDetector {
    fun detect(toolCall: ParsedToolCall, allowHeuristics: Boolean): DetectedSubagent?
}

private object GenericSubagentDetector : SubagentDetector {
    override fun detect(toolCall: ParsedToolCall, allowHeuristics: Boolean): DetectedSubagent? {
        val input = toolCall.rawInput
        val agentName = input["subagent_type"]
            ?: input["subagentType"]
            ?: input["agent"]
            ?: input["agent_name"]
            ?: return null
        val title = input["description"] ?: input["prompt"] ?: toolCall.prompt ?: toolCall.title
        if (title.isBlank()) return null
        return DetectedSubagent(agentName, title)
    }
}

private object OpenCodeSubagentDetector : SubagentDetector {
    override fun detect(toolCall: ParsedToolCall, allowHeuristics: Boolean): DetectedSubagent? {
        if (!allowHeuristics) return null
        if (toolCall.kind != "think") return null
        if (toolCall.title != "task" && !toolCall.rawInput.containsKey("description") && !toolCall.rawInput.containsKey("prompt")) return null
        val title = toolCall.rawInput["description"] ?: toolCall.rawInput["prompt"] ?: toolCall.title
        if (title.isBlank()) return null
        return DetectedSubagent(agentName = "subagent", title = title)
    }
}

private object CodexSubagentDetector : SubagentDetector {
    private val collabTools = setOf("SpawnAgent", "SendInput", "ResumeAgent", "Wait", "CloseAgent")

    override fun detect(toolCall: ParsedToolCall, allowHeuristics: Boolean): DetectedSubagent? {
        if (!allowHeuristics) return null
        val input = toolCall.rawInput
        val agentName = input["agent_type"]
            ?: input["agent_role"]
            ?: input["agent_nickname"]
            ?: toolCall.agentPath?.substringAfterLast('/')
            ?: if (toolCall.tool in collabTools || (!toolCall.prompt.isNullOrBlank() && toolCall.tool.isNotBlank())) "subagent" else null
            ?: return null
        val title = input["description"] ?: input["prompt"] ?: toolCall.prompt ?: toolCall.title
        if (title.isBlank()) return null
        return DetectedSubagent(agentName, title)
    }
}

private class CompositeSubagentDetector(private val detectors: List<SubagentDetector>) : SubagentDetector {
    override fun detect(toolCall: ParsedToolCall, allowHeuristics: Boolean): DetectedSubagent? {
        return detectors.firstNotNullOfOrNull { it.detect(toolCall, allowHeuristics) }
    }
}

private fun parseToolCall(rawJson: String): ParsedToolCall? {
    val parsed = try { Json.parseToJsonElement(rawJson).jsonObject } catch (_: Exception) { return null }
    return ParsedToolCall(
        kind = parsed["kind"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        tool = parsed["tool"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        title = parsed["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        status = parsed["status"]?.jsonPrimitive?.contentOrNull,
        rawInput = parsed["rawInput"]?.let { parseRawInput(it) }.orEmpty(),
        output = parsed["rawOutput"]?.let { extractOutputText(it) },
        prompt = parsed["prompt"]?.jsonPrimitive?.contentOrNull,
        agentPath = parsed["agentPath"]?.jsonPrimitive?.contentOrNull
            ?: parsed["agent_path"]?.jsonPrimitive?.contentOrNull
    )
}

private fun parseRawInput(element: JsonElement): Map<String, String> {
    val obj = when (element) {
        is JsonObject -> element
        is JsonPrimitive -> {
            val content = element.contentOrNull ?: return emptyMap()
            runCatching { Json.parseToJsonElement(content).jsonObject }.getOrNull() ?: return emptyMap()
        }
        else -> return emptyMap()
    }
    return obj.entries.mapNotNull { (key, value) ->
        val str = (value as? JsonPrimitive)?.contentOrNull
        if (str != null) key to str else null
    }.toMap()
}

private fun extractOutputText(element: JsonElement): String? {
    return when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> {
            val output = OUTPUT_TEXT_KEYS.asSequence()
                .mapNotNull { key -> (element[key] as? JsonPrimitive)?.contentOrNull }
                .firstOrNull { it.isNotBlank() }
            output ?: element.toString()
        }
        else -> element.toString()
    }
}

private fun mapStatus(status: String): String {
    return when (status.lowercase()) {
        "completed", "completed.", "done", "success" -> "done"
        "error", "failed" -> "error"
        else -> "running"
    }
}

internal fun List<SubagentThread>.toJsonArrayString(): String {
    val arr = buildJsonArray {
        forEach { thread ->
            add(buildJsonObject {
                put("id", thread.id)
                put("agentName", thread.agentName)
                put("title", thread.title)
                put("status", thread.status)
                thread.output?.let { put("output", it) }
            })
        }
    }
    return arr.toString()
}
