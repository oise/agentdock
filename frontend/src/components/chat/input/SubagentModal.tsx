import { SubagentThread } from '../../../types/chat';
import { X, LoaderCircle, Check } from 'lucide-react';

interface SubagentModalProps {
  thread: SubagentThread;
  onClose: () => void;
}

export function SubagentModal({ thread, onClose }: SubagentModalProps) {
  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-8"
      onClick={onClose}
    >
      <div
        className="flex max-h-[80vh] w-full max-w-[700px] flex-col overflow-hidden rounded-lg border
          border-[var(--ide-Button-startBorderColor)] bg-editor-bg shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center gap-2 border-b border-[var(--ide-Button-startBorderColor)] px-4 py-3">
          {thread.status === 'running' && <LoaderCircle size={16} className="animate-spin text-foreground-secondary" />}
          {thread.status === 'done' && <Check size={16} className="text-[var(--ide-Green)]" />}
          {thread.status === 'error' && <X size={16} className="text-error" />}
          <span className="text-ide font-medium">{thread.agentName}</span>
          {thread.title && <span className="truncate text-ide-small text-foreground-secondary">— {thread.title}</span>}
          <button
            type="button"
            onClick={onClose}
            className="ml-auto rounded p-1 text-foreground-secondary hover:bg-hover hover:text-foreground"
          >
            <X size={16} />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-4">
          {thread.output ? (
            <pre className="whitespace-pre-wrap break-words font-mono text-ide-small text-foreground">
              {thread.output}
            </pre>
          ) : thread.status === 'running' ? (
            <div className="space-y-2 text-ide-small text-foreground-secondary">
              <div className="flex items-center gap-2">
                <LoaderCircle size={14} className="animate-spin" />
                Subagent is running.
              </div>
              <div>
                Live subagent output is not exposed by this ACP adapter yet. The final summary will appear here when the task completes.
              </div>
            </div>
          ) : (
            <div className="text-ide-small text-foreground-secondary">No output available.</div>
          )}
        </div>
      </div>
    </div>
  );
}
