'use client';

import React, { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import DOMPurify from 'isomorphic-dompurify';
import { Input } from '@/components/ui/Input';

export interface ContentBlock {
  id: string;
  name: string;
  blockType: string;
  content: string;
  styles?: Record<string, unknown>;
  settings?: Record<string, unknown>;
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

const BLOCK_LIBRARY: Array<{ type: string; label: string; defaultContent: string }> = [
  { type: 'HEADER', label: 'Header', defaultContent: '<h2>Header Title</h2>' },
  { type: 'TEXT', label: 'Text', defaultContent: '<p>Write your content here.</p>' },
  { type: 'RICH_TEXT', label: 'Rich Text', defaultContent: '<p><strong>Rich</strong> text block.</p>' },
  { type: 'IMAGE', label: 'Image', defaultContent: '<img src="https://placehold.co/640x240" alt="Template image" />' },
  { type: 'BUTTON', label: 'Button', defaultContent: '<a href="https://example.com" style="display:inline-block;padding:10px 16px;background:#4f46e5;color:#fff;border-radius:8px;text-decoration:none;">Click Here</a>' },
  { type: 'DIVIDER', label: 'Divider', defaultContent: '<hr />' },
  { type: 'SPACER', label: 'Spacer', defaultContent: '<div style="height:24px;"></div>' },
  { type: 'BANNER', label: 'Banner', defaultContent: '<div style="padding:18px;background:#eef2ff;border-radius:10px;">Banner text</div>' },
  { type: 'VIDEO', label: 'Video Preview', defaultContent: '<a href="https://example.com"><img src="https://placehold.co/640x360?text=Video+Preview" alt="Video preview"/></a>' },
  { type: 'SOCIAL', label: 'Social Icons', defaultContent: '<div><a href="https://x.com">X</a> · <a href="https://linkedin.com">LinkedIn</a> · <a href="https://instagram.com">Instagram</a></div>' },
  { type: 'PRODUCT', label: 'Product Card', defaultContent: '<table role="presentation" width="100%"><tr><td><img src="https://placehold.co/280x160" alt="Product"/></td></tr><tr><td><h3>Product name</h3><p>Short product description.</p></td></tr></table>' },
  { type: 'CTA', label: 'CTA', defaultContent: '<div><h3>Ready to get started?</h3><p>Use this CTA block to drive conversion.</p></div>' },
  { type: 'COUNTDOWN', label: 'Countdown', defaultContent: '<div>Offer ends in: <strong>03d 10h 25m</strong></div>' },
  { type: 'HTML', label: 'HTML Block', defaultContent: '<!-- custom HTML -->' },
  { type: 'DYNAMIC', label: 'Dynamic Content', defaultContent: '<div>{{firstName}}, recommended for you: {{productName}}</div>' },
  { type: 'FOOTER', label: 'Footer', defaultContent: '<p style="font-size:12px;color:#64748b;">Company footer · Address · Unsubscribe</p>' },
];

const defaultStyles = {
  backgroundColor: '#ffffff',
  textColor: '#0f172a',
  padding: '16',
  borderRadius: '8',
  borderColor: '#e2e8f0',
};

const createBlock = (type: string): ContentBlock => {
  const blueprint = BLOCK_LIBRARY.find((b) => b.type === type) ?? BLOCK_LIBRARY[1];
  const blockId = `block-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
  return {
    id: blockId,
    name: blueprint.label,
    blockType: blueprint.type,
    content: blueprint.defaultContent,
    styles: { ...defaultStyles },
    settings: {
      hideOnMobile: false,
      hideOnDesktop: false,
      visibilityRule: '',
    },
  };
};

export const TemplateBuilder: React.FC<TemplateBuilderProps> = ({ blocks, onBlocksChange }) => {
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(blocks[0]?.id ?? null);

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

  const selectedBlock = blocks.find((block) => block.id === selectedId) ?? null;

  const patchBlock = (id: string, patch: Partial<ContentBlock>) => {
    onBlocksChange(
      blocks.map((block) => (block.id === id ? { ...block, ...patch } : block))
    );
  };

  const patchBlockStyles = (id: string, patch: Record<string, unknown>) => {
    onBlocksChange(
      blocks.map((block) =>
        block.id === id
          ? { ...block, styles: { ...(block.styles ?? {}), ...patch } }
          : block
      )
    );
  };

  const patchBlockSettings = (id: string, patch: Record<string, unknown>) => {
    onBlocksChange(
      blocks.map((block) =>
        block.id === id
          ? { ...block, settings: { ...(block.settings ?? {}), ...patch } }
          : block
      )
    );
  };

  const handleAddBlock = (type: string) => {
    const newBlock = createBlock(type);
    onBlocksChange([...blocks, newBlock]);
    setSelectedId(newBlock.id);
  };

  const removeBlock = (id: string) => {
    const nextBlocks = blocks.filter((block) => block.id !== id);
    onBlocksChange(nextBlocks);
    if (selectedId === id) {
      setSelectedId(nextBlocks[0]?.id ?? null);
    }
  };

  return (
    <div className="grid gap-4 lg:grid-cols-[280px_1fr_320px]">
      <Card className="space-y-3">
        <h3 className="text-sm font-semibold text-content-primary">Content Blocks</h3>
        <p className="text-xs text-content-secondary">
          Drag blocks to reorder sections and build responsive layouts.
        </p>
        <div className="grid grid-cols-2 gap-2">
          {BLOCK_LIBRARY.map((block) => (
            <Button
              key={block.type}
              size="sm"
              variant="secondary"
              onClick={() => handleAddBlock(block.type)}
            >
              {block.label}
            </Button>
          ))}
        </div>
      </Card>

      <Card className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-content-primary">Canvas</h3>
          <span className="text-xs text-content-secondary">{blocks.length} blocks</span>
        </div>
        {blocks.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border-default p-8 text-center">
            <p className="text-sm text-content-secondary">No blocks yet. Add blocks from left panel.</p>
          </div>
        ) : (
          <div className="space-y-2">
            {blocks.map((block, idx) => {
              const blockStyles = block.styles ?? {};
              return (
                <Card
                  key={block.id}
                  draggable
                  onDragStart={() => handleDragStart(idx)}
                  onDragEnd={handleDragEnd}
                  onDrop={() => handleDrop(idx)}
                  onClick={() => setSelectedId(block.id)}
                  className={`cursor-pointer border ${draggedIndex === idx ? 'opacity-50' : ''} ${
                    selectedId === block.id ? 'border-accent' : 'border-border-default'
                  }`}
                >
                  <div className="mb-3 flex items-center justify-between">
                    <div>
                      <p className="text-sm font-semibold text-content-primary">{block.name}</p>
                      <p className="text-xs text-content-secondary">{block.blockType}</p>
                    </div>
                    <Button size="sm" variant="danger" onClick={() => removeBlock(block.id)}>
                      Delete
                    </Button>
                  </div>
                  <div
                    className="rounded-lg border border-border-default bg-surface-secondary p-3"
                    style={{
                      backgroundColor: String(blockStyles.backgroundColor ?? '#ffffff'),
                      color: String(blockStyles.textColor ?? '#0f172a'),
                      padding: `${Number(blockStyles.padding ?? 16)}px`,
                      borderRadius: `${Number(blockStyles.borderRadius ?? 8)}px`,
                    }}
                  >
                    <div dangerouslySetInnerHTML={{ __html: sanitizeHtml(block.content) }} />
                  </div>
                </Card>
              );
            })}
          </div>
        )}
      </Card>

      <Card className="space-y-3">
        <h3 className="text-sm font-semibold text-content-primary">Properties</h3>
        {!selectedBlock ? (
          <p className="text-sm text-content-secondary">Select a block to edit.</p>
        ) : (
          <div className="space-y-3">
            <Input
              label="Block Name"
              value={selectedBlock.name}
              onChange={(event) => patchBlock(selectedBlock.id, { name: event.target.value })}
            />
            <div>
              <label className="mb-1 block text-sm font-medium text-content-primary">HTML Content</label>
              <textarea
                className="min-h-[140px] w-full rounded-lg border border-border-default bg-surface-primary p-3 text-xs font-mono"
                value={selectedBlock.content}
                onChange={(event) => patchBlock(selectedBlock.id, { content: event.target.value })}
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Background"
                type="color"
                value={String(selectedBlock.styles?.backgroundColor ?? '#ffffff')}
                onChange={(event) => patchBlockStyles(selectedBlock.id, { backgroundColor: event.target.value })}
              />
              <Input
                label="Text Color"
                type="color"
                value={String(selectedBlock.styles?.textColor ?? '#0f172a')}
                onChange={(event) => patchBlockStyles(selectedBlock.id, { textColor: event.target.value })}
              />
              <Input
                label="Padding"
                type="number"
                min={0}
                value={String(selectedBlock.styles?.padding ?? 16)}
                onChange={(event) => patchBlockStyles(selectedBlock.id, { padding: event.target.value })}
              />
              <Input
                label="Radius"
                type="number"
                min={0}
                value={String(selectedBlock.styles?.borderRadius ?? 8)}
                onChange={(event) => patchBlockStyles(selectedBlock.id, { borderRadius: event.target.value })}
              />
            </div>
            <div className="space-y-2 rounded-lg border border-border-default p-3">
              <p className="text-xs font-semibold uppercase text-content-secondary">Visibility</p>
              <label className="flex items-center gap-2 text-sm text-content-primary">
                <input
                  type="checkbox"
                  checked={Boolean(selectedBlock.settings?.hideOnMobile)}
                  onChange={(event) => patchBlockSettings(selectedBlock.id, { hideOnMobile: event.target.checked })}
                />
                Hide on mobile
              </label>
              <label className="flex items-center gap-2 text-sm text-content-primary">
                <input
                  type="checkbox"
                  checked={Boolean(selectedBlock.settings?.hideOnDesktop)}
                  onChange={(event) => patchBlockSettings(selectedBlock.id, { hideOnDesktop: event.target.checked })}
                />
                Hide on desktop
              </label>
              <Input
                label="Conditional Rule"
                placeholder="segment == VIP"
                value={String(selectedBlock.settings?.visibilityRule ?? '')}
                onChange={(event) => patchBlockSettings(selectedBlock.id, { visibilityRule: event.target.value })}
              />
            </div>
          </div>
        )}
      </Card>
    </div>
  );
};
