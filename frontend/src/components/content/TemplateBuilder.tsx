'use client';

import React, { useEffect, useMemo, useRef, useState, type CSSProperties, type DragEvent } from 'react';
import { clsx } from 'clsx';
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  ArrowDown,
  ArrowUp,
  Braces,
  Code2,
  Copy,
  Eye,
  GripVertical,
  Laptop,
  Plus,
  Redo2,
  Search,
  Settings2,
  Smartphone,
  Trash2,
  Type,
  Undo2,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { sanitizeEmailHtml } from '@/lib/sanitize-html';

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

type BuilderViewMode = 'desktop' | 'mobile';
type InspectorTab = 'content' | 'style' | 'rules';
type TextAlign = 'left' | 'center' | 'right';
type BlockCategory = 'All' | 'Structure' | 'Content' | 'Commerce' | 'Personalization';

type BlockBlueprint = {
  type: string;
  label: string;
  category: Exclude<BlockCategory, 'All'>;
  description: string;
  defaultContent: string;
};

type DragPayload =
  | { kind: 'library'; type: string }
  | { kind: 'canvas'; id: string };

const DRAG_MIME = 'application/x-legent-template-block';
const MAX_HISTORY = 30;
const BLOCK_CATEGORIES: BlockCategory[] = ['All', 'Structure', 'Content', 'Commerce', 'Personalization'];

const BLOCK_LIBRARY: BlockBlueprint[] = [
  {
    type: 'HEADER',
    label: 'Header',
    category: 'Structure',
    description: 'Logo, title, intro',
    defaultContent: '<h2 style="margin:0;font-size:28px;line-height:1.2;">Header Title</h2>',
  },
  {
    type: 'TEXT',
    label: 'Text',
    category: 'Content',
    description: 'Paragraph copy',
    defaultContent: '<p style="margin:0;line-height:1.6;">Write your content here.</p>',
  },
  {
    type: 'RICH_TEXT',
    label: 'Rich Text',
    category: 'Content',
    description: 'Bold, links, lists',
    defaultContent: '<p style="margin:0;line-height:1.6;"><strong>Rich</strong> text block with <a href="https://example.com">a link</a>.</p>',
  },
  {
    type: 'IMAGE',
    label: 'Image',
    category: 'Content',
    description: 'Responsive image',
    defaultContent: '<img src="https://placehold.co/640x240" alt="Template image" style="display:block;width:100%;height:auto;border:0;" />',
  },
  {
    type: 'BUTTON',
    label: 'Button',
    category: 'Content',
    description: 'Primary CTA',
    defaultContent: '<a href="https://example.com" style="display:inline-block;padding:12px 18px;background:#2563eb;color:#ffffff;border-radius:8px;text-decoration:none;font-weight:700;">Click Here</a>',
  },
  {
    type: 'DIVIDER',
    label: 'Divider',
    category: 'Structure',
    description: 'Section split',
    defaultContent: '<hr style="border:0;border-top:1px solid #e2e8f0;margin:0;" />',
  },
  {
    type: 'SPACER',
    label: 'Spacer',
    category: 'Structure',
    description: 'Vertical rhythm',
    defaultContent: '<div style="height:24px;line-height:24px;">&nbsp;</div>',
  },
  {
    type: 'BANNER',
    label: 'Banner',
    category: 'Content',
    description: 'Announcement band',
    defaultContent: '<div style="padding:18px;background:#eff6ff;border:1px solid #bfdbfe;border-radius:8px;"><strong>Announcement</strong><br />Banner text goes here.</div>',
  },
  {
    type: 'VIDEO',
    label: 'Video Preview',
    category: 'Content',
    description: 'Linked thumbnail',
    defaultContent: '<a href="https://example.com"><img src="https://placehold.co/640x360?text=Video+Preview" alt="Video preview" style="display:block;width:100%;height:auto;border:0;" /></a>',
  },
  {
    type: 'SOCIAL',
    label: 'Social Icons',
    category: 'Content',
    description: 'Social links',
    defaultContent: '<div><a href="https://x.com">X</a> &middot; <a href="https://linkedin.com">LinkedIn</a> &middot; <a href="https://instagram.com">Instagram</a></div>',
  },
  {
    type: 'PRODUCT',
    label: 'Product Card',
    category: 'Commerce',
    description: 'Product module',
    defaultContent: '<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td><img src="https://placehold.co/280x160" alt="Product" style="display:block;width:100%;height:auto;border:0;" /></td></tr><tr><td style="padding-top:12px;"><h3 style="margin:0 0 6px;">Product name</h3><p style="margin:0;line-height:1.5;">Short product description.</p></td></tr></table>',
  },
  {
    type: 'CTA',
    label: 'CTA',
    category: 'Commerce',
    description: 'Conversion section',
    defaultContent: '<div><h3 style="margin:0 0 8px;">Ready to get started?</h3><p style="margin:0 0 14px;">Use this CTA block to drive conversion.</p><a href="https://example.com" style="display:inline-block;padding:12px 18px;background:#0f172a;color:#ffffff;border-radius:8px;text-decoration:none;font-weight:700;">Start now</a></div>',
  },
  {
    type: 'COUNTDOWN',
    label: 'Countdown',
    category: 'Commerce',
    description: 'Offer urgency',
    defaultContent: '<div>Offer ends in: <strong>03d 10h 25m</strong></div>',
  },
  {
    type: 'HTML',
    label: 'HTML Block',
    category: 'Structure',
    description: 'Custom markup',
    defaultContent: '<!-- custom HTML -->',
  },
  {
    type: 'DYNAMIC',
    label: 'Dynamic Content',
    category: 'Personalization',
    description: 'Rule/token slot',
    defaultContent: '<div>{{firstName}}, recommended for you: {{productName}}</div>',
  },
  {
    type: 'FOOTER',
    label: 'Footer',
    category: 'Structure',
    description: 'Compliance footer',
    defaultContent: '<p style="margin:0;font-size:12px;line-height:1.5;color:#64748b;">Company footer &middot; Address &middot; <a href="{{unsubscribeUrl}}">Unsubscribe</a></p>',
  },
];

const defaultStyles = {
  backgroundColor: '#ffffff',
  textColor: '#0f172a',
  padding: 16,
  borderRadius: 8,
  borderColor: '#e2e8f0',
  borderWidth: 0,
  textAlign: 'left',
};

const serializeBlocks = (items: ContentBlock[]) => JSON.stringify(items);

const createBlockId = () => `block-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;

const createBlock = (type: string): ContentBlock => {
  const blueprint = BLOCK_LIBRARY.find((block) => block.type === type) ?? BLOCK_LIBRARY[1];
  return {
    id: createBlockId(),
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

const cloneBlock = (block: ContentBlock): ContentBlock => ({
  ...block,
  id: createBlockId(),
  name: `${block.name} copy`,
  styles: { ...(block.styles ?? {}) },
  settings: { ...(block.settings ?? {}) },
});

const parseDragPayload = (event: DragEvent): DragPayload | null => {
  const raw = event.dataTransfer.getData(DRAG_MIME);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as DragPayload;
    if (parsed.kind === 'library' && typeof parsed.type === 'string') return parsed;
    if (parsed.kind === 'canvas' && typeof parsed.id === 'string') return parsed;
  } catch {
    return null;
  }
  return null;
};

const setDragPayload = (event: DragEvent, payload: DragPayload) => {
  event.dataTransfer.setData(DRAG_MIME, JSON.stringify(payload));
  event.dataTransfer.setData('text/plain', payload.kind === 'library' ? payload.type : payload.id);
};

const clampIndex = (index: number, max: number) => Math.max(0, Math.min(index, max));

const numericStyle = (styles: Record<string, unknown> | undefined, key: string, fallback: number) => {
  const value = Number(styles?.[key] ?? fallback);
  return Number.isFinite(value) ? value : fallback;
};

const stringStyle = (styles: Record<string, unknown> | undefined, key: string, fallback: string) => {
  const value = styles?.[key];
  return typeof value === 'string' && value.trim() ? value : fallback;
};

const textAlignStyle = (styles: Record<string, unknown> | undefined): TextAlign => {
  const value = stringStyle(styles, 'textAlign', 'left');
  return value === 'center' || value === 'right' ? value : 'left';
};

const blockPreviewStyle = (block: ContentBlock): CSSProperties => {
  const styles = block.styles ?? {};
  const borderWidth = numericStyle(styles, 'borderWidth', 0);
  return {
    backgroundColor: stringStyle(styles, 'backgroundColor', '#ffffff'),
    color: stringStyle(styles, 'textColor', '#0f172a'),
    padding: `${Math.max(0, numericStyle(styles, 'padding', 16))}px`,
    borderRadius: `${Math.max(0, numericStyle(styles, 'borderRadius', 8))}px`,
    border: borderWidth > 0 ? `${borderWidth}px solid ${stringStyle(styles, 'borderColor', '#e2e8f0')}` : undefined,
    textAlign: textAlignStyle(styles),
  };
};

const hiddenInMode = (block: ContentBlock, viewMode: BuilderViewMode) =>
  (viewMode === 'mobile' && Boolean(block.settings?.hideOnMobile)) ||
  (viewMode === 'desktop' && Boolean(block.settings?.hideOnDesktop));

function ToolbarIconButton({
  disabled = false,
  icon,
  label,
  onClick,
}: {
  disabled?: boolean;
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <Button
      aria-label={label}
      className="h-8 w-8 rounded-lg"
      disabled={disabled}
      icon={icon}
      onClick={(event) => {
        event.stopPropagation();
        onClick();
      }}
      size="icon"
      title={label}
      variant="ghost"
    >
      <span className="sr-only">{label}</span>
    </Button>
  );
}

function DropZone({
  activeDropIndex,
  draggedId,
  index,
  onClear,
  onDropAt,
  onSetActive,
}: {
  activeDropIndex: number | null;
  draggedId: string | null;
  index: number;
  onClear: () => void;
  onDropAt: (event: DragEvent, targetIndex: number) => void;
  onSetActive: (index: number) => void;
}) {
  return (
    <div
      aria-label={`Drop block at position ${index + 1}`}
      className={clsx(
        'h-4 rounded-lg border border-dashed transition-all',
        activeDropIndex === index
          ? 'border-accent bg-brand-100/70 dark:bg-brand-900/30'
          : 'border-transparent hover:border-border-default hover:bg-surface-secondary',
      )}
      data-testid={`builder-drop-zone-${index}`}
      onDragLeave={onClear}
      onDragOver={(event) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = draggedId ? 'move' : 'copy';
        onSetActive(index);
      }}
      onDrop={(event) => onDropAt(event, index)}
      role="separator"
    />
  );
}

export const TemplateBuilder: React.FC<TemplateBuilderProps> = ({ blocks, onBlocksChange }) => {
  const [selectedId, setSelectedId] = useState<string | null>(blocks[0]?.id ?? null);
  const [draggedId, setDraggedId] = useState<string | null>(null);
  const [activeDropIndex, setActiveDropIndex] = useState<number | null>(null);
  const [libraryQuery, setLibraryQuery] = useState('');
  const [libraryCategory, setLibraryCategory] = useState<BlockCategory>('All');
  const [viewMode, setViewMode] = useState<BuilderViewMode>('desktop');
  const [inspectorTab, setInspectorTab] = useState<InspectorTab>('content');
  const [history, setHistory] = useState<{ past: ContentBlock[][]; future: ContentBlock[][] }>({
    past: [],
    future: [],
  });
  const lastLocalSignatureRef = useRef('');

  useEffect(() => {
    if (blocks.length === 0) {
      if (selectedId !== null) setSelectedId(null);
      return;
    }
    if (!selectedId || !blocks.some((block) => block.id === selectedId)) {
      setSelectedId(blocks[0].id);
    }
  }, [blocks, selectedId]);

  useEffect(() => {
    const signature = serializeBlocks(blocks);
    if (!lastLocalSignatureRef.current) {
      lastLocalSignatureRef.current = signature;
      return;
    }
    if (signature !== lastLocalSignatureRef.current) {
      setHistory({ past: [], future: [] });
      lastLocalSignatureRef.current = signature;
    }
  }, [blocks]);

  const selectedBlock = useMemo(
    () => blocks.find((block) => block.id === selectedId) ?? null,
    [blocks, selectedId],
  );

  const filteredLibrary = useMemo(() => {
    const query = libraryQuery.trim().toLowerCase();
    return BLOCK_LIBRARY.filter((block) => {
      const categoryMatch = libraryCategory === 'All' || block.category === libraryCategory;
      const queryMatch =
        !query ||
        block.label.toLowerCase().includes(query) ||
        block.type.toLowerCase().includes(query) ||
        block.description.toLowerCase().includes(query);
      return categoryMatch && queryMatch;
    });
  }, [libraryCategory, libraryQuery]);

  const commitBlocks = (nextBlocks: ContentBlock[], nextSelectedId?: string | null) => {
    const currentSignature = serializeBlocks(blocks);
    const nextSignature = serializeBlocks(nextBlocks);
    if (currentSignature === nextSignature) return;

    setHistory((current) => ({
      past: [...current.past, blocks].slice(-MAX_HISTORY),
      future: [],
    }));
    lastLocalSignatureRef.current = nextSignature;
    onBlocksChange(nextBlocks);
    setSelectedId(nextSelectedId ?? nextBlocks[0]?.id ?? null);
  };

  const restoreBlocks = (nextBlocks: ContentBlock[]) => {
    lastLocalSignatureRef.current = serializeBlocks(nextBlocks);
    onBlocksChange(nextBlocks);
    setSelectedId((current) => nextBlocks.find((block) => block.id === current)?.id ?? nextBlocks[0]?.id ?? null);
  };

  const undo = () => {
    const previous = history.past[history.past.length - 1];
    if (!previous) return;
    setHistory({
      past: history.past.slice(0, -1),
      future: [blocks, ...history.future].slice(0, MAX_HISTORY),
    });
    restoreBlocks(previous);
  };

  const redo = () => {
    const next = history.future[0];
    if (!next) return;
    setHistory({
      past: [...history.past, blocks].slice(-MAX_HISTORY),
      future: history.future.slice(1),
    });
    restoreBlocks(next);
  };

  const insertBlockAt = (type: string, index = blocks.length) => {
    const nextBlock = createBlock(type);
    const nextBlocks = [...blocks];
    nextBlocks.splice(clampIndex(index, nextBlocks.length), 0, nextBlock);
    commitBlocks(nextBlocks, nextBlock.id);
  };

  const moveBlockTo = (sourceId: string, targetIndex: number) => {
    const sourceIndex = blocks.findIndex((block) => block.id === sourceId);
    if (sourceIndex < 0) return;

    const nextBlocks = [...blocks];
    const [moved] = nextBlocks.splice(sourceIndex, 1);
    const adjustedIndex = sourceIndex < targetIndex ? targetIndex - 1 : targetIndex;
    nextBlocks.splice(clampIndex(adjustedIndex, nextBlocks.length), 0, moved);
    commitBlocks(nextBlocks, moved.id);
  };

  const moveBlockBy = (id: string, offset: number) => {
    const index = blocks.findIndex((block) => block.id === id);
    if (index < 0) return;
    moveBlockTo(id, index + offset);
  };

  const duplicateBlock = (id: string) => {
    const index = blocks.findIndex((block) => block.id === id);
    if (index < 0) return;
    const nextBlock = cloneBlock(blocks[index]);
    const nextBlocks = [...blocks];
    nextBlocks.splice(index + 1, 0, nextBlock);
    commitBlocks(nextBlocks, nextBlock.id);
  };

  const removeBlock = (id: string) => {
    const index = blocks.findIndex((block) => block.id === id);
    const nextBlocks = blocks.filter((block) => block.id !== id);
    commitBlocks(nextBlocks, nextBlocks[index]?.id ?? nextBlocks[index - 1]?.id ?? nextBlocks[0]?.id ?? null);
  };

  const patchBlock = (id: string, patch: Partial<ContentBlock>) => {
    commitBlocks(blocks.map((block) => (block.id === id ? { ...block, ...patch } : block)), id);
  };

  const patchBlockStyles = (id: string, patch: Record<string, unknown>) => {
    commitBlocks(
      blocks.map((block) =>
        block.id === id
          ? { ...block, styles: { ...(block.styles ?? {}), ...patch } }
          : block,
      ),
      id,
    );
  };

  const patchBlockSettings = (id: string, patch: Record<string, unknown>) => {
    commitBlocks(
      blocks.map((block) =>
        block.id === id
          ? { ...block, settings: { ...(block.settings ?? {}), ...patch } }
          : block,
      ),
      id,
    );
  };

  const handleDropAt = (event: DragEvent, targetIndex: number) => {
    event.preventDefault();
    event.stopPropagation();
    const payload = parseDragPayload(event);
    if (payload?.kind === 'library') {
      insertBlockAt(payload.type, targetIndex);
    }
    if (payload?.kind === 'canvas') {
      moveBlockTo(payload.id, targetIndex);
    }
    setActiveDropIndex(null);
    setDraggedId(null);
  };

  const handleCanvasDrop = (event: DragEvent) => {
    event.preventDefault();
    const payload = parseDragPayload(event);
    if (payload?.kind === 'library') {
      insertBlockAt(payload.type, blocks.length);
    }
    setActiveDropIndex(null);
    setDraggedId(null);
  };

  return (
    <div className="grid gap-4 xl:grid-cols-[280px_minmax(0,1fr)_340px]" data-testid="template-builder">
      <aside className="h-fit rounded-lg border border-border-default bg-surface-elevated/80 p-3">
        <div className="mb-3 flex items-center gap-2">
          <Plus className="h-4 w-4 text-brand-600" aria-hidden="true" />
          <h3 className="text-sm font-semibold text-content-primary">Library</h3>
        </div>

        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-content-muted" aria-hidden="true" />
          <input
            aria-label="Search blocks"
            className="w-full rounded-lg border border-border-default bg-surface-primary py-2 pl-9 pr-3 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
            onChange={(event) => setLibraryQuery(event.target.value)}
            placeholder="Search blocks"
            value={libraryQuery}
          />
        </div>

        <div className="mt-3 flex flex-wrap gap-1.5">
          {BLOCK_CATEGORIES.map((category) => (
            <button
              className={clsx(
                'rounded-md border px-2 py-1 text-xs font-semibold transition-colors',
                libraryCategory === category
                  ? 'border-accent bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-200'
                  : 'border-border-default text-content-secondary hover:border-border-strong hover:text-content-primary',
              )}
              key={category}
              onClick={() => setLibraryCategory(category)}
              type="button"
            >
              {category}
            </button>
          ))}
        </div>

        <div className="mt-3 grid gap-2">
          {filteredLibrary.map((block) => (
            <button
              className="group rounded-lg border border-border-default bg-surface-primary p-3 text-left transition-all hover:border-accent hover:bg-surface-secondary focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
              data-testid={`block-library-${block.type}`}
              draggable
              key={block.type}
              onClick={() => insertBlockAt(block.type)}
              onDragStart={(event) => {
                setDragPayload(event, { kind: 'library', type: block.type });
                event.dataTransfer.effectAllowed = 'copy';
              }}
              type="button"
            >
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-content-primary">{block.label}</p>
                  <p className="truncate text-xs text-content-secondary">{block.description}</p>
                </div>
                <GripVertical className="h-4 w-4 flex-shrink-0 text-content-muted group-hover:text-accent" aria-hidden="true" />
              </div>
            </button>
          ))}
          {filteredLibrary.length === 0 && (
            <div className="rounded-lg border border-dashed border-border-default p-4 text-sm text-content-secondary">
              No matching blocks.
            </div>
          )}
        </div>
      </aside>

      <section className="min-w-0 rounded-lg border border-border-default bg-surface-elevated/80 p-3">
        <div className="mb-3 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div className="flex items-center gap-2">
            <Eye className="h-4 w-4 text-brand-600" aria-hidden="true" />
            <h3 className="text-sm font-semibold text-content-primary">Canvas</h3>
            <span className="rounded-md bg-surface-secondary px-2 py-1 text-xs font-semibold text-content-secondary">
              {blocks.length}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="flex rounded-lg border border-border-default bg-surface-primary p-1">
              <button
                aria-label="Desktop preview"
                className={clsx(
                  'inline-flex h-8 items-center gap-1.5 rounded-md px-3 text-xs font-semibold transition-colors',
                  viewMode === 'desktop' ? 'bg-surface-elevated text-content-primary shadow-sm' : 'text-content-secondary hover:text-content-primary',
                )}
                onClick={() => setViewMode('desktop')}
                type="button"
              >
                <Laptop className="h-4 w-4" aria-hidden="true" />
                Desktop
              </button>
              <button
                aria-label="Mobile preview"
                className={clsx(
                  'inline-flex h-8 items-center gap-1.5 rounded-md px-3 text-xs font-semibold transition-colors',
                  viewMode === 'mobile' ? 'bg-surface-elevated text-content-primary shadow-sm' : 'text-content-secondary hover:text-content-primary',
                )}
                onClick={() => setViewMode('mobile')}
                type="button"
              >
                <Smartphone className="h-4 w-4" aria-hidden="true" />
                Mobile
              </button>
            </div>
            <ToolbarIconButton
              disabled={history.past.length === 0}
              icon={<Undo2 className="h-4 w-4" />}
              label="Undo builder change"
              onClick={undo}
            />
            <ToolbarIconButton
              disabled={history.future.length === 0}
              icon={<Redo2 className="h-4 w-4" />}
              label="Redo builder change"
              onClick={redo}
            />
          </div>
        </div>

        <div
          className={clsx(
            'mx-auto min-h-[520px] rounded-lg border border-border-default bg-surface-primary p-3 transition-all',
            viewMode === 'mobile' ? 'max-w-[390px]' : 'max-w-[760px]',
          )}
          data-testid="template-canvas"
          onDragOver={(event) => {
            event.preventDefault();
            event.dataTransfer.dropEffect = draggedId ? 'move' : 'copy';
          }}
          onDrop={handleCanvasDrop}
        >
          {blocks.length === 0 ? (
            <div className="flex min-h-[480px] flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-border-default bg-surface-secondary/60 p-6 text-center">
              <p className="text-sm font-semibold text-content-primary">Empty template</p>
              <div className="flex flex-wrap justify-center gap-2">
                {['HEADER', 'TEXT', 'BUTTON'].map((type) => (
                  <Button key={type} onClick={() => insertBlockAt(type)} size="sm" variant="secondary">
                    {BLOCK_LIBRARY.find((block) => block.type === type)?.label ?? type}
                  </Button>
                ))}
              </div>
            </div>
          ) : (
            <div className="space-y-1">
              {blocks.map((block, index) => {
                const isSelected = selectedId === block.id;
                const isHidden = hiddenInMode(block, viewMode);
                return (
                  <React.Fragment key={block.id}>
                    <DropZone
                      activeDropIndex={activeDropIndex}
                      draggedId={draggedId}
                      index={index}
                      onClear={() => setActiveDropIndex(null)}
                      onDropAt={handleDropAt}
                      onSetActive={setActiveDropIndex}
                    />
                    <article
                      aria-label={`Select ${block.name}`}
                      className={clsx(
                        'group rounded-lg border bg-surface-elevated p-2 transition-all focus:outline-none focus:ring-2 focus:ring-accent/30',
                        isSelected ? 'border-accent shadow-[0_0_0_1px_rgba(37,99,235,0.25)]' : 'border-border-default hover:border-border-strong',
                        draggedId === block.id && 'opacity-50',
                      )}
                      data-testid={`builder-block-${block.blockType}`}
                      draggable
                      onClick={() => setSelectedId(block.id)}
                      onDragEnd={() => {
                        setDraggedId(null);
                        setActiveDropIndex(null);
                      }}
                      onDragStart={(event) => {
                        setDraggedId(block.id);
                        setDragPayload(event, { kind: 'canvas', id: block.id });
                        event.dataTransfer.effectAllowed = 'move';
                      }}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          setSelectedId(block.id);
                        }
                      }}
                      role="button"
                      tabIndex={0}
                    >
                      <div className="mb-2 flex items-center justify-between gap-2">
                        <div className="flex min-w-0 items-center gap-2">
                          <GripVertical className="h-4 w-4 flex-shrink-0 text-content-muted" aria-hidden="true" />
                          <div className="min-w-0">
                            <p className="truncate text-sm font-semibold text-content-primary">{block.name}</p>
                            <p className="truncate text-xs text-content-secondary">
                              {block.blockType}
                              {block.settings?.visibilityRule ? ' / conditional' : ''}
                            </p>
                          </div>
                        </div>
                        <div className="flex flex-shrink-0 items-center gap-1 opacity-100 md:opacity-0 md:transition-opacity md:group-hover:opacity-100 md:group-focus-within:opacity-100">
                          <ToolbarIconButton
                            disabled={index === 0}
                            icon={<ArrowUp className="h-4 w-4" />}
                            label="Move block up"
                            onClick={() => moveBlockBy(block.id, -1)}
                          />
                          <ToolbarIconButton
                            disabled={index === blocks.length - 1}
                            icon={<ArrowDown className="h-4 w-4" />}
                            label="Move block down"
                            onClick={() => moveBlockBy(block.id, 2)}
                          />
                          <ToolbarIconButton
                            icon={<Copy className="h-4 w-4" />}
                            label="Duplicate block"
                            onClick={() => duplicateBlock(block.id)}
                          />
                          <ToolbarIconButton
                            icon={<Trash2 className="h-4 w-4" />}
                            label="Delete block"
                            onClick={() => removeBlock(block.id)}
                          />
                        </div>
                      </div>

                      <div
                        className={clsx(
                          'overflow-hidden rounded-lg border border-border-default bg-white',
                          isHidden && 'border-dashed opacity-70',
                        )}
                        style={blockPreviewStyle(block)}
                      >
                        {isHidden ? (
                          <div className="rounded-md bg-surface-secondary px-3 py-2 text-xs font-semibold text-content-secondary">
                            Hidden on {viewMode}
                          </div>
                        ) : (
                          <div dangerouslySetInnerHTML={{ __html: sanitizeEmailHtml(block.content) }} />
                        )}
                      </div>
                    </article>
                  </React.Fragment>
                );
              })}
              <DropZone
                activeDropIndex={activeDropIndex}
                draggedId={draggedId}
                index={blocks.length}
                onClear={() => setActiveDropIndex(null)}
                onDropAt={handleDropAt}
                onSetActive={setActiveDropIndex}
              />
            </div>
          )}
        </div>
      </section>

      <aside className="h-fit rounded-lg border border-border-default bg-surface-elevated/80 p-3">
        <div className="mb-3 flex items-center gap-2">
          <Settings2 className="h-4 w-4 text-brand-600" aria-hidden="true" />
          <h3 className="text-sm font-semibold text-content-primary">Inspector</h3>
        </div>

        {!selectedBlock ? (
          <div className="rounded-lg border border-dashed border-border-default p-4 text-sm text-content-secondary">
            Select a block to edit.
          </div>
        ) : (
          <div className="space-y-3">
            <div className="flex rounded-lg border border-border-default bg-surface-primary p-1">
              {[
                { key: 'content', label: 'Content', icon: <Code2 className="h-4 w-4" /> },
                { key: 'style', label: 'Style', icon: <Type className="h-4 w-4" /> },
                { key: 'rules', label: 'Rules', icon: <Braces className="h-4 w-4" /> },
              ].map((tab) => (
                <button
                  className={clsx(
                    'flex flex-1 items-center justify-center gap-1.5 rounded-md px-2 py-2 text-xs font-semibold transition-colors',
                    inspectorTab === tab.key ? 'bg-surface-elevated text-content-primary shadow-sm' : 'text-content-secondary hover:text-content-primary',
                  )}
                  key={tab.key}
                  onClick={() => setInspectorTab(tab.key as InspectorTab)}
                  type="button"
                >
                  {tab.icon}
                  {tab.label}
                </button>
              ))}
            </div>

            {inspectorTab === 'content' && (
              <div className="space-y-3">
                <Input
                  label="Block Name"
                  value={selectedBlock.name}
                  onChange={(event) => patchBlock(selectedBlock.id, { name: event.target.value })}
                />
                <div>
                  <label className="mb-1 block text-sm font-medium text-content-primary" htmlFor="selected-block-content">
                    HTML Content
                  </label>
                  <textarea
                    className="min-h-[240px] w-full rounded-lg border border-border-default bg-surface-primary p-3 font-mono text-xs text-content-primary focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
                    id="selected-block-content"
                    onChange={(event) => patchBlock(selectedBlock.id, { content: event.target.value })}
                    value={selectedBlock.content}
                  />
                </div>
              </div>
            )}

            {inspectorTab === 'style' && (
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <Input
                    label="Background"
                    type="color"
                    value={stringStyle(selectedBlock.styles, 'backgroundColor', '#ffffff')}
                    onChange={(event) => patchBlockStyles(selectedBlock.id, { backgroundColor: event.target.value })}
                  />
                  <Input
                    label="Text Color"
                    type="color"
                    value={stringStyle(selectedBlock.styles, 'textColor', '#0f172a')}
                    onChange={(event) => patchBlockStyles(selectedBlock.id, { textColor: event.target.value })}
                  />
                  <Input
                    label="Padding"
                    min={0}
                    type="number"
                    value={String(numericStyle(selectedBlock.styles, 'padding', 16))}
                    onChange={(event) => patchBlockStyles(selectedBlock.id, { padding: event.target.value })}
                  />
                  <Input
                    label="Radius"
                    min={0}
                    type="number"
                    value={String(numericStyle(selectedBlock.styles, 'borderRadius', 8))}
                    onChange={(event) => patchBlockStyles(selectedBlock.id, { borderRadius: event.target.value })}
                  />
                  <Input
                    label="Border"
                    min={0}
                    type="number"
                    value={String(numericStyle(selectedBlock.styles, 'borderWidth', 0))}
                    onChange={(event) => patchBlockStyles(selectedBlock.id, { borderWidth: event.target.value })}
                  />
                  <Input
                    label="Border Color"
                    type="color"
                    value={stringStyle(selectedBlock.styles, 'borderColor', '#e2e8f0')}
                    onChange={(event) => patchBlockStyles(selectedBlock.id, { borderColor: event.target.value })}
                  />
                </div>

                <div>
                  <p className="mb-1 block text-sm font-medium text-content-primary">Alignment</p>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { value: 'left', label: 'Left', icon: <AlignLeft className="h-4 w-4" /> },
                      { value: 'center', label: 'Center', icon: <AlignCenter className="h-4 w-4" /> },
                      { value: 'right', label: 'Right', icon: <AlignRight className="h-4 w-4" /> },
                    ].map((option) => (
                      <button
                        aria-label={`${option.label} align`}
                        className={clsx(
                          'inline-flex items-center justify-center rounded-lg border px-3 py-2 text-sm font-semibold transition-colors',
                          textAlignStyle(selectedBlock.styles) === option.value
                            ? 'border-accent bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-200'
                            : 'border-border-default text-content-secondary hover:border-border-strong hover:text-content-primary',
                        )}
                        key={option.value}
                        onClick={() => patchBlockStyles(selectedBlock.id, { textAlign: option.value })}
                        title={`${option.label} align`}
                        type="button"
                      >
                        {option.icon}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {inspectorTab === 'rules' && (
              <div className="space-y-3">
                <label className="flex items-center gap-2 rounded-lg border border-border-default bg-surface-primary p-3 text-sm font-medium text-content-primary">
                  <input
                    checked={Boolean(selectedBlock.settings?.hideOnMobile)}
                    className="h-4 w-4 rounded border-border-default text-accent focus:ring-accent"
                    onChange={(event) => patchBlockSettings(selectedBlock.id, { hideOnMobile: event.target.checked })}
                    type="checkbox"
                  />
                  Hide on mobile
                </label>
                <label className="flex items-center gap-2 rounded-lg border border-border-default bg-surface-primary p-3 text-sm font-medium text-content-primary">
                  <input
                    checked={Boolean(selectedBlock.settings?.hideOnDesktop)}
                    className="h-4 w-4 rounded border-border-default text-accent focus:ring-accent"
                    onChange={(event) => patchBlockSettings(selectedBlock.id, { hideOnDesktop: event.target.checked })}
                    type="checkbox"
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
            )}
          </div>
        )}
      </aside>
    </div>
  );
};
