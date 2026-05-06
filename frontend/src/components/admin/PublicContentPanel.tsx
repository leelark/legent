'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createAdminPublicContent,
  listAdminPublicContent,
  publishAdminPublicContent,
  updateAdminPublicContent,
  type PublicContentRecord,
} from '@/lib/public-content-api';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';

type FormState = {
  id?: string;
  contentType: string;
  pageKey: string;
  slug: string;
  title: string;
  status: string;
  payload: string;
  seoMeta: string;
};

const blankForm: FormState = {
  contentType: 'PAGE',
  pageKey: 'home',
  slug: '',
  title: '',
  status: 'DRAFT',
  payload: '{\n  "heroTitle": "Enterprise Email Marketing. Reimagined."\n}',
  seoMeta: '{\n  "title": "Legent"\n}',
};

export function PublicContentPanel() {
  const [items, setItems] = useState<PublicContentRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<FormState>(blankForm);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const next = await listAdminPublicContent();
      setItems(next || []);
    } catch (err: any) {
      setError(err?.response?.data?.error?.message || err?.message || 'Failed to load public content.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const sortedItems = useMemo(
    () => [...items].sort((a, b) => (a.updatedAt || '').localeCompare(b.updatedAt || '')).reverse(),
    [items]
  );

  const save = async () => {
    setError(null);
    try {
      const payload = JSON.parse(form.payload || '{}');
      const seoMeta = JSON.parse(form.seoMeta || '{}');
      const body = {
        contentType: form.contentType,
        pageKey: form.pageKey,
        slug: form.slug || undefined,
        title: form.title || undefined,
        status: form.status,
        payload,
        seoMeta,
      };
      if (form.id) {
        await updateAdminPublicContent(form.id, body);
      } else {
        await createAdminPublicContent(body);
      }
      setForm(blankForm);
      await refresh();
    } catch (err: any) {
      setError(err?.message || 'Invalid JSON payload.');
    }
  };

  const edit = (item: PublicContentRecord) => {
    setForm({
      id: item.id,
      contentType: item.contentType,
      pageKey: item.pageKey,
      slug: item.slug || '',
      title: item.title || '',
      status: item.status || 'DRAFT',
      payload: JSON.stringify(item.payload || {}, null, 2),
      seoMeta: JSON.stringify(item.seoMeta || {}, null, 2),
    });
  };

  const togglePublish = async (item: PublicContentRecord) => {
    if (!item.id) return;
    await publishAdminPublicContent(item.id, item.status !== 'PUBLISHED');
    await refresh();
  };

  return (
    <Card className="space-y-4 p-6">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Public Content CMS</h3>
        <Button size="sm" variant="secondary" onClick={refresh} loading={loading}>
          Refresh
        </Button>
      </div>
      {error ? <p className="text-sm text-danger">{error}</p> : null}

      <div className="grid gap-3 md:grid-cols-2">
        <input className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm" value={form.contentType} onChange={(event) => setForm((prev) => ({ ...prev, contentType: event.target.value.toUpperCase() }))} placeholder="PAGE | BLOG" />
        <input className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm" value={form.pageKey} onChange={(event) => setForm((prev) => ({ ...prev, pageKey: event.target.value }))} placeholder="home" />
        <input className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm" value={form.slug} onChange={(event) => setForm((prev) => ({ ...prev, slug: event.target.value }))} placeholder="optional-slug" />
        <input className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm" value={form.title} onChange={(event) => setForm((prev) => ({ ...prev, title: event.target.value }))} placeholder="Title" />
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        <textarea className="min-h-40 rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-xs font-mono" value={form.payload} onChange={(event) => setForm((prev) => ({ ...prev, payload: event.target.value }))} />
        <textarea className="min-h-40 rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-xs font-mono" value={form.seoMeta} onChange={(event) => setForm((prev) => ({ ...prev, seoMeta: event.target.value }))} />
      </div>
      <div className="flex gap-2">
        <Button onClick={save}>{form.id ? 'Update Content' : 'Create Content'}</Button>
        <Button variant="secondary" onClick={() => setForm(blankForm)}>Reset</Button>
      </div>

      <div className="space-y-2 border-t border-border-default pt-4">
        {sortedItems.map((item) => (
          <div key={item.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm">
            <div className="min-w-0">
              <p className="font-medium">{item.contentType} • {item.pageKey}{item.slug ? `/${item.slug}` : ''}</p>
              <p className="text-xs text-content-secondary">{item.title || 'Untitled'} • {item.status}</p>
            </div>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => edit(item)}>Edit</Button>
              <Button size="sm" onClick={() => togglePublish(item)}>
                {item.status === 'PUBLISHED' ? 'Unpublish' : 'Publish'}
              </Button>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}

