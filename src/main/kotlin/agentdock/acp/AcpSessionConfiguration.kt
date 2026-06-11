package agentdock.acp

import com.agentclientprotocol.model.ModelId
import com.agentclientprotocol.model.SessionModeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("OPT_IN_USAGE")
internal suspend fun AcpClientService.setModel(chatId: String, modelId: String): Boolean {
    val context = sessions[chatId] ?: return false
    val trimmedModelId = modelId.trim()
    val adapterName = context.activeAdapterNameRef.get() ?: return false
    if (context.activeModelIdRef.get() == trimmedModelId) {
        AcpAgentPreferencesStore.rememberModel(adapterName, trimmedModelId)
        return true
    }

    val adapterInfo = AcpAdapterPaths.getAdapterInfo(adapterName)
    return when (adapterInfo.modelChangeStrategy) {
        "restart-resume" -> runCatching {
            startAgent(chatId, adapterName, trimmedModelId, context.sessionIdRef.get())
            context.activeModelIdRef.set(trimmedModelId)
            AcpAgentPreferencesStore.rememberModel(adapterName, trimmedModelId)
            adapterRuntimeMetadataMap[adapterName]?.let { metadata ->
                adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentModelId = trimmedModelId)
            }
            true
        }.getOrDefault(false)
        else -> {
            val session = context.session ?: return false
            val applied = runCatching {
                withContext(Dispatchers.IO) {
                    val configId = adapterRuntimeMetadataMap[adapterName]?.modelConfigId
                    val protocol = context.sharedProcess?.protocol
                    val sessionId = context.sessionIdRef.get()
                    if (!configId.isNullOrBlank() && protocol != null && !sessionId.isNullOrBlank()) {
                        protocol.setSessionConfigOptionRaw(sessionId, configId, trimmedModelId)
                    } else {
                        session.setModel(ModelId(trimmedModelId))
                    }
                }
                context.activeModelIdRef.set(trimmedModelId)
                AcpAgentPreferencesStore.rememberModel(adapterName, trimmedModelId)
                adapterRuntimeMetadataMap[adapterName]?.let { metadata ->
                    adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentModelId = trimmedModelId)
                }
                true
            }.getOrDefault(false)
            if (applied) {
                true
            } else if (session.modelsSupported) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        session.setModel(ModelId(trimmedModelId))
                    }
                    context.activeModelIdRef.set(trimmedModelId)
                    AcpAgentPreferencesStore.rememberModel(adapterName, trimmedModelId)
                    adapterRuntimeMetadataMap[adapterName]?.let { metadata ->
                        adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentModelId = trimmedModelId)
                    }
                    true
                }.getOrDefault(false)
            } else {
                false
            }
        }
    }
}

@Suppress("OPT_IN_USAGE")
internal suspend fun AcpClientService.setMode(chatId: String, modeId: String): Boolean {
    val context = sessions[chatId] ?: return false
    val trimmedModeId = modeId.trim()
    val adapterName = context.activeAdapterNameRef.get()
    if (context.activeModeIdRef.get() == trimmedModeId) {
        if (!adapterName.isNullOrBlank()) {
            AcpAgentPreferencesStore.rememberMode(adapterName, trimmedModeId)
        }
        return true
    }

    val session = context.session ?: return false
    val applied = runCatching {
        withContext(Dispatchers.IO) {
            val configId = if (!adapterName.isNullOrBlank()) adapterRuntimeMetadataMap[adapterName]?.modeConfigId else null
            val protocol = context.sharedProcess?.protocol
            val sessionId = context.sessionIdRef.get()
            if (!configId.isNullOrBlank() && protocol != null && !sessionId.isNullOrBlank()) {
                protocol.setSessionConfigOptionRaw(sessionId, configId, trimmedModeId)
            } else {
                session.setMode(SessionModeId(trimmedModeId))
            }
        }
        context.activeModeIdRef.set(trimmedModeId)
        if (!adapterName.isNullOrBlank()) {
            AcpAgentPreferencesStore.rememberMode(adapterName, trimmedModeId)
            adapterRuntimeMetadataMap[adapterName]?.let { metadata ->
                adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentModeId = trimmedModeId)
            }
        }
        true
    }.getOrDefault(false)
    if (applied) return true

    if (!session.modesSupported) return false
    return runCatching {
        withContext(Dispatchers.IO) {
            session.setMode(SessionModeId(trimmedModeId))
        }
        context.activeModeIdRef.set(trimmedModeId)
        if (!adapterName.isNullOrBlank()) {
            AcpAgentPreferencesStore.rememberMode(adapterName, trimmedModeId)
            adapterRuntimeMetadataMap[adapterName]?.let { metadata ->
                adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentModeId = trimmedModeId)
            }
        }
        true
    }.getOrDefault(false)
}

internal suspend fun AcpClientService.setReasoningEffort(chatId: String, reasoningEffortId: String): Boolean {
    val context = sessions[chatId] ?: return false
    val trimmedReasoningEffortId = reasoningEffortId.trim()
    val adapterName = context.activeAdapterNameRef.get() ?: return false
    if (context.activeReasoningEffortIdRef.get() == trimmedReasoningEffortId) {
        AcpAgentPreferencesStore.rememberReasoningEffort(adapterName, trimmedReasoningEffortId)
        return true
    }

    val metadata = adapterRuntimeMetadataMap[adapterName] ?: return false
    val configId = metadata.reasoningEffortConfigId ?: return false
    if (metadata.availableReasoningEfforts.none { it.id == trimmedReasoningEffortId }) return false
    val protocol = context.sharedProcess?.protocol ?: return false
    val sessionId = context.sessionIdRef.get()?.takeIf { it.isNotBlank() } ?: return false

    return runCatching {
        withContext(Dispatchers.IO) {
            protocol.setSessionConfigOptionRaw(sessionId, configId, trimmedReasoningEffortId)
        }
        context.activeReasoningEffortIdRef.set(trimmedReasoningEffortId)
        AcpAgentPreferencesStore.rememberReasoningEffort(adapterName, trimmedReasoningEffortId)
        adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentReasoningEffortId = trimmedReasoningEffortId)
        true
    }.getOrDefault(false)
}
