'use client';

import React, { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import DOMPurify from 'isomorphic-dompurify';

export interface ContentBlock {
  id: string;
  name: string;
  blockType: string;
  content: string;
  styles?: any;
  settings?: any;
}

interface TemplateBuilderProps {
  blocks: ContentBlock[];
  onBlocksChange: (blocks: ContentBlock[]) => void;
}

const sanitizeHtml = (html: string) => {
  if (!html) return '';
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed'],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'style']
  });
};

export const TemplateBuilder: React.FC<TemplateBuilderProps> = ({ blocks, onBlocksChange }) => {
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);

  const handleDragStart = (index: number) => setDraggedIndex(index);
  const handleDragEnd = () => setDraggedIndex(null);
  const handleDrop = (index: number) => {
    if (draggedIndex === null || draggedIndex === index) return;
    const updated = [...blocks];
    const [removed] = updated.splice(draggedIndex, 1);
    updated.splice(index, 0, removed);
    onBlocksChange(updated);
    setDraggedIndex(null);
  };

  return (
    <div className="space-y-2">
      {blocks.map((block, idx) => (
        <Card
          key={block.id}
          draggable
          onDragStart={() => handleDragStart(idx)}
          onDragEnd={handleDragEnd}
          onDrop={() => handleDrop(idx)}
          className={draggedIndex === idx ? 'opacity-50' : ''}
        >
          <div className="flex justify-between items-center p-2">
            <span>{block.name} ({block.blockType})</span>
            <Button size="sm" variant="danger" onClick={() => {
              const updated = blocks.filter((_, i) => i !== idx);
              onBlocksChange(updated);
            }}>Delete</Button>
          </div>
          <div className="p-2 bg-gray-50 rounded">
            <div dangerouslySetInnerHTML={{ __html: sanitizeHtml(block.content) }} />
          </div>
        </Card>
      ))}
    </div>
  );
};
