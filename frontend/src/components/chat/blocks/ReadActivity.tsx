import React from 'react';
import { ToolCallEntry } from '../../../types/chat';
import { safeParseJson } from '../../../utils/toolCallUtils';
import { FileIcon } from '../shared/FileIcon';
import { ExpandableActivity } from './ExpandableActivity';
import { InlineButton } from './InlineButton';

interface Props {
  entry: ToolCallEntry;
  isActivePrompt: boolean;
  onOpenFile: (path: string, line?: number) => void;
}

function getFileName(path: string): string {
  if (!path) return '';
  return path.split(/[\\/]/).pop() || path;
}

export const ReadActivity: React.FC<Props> = ({ entry, onOpenFile, isActivePrompt }) => {
  const parsed = safeParseJson(entry.rawJson);

  const location = (entry.locations?.[0] ?? parsed?.locations?.[0]) as { path?: string; line?: number } | undefined;
  const rawInput = parsed?.rawInput;
  const filePath = location?.path ?? rawInput?.filePath ?? rawInput?.path;
  const fileName = getFileName(filePath);

  const icon = <span className="flex-shrink-0"><FileIcon filePath={filePath} className="h-[13px] w-[13px] flex-shrink-0" /></span>;
  if (!filePath || !fileName) {
    const cleanTitle = entry.title?.replace(/^"(.*)"$/, '$1') || entry.title;
    return <ExpandableActivity icon={icon} label={cleanTitle || entry.kind} status={entry.status} isActivePrompt={isActivePrompt} />;
  }

  const limit = rawInput?.limit;
  let startLine: number | null = null;
  let endLine: number | null = null;
  if (typeof location?.line === 'number') {
    if (location.line !== 0 || (limit !== undefined && limit !== 0)) {
      startLine = location.line;
      if (limit) endLine = startLine + limit;
    }
  }
  if (startLine === null && Array.isArray(rawInput?.view_range) && rawInput.view_range.length >= 1) {
    startLine = rawInput.view_range[0] ?? null;
    endLine = rawInput.view_range[1] ?? null;
  }
  if (startLine === null && typeof rawInput?.offset === 'number') {
    startLine = rawInput.offset;
    if (limit) endLine = startLine + limit;
  }

  const lineRange = startLine !== null
    ? ` L${startLine}${endLine !== null ? `-${endLine}` : ''}`
    : '';
  const pattern = typeof rawInput?.pattern === 'string' && rawInput.pattern.trim().length > 0
    ? rawInput.pattern.trim()
    : null;

  return (
    <ExpandableActivity icon={icon} status={entry.status} isActivePrompt={isActivePrompt}
      label={<>Read <InlineButton onClick={() => onOpenFile(filePath, startLine || undefined)} className="hover:underline">
        {fileName}{lineRange}</InlineButton>{pattern ? ` | Pattern: ${pattern}` : ''}</>}
    />
  );
};
