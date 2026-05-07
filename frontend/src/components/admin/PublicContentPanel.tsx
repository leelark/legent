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
import { useToast } from '@/components/ui/Toast';
import { AdminEmptyState, AdminPanel, AdminSkeletonRows, AdminTableShell, StatusPill } from '@/components/admin/AdminChrome';

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

const inputClass = 'rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20';
const textAreaClass = 'min-h-40 rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-xs font-mono text-content-primary placeholder-content-muted focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20';

export function PublicContentPanel() {
  const [items, setItems] = useState<PublicContentRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<FormState>(blankForm);
  const [error, setError] = useState<string | null>(null);
  const { addToast } = useToast();

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
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
    setSaving(true);
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
      addToast({ type: 'success', title: form.id ? 'Content updated' : 'Content created', message: `${body.pageKey} is saved as ${body.status}.` });
    } catch (err: any) {
      setError(err?.message || 'Invalid JSON payload.');
    } finally {
      setSaving(false);
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
    const nextPublished = item.status !== 'PUBLISHED';
    if (!window.confirm(`${nextPublished ? 'Publish' : 'Unpublish'} ${item.title || item.pageKey}?`)) {
      return;
    }
    await publishAdminPublicContent(item.id, nextPublished);
    await refresh();
    addToast({ type: 'success', title: nextPublished ? 'Content published' : 'Content unpublished', message: item.title || item.pageKey });
  };

  return (
    <AdminPanel
      title="Public Content CMS"
      subtitle="Static page and blog content controls with JSON validation, draft/publish state, and SEO metadata."
      action={<Button size="sm" variant="secondary" onClick={refresh} loading={loading}>Refresh</Button>}
    >
      <div className="space-y-4">
        {error ? <div className="rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">{error}</div> : null}

        <div className="grid gap-3 md:grid-cols-2">
          <input className={inputClass} value={form.contentType} onChange={(event) => setForm((prev) => ({ ...prev, contentType: event.target.value.toUpperCase() }))} placeholder="PAGE | BLOG" />
          <input className={inputClass} value={form.pageKey} onChange={(event) => setForm((prev) => ({ ...prev, pageKey: event.target.value }))} placeholder="home" />
          <input className={inputClass} value={form.slug} onChange={(event) => setForm((prev) => ({ ...prev, slug: event.target.value }))} placeholder="optional-slug" />
          <input className={inputClass} value={form.title} onChange={(event) => setForm((prev) => ({ ...prev, title: event.target.value }))} placeholder="Title" />
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <textarea aria-label="Content payload JSON" className={textAreaClass} value={form.payload} onChange={(event) => setForm((prev) => ({ ...prev, payload: event.target.value }))} />
          <textarea aria-label="SEO metadata JSON" className={textAreaClass} value={form.seoMeta} onChange={(event) => setForm((prev) => ({ ...prev, seoMeta: event.target.value }))} />
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={save} loading={saving}>{form.id ? 'Update Content' : 'Create Content'}</Button>
          <Button variant="secondary" onClick={() => setForm(blankForm)}>Reset</Button>
        </div>

        <div className="border-t border-border-default pt-4">
          {loading ? (
            <AdminSkeletonRows rows={4} />
          ) : sortedItems.length === 0 ? (
            <AdminEmptyState title="No public content records" description="Create a page, pricing, or blog record to manage public-site copy from admin." />
          ) : (
            <AdminTableShell>
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead className="bg-surface-secondary text-xs uppercase tracking-wide text-content-muted">
                  <tr>
                    <th className="px-4 py-3">Record</th>
                    <th className="px-4 py-3">Title</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Updated</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-default">
                  {sortedItems.map((item) => (
                    <tr key={item.id} className="transition-colors hover:bg-surface-secondary/70">
                      <td className="px-4 py-3">
                        <p className="font-medium text-content-primary">{item.contentType} / {item.pageKey}{item.slug ? `/${item.slug}` : ''}</p>
                        <p className="text-xs text-content-muted">{item.id}</p>
                      </td>
                      <td className="px-4 py-3 text-content-secondary">{item.title || 'Untitled'}</td>
                      <td className="px-4 py-3"><StatusPill status={item.status || 'DRAFT'} /></td>
                      <td className="px-4 py-3 text-xs text-content-muted">{item.updatedAt ? new Date(item.updatedAt).toLocaleString() : '-'}</td>
                      <td className="px-4 py-3 text-right">
                        <div className="inline-flex gap-2">
                          <Button size="sm" variant="secondary" onClick={() => edit(item)}>Edit</Button>
                          <Button size="sm" onClick={() => togglePublish(item)}>
                            {item.status === 'PUBLISHED' ? 'Unpublish' : 'Publish'}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableShell>
          )}
        </div>
      </div>
    </AdminPanel>
  );
}
