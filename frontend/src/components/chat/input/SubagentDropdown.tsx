import { useState } from 'react';
import { ChevronDown, LoaderCircle, Check, X, Bot } from 'lucide-react';
import { SubagentThread } from '../../../types/chat';

interface SubagentDropdownProps {
  threads: SubagentThread[];
  onSelectThread: (thread: SubagentThread) => void;
}

export function SubagentDropdown({ threads, onSelectThread }: SubagentDropdownProps) {
  const [open, setOpen] = useState(false);
  if (threads.length === 0) return null;

  const runningCount = threads.filter((t) => t.status === 'running').length;

  return (
    <div className="relative z-30">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-1.5 rounded-md border border-[var(--ide-Button-startBorderColor)]
          bg-editor-bg px-2 py-1 text-ide-small text-foreground-secondary hover:bg-hover hover:text-foreground
          transition-colors shadow-sm"
      >
        <Bot size={14} />
        <span>Threads</span>
        {runningCount > 0 && (
          <span className="flex items-center gap-0.5 text-foreground-secondary">
            <LoaderCircle size={12} className="animate-spin" />
            {runningCount}
          </span>
        )}
        <ChevronDown size={14} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute left-0 top-full z-50 mt-1 min-w-[240px] rounded-md border
            border-[var(--ide-Button-startBorderColor)] bg-editor-bg shadow-lg">
            {threads.map((thread) => (
              <button
                key={thread.id}
                type="button"
                onClick={() => {
                  setOpen(false);
                  onSelectThread(thread);
                }}
                className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-ide-small
                  text-foreground-secondary hover:bg-hover hover:text-foreground"
              >
                <StatusIcon status={thread.status} />
                <span className="truncate">
                  <span className="text-foreground-secondary">{thread.agentName}</span>
                  {thread.title && <span className="text-foreground-secondary/70"> — {thread.title}</span>}
                </span>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function StatusIcon({ status }: { status: string }) {
  if (status === 'running')
    return <LoaderCircle size={14} className="shrink-0 animate-spin text-foreground-secondary" />;
  if (status === 'done')
    return <Check size={14} className="shrink-0 text-[var(--ide-Green)]" />;
  if (status === 'error')
    return <X size={14} className="shrink-0 text-error" />;
  return <span className="h-3.5 w-3.5 shrink-0 rounded-full border border-current" />;
}
