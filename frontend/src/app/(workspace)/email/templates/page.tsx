'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { Star, Copy, Sparkle, Plus, Trash, Archive, ArrowCounterClockwise } from '@phosphor-icons/react';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { EmptyState } from '@/components/ui/EmptyState';
import { Badge } from '@/components/ui/Badge';
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

export default function EmailTemplatesPage() {
  const { addToast } = useToast();
  const [templates, setTemplates] = useState<Template[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [mode, setMode] = useState<FilterMode>('all');

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
      const items: Template[] = Array.isArray(response) ? response : (response?.content ?? response?.data ?? []);
      setTemplates(items);
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Failed to load templates',
        message: error?.response?.data?.error?.message || 'Unable to load template library.',
      });
    } finally {
      setLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    setFavoriteIds(JSON.parse(localStorage.getItem('template_favorites') ?? '[]'));
    setRecentIds(JSON.parse(localStorage.getItem('template_recent') ?? '[]'));
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
    }

    return results;
  }, [templates, query, categoryFilter, mode, favoriteIds, recentIds]);

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
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Create failed',
        message: error?.response?.data?.error?.message || 'Unable to create template.',
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
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Prebuilt import failed',
        message: error?.response?.data?.error?.message || 'Unable to import prebuilt template.',
      });
    }
  };

  const handleDuplicate = async (templateId: string) => {
    try {
      const clone = await cloneTemplate(templateId);
      setTemplates((current) => [clone, ...current]);
      addToast({ type: 'success', title: 'Template duplicated', message: `${clone.name} created.` });
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Duplicate failed',
        message: error?.response?.data?.error?.message || 'Unable to duplicate template.',
      });
    }
  };

  const handleArchive = async (templateId: string) => {
    try {
      const updated = await archiveTemplate(templateId, 'Archived from template library');
      setTemplates((current) => current.map((item) => (item.id === templateId ? updated : item)));
      addToast({ type: 'success', title: 'Template archived', message: `${updated.name} moved to archive.` });
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Archive failed',
        message: error?.response?.data?.error?.message || 'Unable to archive template.',
      });
    }
  };

  const handleRestore = async (templateId: string) => {
    try {
      const updated = await restoreTemplate(templateId, 'Restored from template library');
      setTemplates((current) => current.map((item) => (item.id === templateId ? updated : item)));
      addToast({ type: 'success', title: 'Template restored', message: `${updated.name} restored.` });
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Restore failed',
        message: error?.response?.data?.error?.message || 'Unable to restore template.',
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
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Delete failed',
        message: error?.response?.data?.error?.message || 'Unable to delete template.',
      });
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Template Studio</h1>
          <p className="mt-1 text-sm text-content-secondary">Build reusable, branded email templates with versioned workflows.</p>
        </div>
        <div className="flex gap-2">
          <Link href="/app/email/landing-pages">
            <Button variant="secondary">Landing Pages</Button>
          </Link>
          <Link href="/app/email">
            <Button variant="secondary">Back to Email Studio</Button>
          </Link>
        </div>
      </div>

      <Card>
        <CardHeader title="Quick Start Library" subtitle="Start from professionally designed templates." />
        <div className="grid gap-3 p-6 sm:grid-cols-2 xl:grid-cols-5">
          {PREBUILT_TEMPLATES.map((preset) => (
            <div key={preset.key} className="rounded-xl border border-border-default bg-surface-secondary p-3">
              <p className="font-medium text-content-primary">{preset.name}</p>
              <p className="mt-1 text-xs text-content-secondary">{preset.category}</p>
              <p className="mt-2 line-clamp-2 text-xs text-content-muted">{preset.subject}</p>
              <Button size="sm" className="mt-3 w-full" onClick={() => handleUsePrebuilt(preset)}>
                Use Template
              </Button>
            </div>
          ))}
        </div>
      </Card>

      <Card>
        <CardHeader title="Create Blank Template" />
        <div className="grid gap-4 p-6 md:grid-cols-3">
          <Input label="Name" value={name} onChange={(event) => setName(event.target.value)} placeholder="May Newsletter" />
          <Input label="Subject" value={subject} onChange={(event) => setSubject(event.target.value)} placeholder="Latest product updates" />
          <Button icon={<Plus size={16} />} loading={creating} disabled={creating} onClick={handleCreateBlank}>
            Create Blank
          </Button>
        </div>
        <div className="p-6 pt-0">
          <label className="mb-1 block text-sm font-medium text-content-primary">HTML Body</label>
          <textarea
            value={htmlBody}
            onChange={(event) => setHtmlBody(event.target.value)}
            rows={4}
            className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
            placeholder="<h1>Template content</h1>"
          />
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Template Library"
          action={<Badge variant="info">{filteredTemplates.length} templates</Badge>}
        />
        <div className="grid gap-3 border-t border-border-default p-4 md:grid-cols-4">
          <div className="md:col-span-2">
            <Input
              label="Search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search by name, subject, tags"
            />
          </div>
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
        </div>

        {loading ? (
          <div className="p-8 text-sm text-content-secondary">Loading template library...</div>
        ) : filteredTemplates.length === 0 ? (
          <EmptyState
            type="empty"
            title="No templates found"
            description="Adjust filters or create a new template to begin."
          />
        ) : (
          <div className="divide-y divide-border-default">
            {filteredTemplates.map((template) => (
              <div key={template.id} className="flex flex-col gap-3 p-4 md:flex-row md:items-center md:justify-between">
                <Link href={`/app/email/templates/${template.id}`} className="block flex-1" onClick={() => markRecent(template.id)}>
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-semibold text-content-primary">{template.name}</p>
                    <Badge variant={template.status === 'PUBLISHED' ? 'success' : 'default'}>{template.status}</Badge>
                    {template.category && <Badge variant="info">{template.category}</Badge>}
                  </div>
                  <p className="mt-1 text-sm text-content-secondary">{template.subject || 'No subject set'}</p>
                </Link>
                <div className="flex items-center gap-2">
                  <Button
                    size="sm"
                    variant={favoriteIds.includes(template.id) ? 'primary' : 'secondary'}
                    onClick={() => toggleFavorite(template.id)}
                  >
                    <Star size={14} weight={favoriteIds.includes(template.id) ? 'fill' : 'regular'} />
                    Favorite
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => handleDuplicate(template.id)}>
                    <Copy size={14} />
                    Duplicate
                  </Button>
                  {template.status === 'ARCHIVED' ? (
                    <Button size="sm" variant="secondary" onClick={() => handleRestore(template.id)}>
                      <ArrowCounterClockwise size={14} />
                      Restore
                    </Button>
                  ) : (
                    <Button size="sm" variant="secondary" onClick={() => handleArchive(template.id)}>
                      <Archive size={14} />
                      Archive
                    </Button>
                  )}
                  <Button size="sm" variant="danger" onClick={() => handleDelete(template.id)}>
                    <Trash size={14} />
                    Delete
                  </Button>
                  <Link href={`/app/email/templates/${template.id}`}>
                    <Button size="sm" icon={<Sparkle size={14} />}>Open Studio</Button>
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
