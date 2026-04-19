import React from 'react';

interface Version {
  versionNumber: number;
  subject?: string;
  createdAt?: string;
  isPublished?: boolean;
}

interface VersionHistoryProps {
  versions: Version[];
  onSelect: (version: Version) => void;
}

export const VersionHistory: React.FC<VersionHistoryProps> = ({ versions, onSelect }) => (
  <div className="space-y-1">
    {versions.map(v => (
      <div
        key={v.versionNumber}
        className={`flex items-center justify-between p-2 rounded cursor-pointer ${v.isPublished ? 'bg-green-50' : 'bg-gray-50'}`}
        onClick={() => onSelect(v)}
      >
        <span>v{v.versionNumber} {v.subject && `- ${v.subject}`}</span>
        {v.isPublished && <span className="text-xs text-green-600">Published</span>}
      </div>
    ))}
  </div>
);
