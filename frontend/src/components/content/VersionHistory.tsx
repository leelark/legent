import React from 'react';
import { Button } from '@/components/ui/Button';

interface Version {
  id?: string;
  versionNumber: number;
  subject?: string;
  createdAt?: string;
  isPublished?: boolean;
  changes?: string;
}

interface VersionHistoryProps {
  versions: Version[];
  onSelect: (version: Version) => void;
  onPublish?: (version: Version) => void;
  onRollback?: (version: Version) => void;
}

export const VersionHistory: React.FC<VersionHistoryProps> = ({ versions, onSelect, onPublish, onRollback }) => (
  <div className="space-y-1">
    {versions.map(v => (
      <div
        key={v.versionNumber}
        className={`rounded border p-3 ${v.isPublished ? 'border-green-200 bg-green-50' : 'border-border-default bg-surface-secondary'}`}
        onClick={() => onSelect(v)}
      >
        <div className="space-y-1">
          <span className="block text-sm font-medium text-content-primary">
            v{v.versionNumber} {v.subject && `- ${v.subject}`}
          </span>
          {v.createdAt && <span className="block text-xs text-content-secondary">{new Date(v.createdAt).toLocaleString()}</span>}
          {v.changes && <span className="block text-xs text-content-secondary">{v.changes}</span>}
          {v.isPublished && <span className="text-xs text-green-700">Published</span>}
        </div>
        <div className="flex items-center gap-2">
          {!v.isPublished && onPublish && (
            <Button
              size="sm"
              onClick={(event) => {
                event.stopPropagation();
                onPublish(v);
              }}
            >
              Publish
            </Button>
          )}
          {onRollback && (
            <Button
              size="sm"
              variant="secondary"
              onClick={(event) => {
                event.stopPropagation();
                onRollback(v);
              }}
            >
              Rollback
            </Button>
          )}
        </div>
      </div>
    ))}
  </div>
);
