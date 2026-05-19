'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import {
  Archive,
  Clock3,
  Copy,
  FileCheck2,
  Grid2X2,
  LayoutList,
  Layers,
  Plus,
  RotateCcw,
  Sparkles,
  Star,
  Trash2,
  WandSparkles,
} from 'lucide-react';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { EmptyState } from '@/components/ui/EmptyState';
import { Badge } from '@/components/ui/Badge';
import { ActionBar, MetricCard, PageHeader, Panel } from '@/components/ui/PageChrome';
import { Skeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import {
  Template,
  archiveTemplate,
  cloneTemplate,
  createTemplate,
  deleteTemplate,
  importTemplateHtml,
  listTemplates,
  restoreTemplate,
} from '@/lib/template-studio-api';

const PREBUILT_TEMPLATES: Array<{
  key: string;
  name: string;
  category: string;
  subject: string;
  html: string;
}> = [
  { key: 'newsletter', name: 'Newsletter', category: 'Newsletter', subject: 'Weekly product digest', html: '<h1>Weekly Digest</h1><p>{{firstName}}, here are top updates.</p>' },
  { key: 'promotion', name: 'Promotion', category: 'Promotion', subject: 'Limited-time offer for you', html: '<h1>Save 30% today</h1><p>Use code {{promoCode}}</p>' },
  { key: 'product-launch', name: 'Product Launch', category: 'Announcement', subject: 'Meet our new release', html: '<h1>New launch</h1><p>Introducing {{productName}}</p>' },
  { key: 'event-invite', name: 'Event Invite', category: 'Event', subject: 'You are invited to our event', html: '<h1>Event Invitation</h1><p>Join us on {{eventDate}}</p>' },
  { key: 'onboarding', name: 'Onboarding', category: 'Lifecycle', subject: 'Let us get you set up', html: '<h1>Welcome aboard</h1><p>Complete step 1 to begin.</p>' },
  { key: 'welcome', name: 'Welcome Email', category: 'Lifecycle', subject: 'Welcome to Legent', html: '<h1>Welcome {{firstName}}</h1><p>We are glad you are here.</p>' },
  { key: 'abandoned-cart', name: 'Abandoned Cart', category: 'Ecommerce', subject: 'You left something behind', html: '<h1>Still interested?</h1><p>Your cart is waiting.</p>' },
  { key: 'festival', name: 'Festival Campaign', category: 'Seasonal', subject: 'Festival specials are live', html: '<h1>Festival offers</h1><p>Celebrate with exclusive deals.</p>' },
  { key: 'announcement', name: 'Announcement', category: 'Announcement', subject: 'Important platform update', html: '<h1>Platform announcement</h1><p>Read latest update details.</p>' },
  { key: 'transactional', name: 'Transactional', category: 'Transactional', subject: 'Your receipt from {{companyName}}', html: '<h1>Receipt</h1><p>Order {{orderId}} confirmed.</p>' },
];

type FilterMode = 'all' | 'favorites' | 'recent';
type ViewMode = 'list' | 'grid';
type SortMode = 'updated' | 'name' | 'status';

type ApiErrorLike = {
  normalized?: { message?: unknown };
  response?: { data?: { error?: { message?: unknown } } };
  message?: unknown;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function asArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) {
    return value as T[];
  }
  if (!isRecord(value)) {
    return [];
  }
  if (Array.isArray(value.content)) {
    return value.content as T[];
  }
  if (Array.isArray(value.data)) {
    return value.data as T[];
  }
  return [];
}

function readStoredIds(key: string) {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === 'string') : [];
  } catch {
    return [];
  }
}

function statusVariant(status?: string): 'default' | 'success' | 'warning' {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'ARCHIVED') return 'warning';
  return 'default';
}

function templateTimestamp(template: Template) {
  const value = template.updatedAt || template.createdAt || '';
  const timestamp = Date.parse(value);
  return Number.isNaN(timestamp) ? 0 : timestamp;
}

function formatDate(value?: string) {
  if (!value) return 'Not updated';
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return 'Unknown';
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(timestamp));
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!isRecord(error)) {
    return fallback;
  }
  const candidate = error as ApiErrorLike;
  const message =
    candidate.normalized?.message ??
    candidate.response?.data?.error?.message ??
    candidate.message;
  return typeof message === 'string' && message.trim() ? message : fallback;
}

export default function EmailTemplatesPage() {
  const { addToast } = useToast();
  const [templates, setTemplates] = useState<Template[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [mode, setMode] = useState<FilterMode>('all');
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [sortMode, setSortMode] = useState<SortMode>('updated');

  const [name, setName] = useState('');
  const [subject, setSubject] = useState('');
  const [htmlBody, setHtmlBody] = useState('');
  const [creating, setCreating] = useState(false);

  const [favoriteIds, setFavoriteIds] = useState<string[]>([]);
  const [recentIds, setRecentIds] = useState<string[]>([]);

  const loadTemplates = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listTemplates(0, 100);
      const items = asArray<Template>(response);
      setTemplates(items);
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Failed to load templates',
        message: getErrorMessage(error, 'Unable to load template library.'),
      });
    } finally {
      setLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    setFavoriteIds(readStoredIds('template_favorites'));
    setRecentIds(readStoredIds('template_recent'));
    void loadTemplates();
  }, [loadTemplates]);

  const categories = useMemo(() => {
    const values = Array.from(new Set(templates.map((template) => template.category).filter(Boolean)));
    return ['all', ...values] as string[];
  }, [templates]);

  const filteredTemplates = useMemo(() => {
    let results = [...templates];

    if (categoryFilter !== 'all') {
      results = results.filter((template) => (template.category || 'General') === categoryFilter);
    }

    if (query.trim()) {
      const lower = query.toLowerCase();
      results = results.filter((template) => {
        const tags = template.tags?.join(' ').toLowerCase() ?? '';
        return (
          template.name.toLowerCase().includes(lower) ||
          (template.subject || '').toLowerCase().includes(lower) ||
          tags.includes(lower)
        );
      });
    }

    if (mode === 'favorites') {
      results = results.filter((template) => favoriteIds.includes(template.id));
    }

    if (mode === 'recent') {
      const ordering = new Map(recentIds.map((id, index) => [id, index]));
      results = results.filter((template) => recentIds.includes(template.id));
      results.sort((a, b) => (ordering.get(a.id) ?? 9999) - (ordering.get(b.id) ?? 9999));
    } else if (sortMode === 'name') {
      results.sort((a, b) => a.name.localeCompare(b.name));
    } else if (sortMode === 'status') {
      results.sort((a, b) => a.status.localeCompare(b.status) || templateTimestamp(b) - templateTimestamp(a));
    } else {
      results.sort((a, b) => templateTimestamp(b) - templateTimestamp(a));
    }

    return results;
  }, [templates, query, categoryFilter, mode, sortMode, favoriteIds, recentIds]);

  const libraryStats = useMemo(() => {
    const published = templates.filter((template) => template.status === 'PUBLISHED').length;
    const archived = templates.filter((template) => template.status === 'ARCHIVED').length;
    const draft = templates.filter((template) => template.status !== 'PUBLISHED' && template.status !== 'ARCHIVED').length;
    return { published, archived, draft };
  }, [templates]);

  const persistFavorites = (nextIds: string[]) => {
    setFavoriteIds(nextIds);
    localStorage.setItem('template_favorites', JSON.stringify(nextIds));
  };

  const toggleFavorite = (templateId: string) => {
    if (favoriteIds.includes(templateId)) {
      persistFavorites(favoriteIds.filter((id) => id !== templateId));
    } else {
      persistFavorites([...favoriteIds, templateId]);
    }
  };

  const markRecent = (templateId: string) => {
    const next = [templateId, ...recentIds.filter((id) => id !== templateId)].slice(0, 20);
    setRecentIds(next);
    localStorage.setItem('template_recent', JSON.stringify(next));
  };

  const handleCreateBlank = async () => {
    if (!name.trim() || !subject.trim() || !htmlBody.trim()) {
      addToast({ type: 'warning', title: 'Validation', message: 'Name, subject, and HTML body are required.' });
      return;
    }
    setCreating(true);
    try {
      const template = await createTemplate({
        name: name.trim(),
        subject: subject.trim(),
        body: htmlBody,
        textContent: htmlBody.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim(),
        category: 'General',
        tags: [],
        metadata: '{}',
      });
      setTemplates((current) => [template, ...current]);
      setName('');
      setSubject('');
      setHtmlBody('');
      addToast({ type: 'success', title: 'Template created', message: `Created ${template.name}` });
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Create failed',
        message: getErrorMessage(error, 'Unable to create template.'),
      });
    } finally {
      setCreating(false);
    }
  };

  const handleUsePrebuilt = async (preset: (typeof PREBUILT_TEMPLATES)[number]) => {
    try {
      const template = await importTemplateHtml({
        name: `${preset.name} ${new Date().toLocaleDateString()}`,
        subject: preset.subject,
        htmlContent: preset.html,
        textContent: preset.html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim(),
        category: preset.category,
        tags: ['prebuilt'],
        metadata: JSON.stringify({ source: 'prebuilt', templateKey: preset.key }),
        publish: false,
      });
      setTemplates((current) => [template, ...current]);
      addToast({ type: 'success', title: 'Prebuilt template added', message: `${preset.name} ready to edit.` });
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Prebuilt import failed',
        message: getErrorMessage(error, 'Unable to import prebuilt template.'),
      });
    }
  };

  const handleDuplicate = async (templateId: string) => {
    try {
      const clone = await cloneTemplate(templateId);
      setTemplates((current) => [clone, ...current]);
      addToast({ type: 'success', title: 'Template duplicated', message: `${clone.name} created.` });
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Duplicate failed',
        message: getErrorMessage(error, 'Unable to duplicate template.'),
      });
    }
  };

  const handleArchive = async (templateId: string) => {
    try {
      const updated = await archiveTemplate(templateId, 'Archived from template library');
      setTemplates((current) => current.map((item) => (item.id === templateId ? updated : item)));
      addToast({ type: 'success', title: 'Template archived', message: `${updated.name} moved to archive.` });
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Archive failed',
        message: getErrorMessage(error, 'Unable to archive template.'),
      });
    }
  };

  const handleRestore = async (templateId: string) => {
    try {
      const updated = await restoreTemplate(templateId, 'Restored from template library');
      setTemplates((current) => current.map((item) => (item.id === templateId ? updated : item)));
      addToast({ type: 'success', title: 'Template restored', message: `${updated.name} restored.` });
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Restore failed',
        message: getErrorMessage(error, 'Unable to restore template.'),
      });
    }
  };

  const handleDelete = async (templateId: string) => {
    if (!window.confirm('Delete this template?')) {
      return;
    }
    try {
      await deleteTemplate(templateId);
      setTemplates((current) => current.filter((item) => item.id !== templateId));
      addToast({ type: 'success', title: 'Template deleted', message: 'Template removed.' });
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Delete failed',
        message: getErrorMessage(error, 'Unable to delete template.'),
      });
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Content operations"
        title="Template Studio"
        description="Build reusable, branded email templates with versioned workflows, presets, and approval-ready content."
        action={(
        <div className="flex flex-wrap gap-2">
          <Link href="/app/email/landing-pages">
            <Button variant="secondary">Landing Pages</Button>
          </Link>
          <Link href="/app/email">
            <Button variant="secondary">Back to Email Studio</Button>
          </Link>
        </div>
        )}
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Total Library"
          value={templates.length}
          helper={`${filteredTemplates.length} in current view`}
          icon={<Layers size={18} />}
          accent="brand"
        />
        <MetricCard
          label="Published"
          value={libraryStats.published}
          helper={`${libraryStats.draft} drafts need work`}
          icon={<FileCheck2 size={18} />}
          accent="success"
        />
        <MetricCard
          label="Favorites"
          value={favoriteIds.length}
          helper={`${recentIds.length} recently opened`}
          icon={<Star size={18} />}
          accent="warning"
        />
        <MetricCard
          label="Archived"
          value={libraryStats.archived}
          helper="Restore when content becomes reusable"
          icon={<Archive size={18} />}
          accent={libraryStats.archived > 0 ? 'warning' : 'brand'}
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Panel className="space-y-4">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-secondary">Starting points</p>
              <h2 className="mt-1 text-lg font-semibold text-content-primary">Prebuilt campaign templates</h2>
            </div>
            <Badge variant="info">{PREBUILT_TEMPLATES.length} presets</Badge>
          </div>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {PREBUILT_TEMPLATES.map((preset) => (
              <div key={preset.key} className="rounded-lg border border-border-default bg-surface-primary/70 p-3">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate font-medium text-content-primary">{preset.name}</p>
                    <p className="mt-1 text-xs text-content-secondary">{preset.category}</p>
                  </div>
                  <WandSparkles size={16} className="mt-0.5 shrink-0 text-brand-500" aria-hidden="true" />
                </div>
                <p className="mt-2 line-clamp-2 text-xs text-content-muted">{preset.subject}</p>
                <Button size="sm" className="mt-3 w-full" icon={<Plus size={14} />} onClick={() => handleUsePrebuilt(preset)}>
                  Use Template
                </Button>
              </div>
            ))}
          </div>
        </Panel>

        <Panel className="space-y-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-secondary">Create</p>
            <h2 className="mt-1 text-lg font-semibold text-content-primary">Blank HTML template</h2>
          </div>
          <div className="space-y-3">
            <Input label="Name" value={name} onChange={(event) => setName(event.target.value)} placeholder="May Newsletter" />
            <Input label="Subject" value={subject} onChange={(event) => setSubject(event.target.value)} placeholder="Latest product updates" />
            <div>
              <label className="mb-1 block text-sm font-medium text-content-primary">HTML Body</label>
              <textarea
                value={htmlBody}
                onChange={(event) => setHtmlBody(event.target.value)}
                rows={7}
                className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 font-mono text-xs text-content-primary"
                placeholder="<h1>Template content</h1>"
              />
            </div>
            <Button className="w-full" icon={<Plus size={16} />} loading={creating} disabled={creating} onClick={handleCreateBlank}>
              Create Blank
            </Button>
          </div>
        </Panel>
      </div>

      <Card>
        <CardHeader
          title="Template Library"
          action={<Badge variant="info">{filteredTemplates.length} templates</Badge>}
        />
        <div className="border-t border-border-default p-4">
          <ActionBar className="items-stretch sm:items-end">
            <div className="grid flex-1 gap-3 md:grid-cols-[minmax(0,1.6fr)_180px_160px_160px]">
              <Input
                label="Search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search by name, subject, tags"
              />
              <div>
                <label className="mb-1 block text-sm font-medium text-content-primary">Category</label>
                <select
                  value={categoryFilter}
                  onChange={(event) => setCategoryFilter(event.target.value)}
                  className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
                >
                  {categories.map((category) => (
                    <option key={category} value={category}>
                      {category === 'all' ? 'All categories' : category}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-content-primary">Filter</label>
                <select
                  value={mode}
                  onChange={(event) => setMode(event.target.value as FilterMode)}
                  className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
                >
                  <option value="all">All</option>
                  <option value="favorites">Favorites</option>
                  <option value="recent">Recent</option>
                </select>
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-content-primary">Sort</label>
                <select
                  value={sortMode}
                  onChange={(event) => setSortMode(event.target.value as SortMode)}
                  className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
                >
                  <option value="updated">Latest update</option>
                  <option value="name">Name</option>
                  <option value="status">Status</option>
                </select>
              </div>
            </div>
            <div className="flex items-end gap-2">
              <Button
                size="icon"
                variant={viewMode === 'list' ? 'primary' : 'secondary'}
                aria-label="List view"
                title="List view"
                onClick={() => setViewMode('list')}
              >
                <LayoutList size={16} />
              </Button>
              <Button
                size="icon"
                variant={viewMode === 'grid' ? 'primary' : 'secondary'}
                aria-label="Grid view"
                title="Grid view"
                onClick={() => setViewMode('grid')}
              >
                <Grid2X2 size={16} />
              </Button>
            </div>
          </ActionBar>
        </div>

        {loading ? (
          <div className="space-y-3 p-4">
            {Array.from({ length: 5 }).map((_, index) => (
              <Skeleton key={index} className="h-16 rounded-lg" />
            ))}
          </div>
        ) : filteredTemplates.length === 0 ? (
          <EmptyState
            type="empty"
            title="No templates found"
            description="Adjust filters or create a new template to begin."
          />
        ) : viewMode === 'grid' ? (
          <div className="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-3">
            {filteredTemplates.map((template) => (
              <article
                key={template.id}
                className="flex min-h-[230px] flex-col justify-between rounded-lg border border-border-default bg-surface-secondary/70 p-4"
                data-testid={`template-card-${template.id}`}
              >
                <div className="min-w-0">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate font-semibold text-content-primary">{template.name}</p>
                      <p className="mt-1 line-clamp-2 text-sm text-content-secondary">{template.subject || 'No subject set'}</p>
                    </div>
                    <Badge variant={statusVariant(template.status)}>{template.status}</Badge>
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {template.category && <Badge variant="info">{template.category}</Badge>}
                    {template.lastPublishedVersion != null && <Badge>v{template.lastPublishedVersion}</Badge>}
                    {favoriteIds.includes(template.id) && <Badge variant="warning">Favorite</Badge>}
                  </div>
                  <div className="mt-4 grid grid-cols-2 gap-3 text-xs text-content-secondary">
                    <div>
                      <p className="font-medium text-content-primary">Updated</p>
                      <p>{formatDate(template.updatedAt || template.createdAt)}</p>
                    </div>
                    <div>
                      <p className="font-medium text-content-primary">Type</p>
                      <p>{template.templateType || 'Email'}</p>
                    </div>
                  </div>
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-2">
                  <Button
                    size="icon"
                    variant={favoriteIds.includes(template.id) ? 'primary' : 'secondary'}
                    aria-label={favoriteIds.includes(template.id) ? 'Unfavorite template' : 'Favorite template'}
                    title={favoriteIds.includes(template.id) ? 'Unfavorite template' : 'Favorite template'}
                    onClick={() => toggleFavorite(template.id)}
                  >
                    <Star size={16} fill={favoriteIds.includes(template.id) ? 'currentColor' : 'none'} />
                  </Button>
                  <Button size="icon" variant="secondary" aria-label="Duplicate template" title="Duplicate template" onClick={() => handleDuplicate(template.id)}>
                    <Copy size={16} />
                  </Button>
                  {template.status === 'ARCHIVED' ? (
                    <Button size="icon" variant="secondary" aria-label="Restore template" title="Restore template" onClick={() => handleRestore(template.id)}>
                      <RotateCcw size={16} />
                    </Button>
                  ) : (
                    <Button size="icon" variant="secondary" aria-label="Archive template" title="Archive template" onClick={() => handleArchive(template.id)}>
                      <Archive size={16} />
                    </Button>
                  )}
                  <Link href={`/app/email/templates/${template.id}`} onClick={() => markRecent(template.id)} className="ml-auto">
                    <Button size="sm" icon={<Sparkles size={14} />}>Open</Button>
                  </Link>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className="divide-y divide-border-default">
            {filteredTemplates.map((template) => (
              <div key={template.id} className="flex flex-col gap-3 p-4 md:flex-row md:items-center md:justify-between">
                <Link href={`/app/email/templates/${template.id}`} className="block flex-1" onClick={() => markRecent(template.id)}>
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-semibold text-content-primary">{template.name}</p>
                    <Badge variant={statusVariant(template.status)}>{template.status}</Badge>
                    {template.category && <Badge variant="info">{template.category}</Badge>}
                    {template.lastPublishedVersion != null && <Badge>v{template.lastPublishedVersion}</Badge>}
                  </div>
                  <p className="mt-1 text-sm text-content-secondary">{template.subject || 'No subject set'}</p>
                  <p className="mt-1 flex items-center gap-1 text-xs text-content-muted">
                    <Clock3 size={12} aria-hidden="true" />
                    Updated {formatDate(template.updatedAt || template.createdAt)}
                  </p>
                </Link>
                <div className="flex items-center gap-2">
                  <Button
                    size="icon"
                    variant={favoriteIds.includes(template.id) ? 'primary' : 'secondary'}
                    aria-label={favoriteIds.includes(template.id) ? 'Unfavorite template' : 'Favorite template'}
                    title={favoriteIds.includes(template.id) ? 'Unfavorite template' : 'Favorite template'}
                    onClick={() => toggleFavorite(template.id)}
                  >
                    <Star size={16} fill={favoriteIds.includes(template.id) ? 'currentColor' : 'none'} />
                  </Button>
                  <Button size="icon" variant="secondary" aria-label="Duplicate template" title="Duplicate template" onClick={() => handleDuplicate(template.id)}>
                    <Copy size={16} />
                  </Button>
                  {template.status === 'ARCHIVED' ? (
                    <Button size="icon" variant="secondary" aria-label="Restore template" title="Restore template" onClick={() => handleRestore(template.id)}>
                      <RotateCcw size={16} />
                    </Button>
                  ) : (
                    <Button size="icon" variant="secondary" aria-label="Archive template" title="Archive template" onClick={() => handleArchive(template.id)}>
                      <Archive size={16} />
                    </Button>
                  )}
                  <Button size="icon" variant="danger" aria-label="Delete template" title="Delete template" onClick={() => handleDelete(template.id)}>
                    <Trash2 size={16} />
                  </Button>
                  <Link href={`/app/email/templates/${template.id}`} onClick={() => markRecent(template.id)}>
                    <Button size="sm" icon={<Sparkles size={14} />}>Open Studio</Button>
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
