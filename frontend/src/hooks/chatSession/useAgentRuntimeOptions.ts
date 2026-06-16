import { MutableRefObject, useEffect, useState } from 'react';
import { AgentOption, HistorySessionMeta } from '../../types/chat';

type UseAgentRuntimeOptionsArgs = {
  availableAgents: AgentOption[];
  effectiveSelectedAgent: AgentOption | undefined;
  selectedAgentId: string;
  conversationId: string;
  status: string;
  historySession?: HistorySessionMeta;
  startedAgentIdRef: MutableRefObject<string>;
  startedModelIdRef: MutableRefObject<string>;
  startedModeIdRef: MutableRefObject<string>;
  startedReasoningEffortIdRef: MutableRefObject<string>;
};

export function useAgentRuntimeOptions({
  availableAgents,
  effectiveSelectedAgent,
  selectedAgentId,
  conversationId,
  status,
  historySession,
  startedAgentIdRef,
  startedModelIdRef,
  startedModeIdRef,
  startedReasoningEffortIdRef
}: UseAgentRuntimeOptionsArgs) {
  const [selectedModelByAgent, setSelectedModelByAgent] = useState<Record<string, string>>({});
  const [selectedModeByAgent, setSelectedModeByAgent] = useState<Record<string, string>>({});
  const [selectedReasoningEffortByAgent, setSelectedReasoningEffortByAgent] = useState<Record<string, string>>({});
  const availableModels = effectiveSelectedAgent?.availableModels ?? [];
  const availableModes = effectiveSelectedAgent?.availableModes ?? [];
  const availableReasoningEfforts = effectiveSelectedAgent?.availableReasoningEfforts ?? [];

  const selectedModelId = effectiveSelectedAgent
    ? selectedModelByAgent[effectiveSelectedAgent.id] ||
      effectiveSelectedAgent.currentModelId ||
      availableModels[0]?.modelId ||
      ''
    : '';

  const selectedModeId = effectiveSelectedAgent
    ? selectedModeByAgent[effectiveSelectedAgent.id] ||
      effectiveSelectedAgent.currentModeId ||
      availableModes[0]?.id ||
      ''
    : '';

  const selectedReasoningEffortId = effectiveSelectedAgent
    ? selectedReasoningEffortByAgent[effectiveSelectedAgent.id] ||
      effectiveSelectedAgent.currentReasoningEffortId ||
      availableReasoningEfforts[0]?.id ||
      ''
    : '';

  const modelIdForStart = selectedAgentId
    ? selectedModelByAgent[selectedAgentId] || effectiveSelectedAgent?.currentModelId || ''
    : '';

  useEffect(() => {
    if (availableAgents.length === 0) return;
    setSelectedModelByAgent((prev) => {
      const next: Record<string, string> = { ...prev };
      availableAgents.forEach((agent) => {
        if (next[agent.id]) return;
        const currentModel = agent.currentModelId || agent.availableModels?.[0]?.modelId || '';
        if (currentModel) next[agent.id] = currentModel;
      });
      return next;
    });

    setSelectedModeByAgent((prev) => {
      const next: Record<string, string> = { ...prev };
      availableAgents.forEach((agent) => {
        if (next[agent.id]) return;
        const currentMode = agent.currentModeId || agent.availableModes?.[0]?.id || '';
        if (currentMode) next[agent.id] = currentMode;
      });
      return next;
    });

    setSelectedReasoningEffortByAgent((prev) => {
      const next: Record<string, string> = { ...prev };
      availableAgents.forEach((agent) => {
        if (next[agent.id]) return;
        const currentReasoningEffort = agent.currentReasoningEffortId || agent.availableReasoningEfforts?.[0]?.id || '';
        if (currentReasoningEffort) next[agent.id] = currentReasoningEffort;
      });
      return next;
    });
  }, [availableAgents]);

  useEffect(() => {
    if (!historySession) return;
    if (historySession.modelId) {
      setSelectedModelByAgent((prev) => ({
        ...prev,
        [historySession.adapterName]: historySession.modelId as string
      }));
    }
    if (historySession.modeId) {
      setSelectedModeByAgent((prev) => ({
        ...prev,
        [historySession.adapterName]: historySession.modeId as string
      }));
    }
  }, [historySession]);

  useEffect(() => {
    if (!selectedAgentId || !selectedModelId) return;
    if (status !== 'ready') return;
    if (startedAgentIdRef.current !== selectedAgentId) return;
    if (startedModelIdRef.current === selectedModelId) return;
    if (typeof window.__setModel !== 'function') return;

    try {
      window.__setModel(conversationId, selectedAgentId, selectedModelId);
      startedModelIdRef.current = selectedModelId;
    } catch (e) {
      console.warn('[useChatSession] Failed to set model:', e);
    }
  }, [conversationId, selectedAgentId, selectedModelId, status, startedAgentIdRef, startedModelIdRef]);

  useEffect(() => {
    if (!selectedAgentId || !selectedModeId) return;
    if (status !== 'ready') return;
    if (startedAgentIdRef.current !== selectedAgentId) return;
    if (startedModeIdRef.current === selectedModeId) return;
    if (typeof window.__setMode !== 'function') return;

    try {
      window.__setMode(conversationId, selectedAgentId, selectedModeId);
      startedModeIdRef.current = selectedModeId;
    } catch (e) {
      console.warn('[useChatSession] Failed to set mode:', e);
    }
  }, [conversationId, selectedAgentId, selectedModeId, status, startedAgentIdRef, startedModeIdRef]);

  useEffect(() => {
    if (!selectedAgentId || !selectedReasoningEffortId) return;
    if (availableReasoningEfforts.length === 0) return;
    if (status !== 'ready') return;
    if (startedAgentIdRef.current !== selectedAgentId) return;
    if (startedReasoningEffortIdRef.current === selectedReasoningEffortId) return;
    if (typeof window.__setReasoningEffort !== 'function') return;

    try {
      window.__setReasoningEffort(conversationId, selectedAgentId, selectedReasoningEffortId);
      startedReasoningEffortIdRef.current = selectedReasoningEffortId;
    } catch (e) {
      console.warn('[useChatSession] Failed to set reasoning effort:', e);
    }
  }, [
    availableReasoningEfforts.length,
    conversationId,
    selectedAgentId,
    selectedReasoningEffortId,
    status,
    startedAgentIdRef,
    startedReasoningEffortIdRef
  ]);

  const handleModelChange = (modelId: string, targetAgentId?: string) => {
    const agentId = targetAgentId || selectedAgentId;
    setSelectedModelByAgent((prev) => (agentId ? { ...prev, [agentId]: modelId } : prev));
  };

  const handleModeChange = (modeId: string) => {
    setSelectedModeByAgent((prev) => (selectedAgentId ? { ...prev, [selectedAgentId]: modeId } : prev));
  };

  const handleReasoningEffortChange = (reasoningEffortId: string) => {
    setSelectedReasoningEffortByAgent((prev) =>
      selectedAgentId ? { ...prev, [selectedAgentId]: reasoningEffortId } : prev
    );
  };

  return {
    availableModels,
    availableModes,
    availableReasoningEfforts,
    selectedModelId,
    selectedModeId,
    selectedReasoningEffortId,
    modelIdForStart,
    handleModelChange,
    handleModeChange,
    handleReasoningEffortChange
  };
}
