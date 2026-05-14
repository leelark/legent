'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageChrome';
import { Skeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { sanitizeLandingPageHtml } from '@/lib/sanitize-html';
import {
  LandingPage,
  archiveLandingPage,
  createLandingPage,
  listLandingPages,
  publishLandingPage,
  updateLandingPage,
} from '@/lib/template-studio-api';

const defaultLandingHtml = `<section style="max-width:720px;margin:0 auto;padding:48px 24px;font-family:Arial,sans-serif">
  <h1>Campaign Landing Page</h1>
  <p>Use this page for gated content and post-click campaign experiences.</p>
  <div aria-label="Inert signup preview" style="display:grid;gap:12px;max-width:360px">
    <label>Email<br><input name="email" type="email" placeholder="preview only" disabled style="padding:10px;width:100%"></label>
    <button type="button" disabled style="padding:10px 16px">Preview only</button>
  </div>
</section>`;

const slugify = (value: string) =>
  value.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 100);

export default function LandingPageStudio() {
  const { addToast } = useToast();
  const [pages, setPages] = useState<LandingPage[]>([]);
  const [selected, setSelected] = useState<LandingPage | null>(null);
  const [name, setName] = useState('Campaign Landing Page');
  const [slug, setSlug] = useState('campaign-landing-page');
  const [htmlContent, setHtmlContent] = useState(defaultLandingHtml);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const publicPath = useMemo(() => (selected?.slug ? `/lp/${selected.slug}` : `/lp/${slug}`), [selected?.slug, slug]);

  const loadPages = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listLandingPages(0, 100);
      const items: LandingPage[] = Array.isArray(response) ? response : (response?.content ?? response?.data ?? []);
      setPages(items);
      if (!selected && items[0]) {
        setSelected(items[0]);
        setName(items[0].name);
        setSlug(items[0].slug);
        setHtmlContent(items[0].htmlContent || defaultLandingHtml);
      }
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Failed to load landing pages',
        message: error?.response?.data?.error?.message || 'Unable to load landing pages.',
      });
    } finally {
      setLoading(false);
    }
  }, [addToast, selected]);

  useEffect(() => {
    void loadPages();
  }, [loadPages]);

  const handleSelect = (page: LandingPage) => {
    setSelected(page);
    setName(page.name);
    setSlug(page.slug);
    setHtmlContent(page.htmlContent || defaultLandingHtml);
  };

  const handleNew = () => {
    setSelected(null);
    setName('Campaign Landing Page');
    setSlug('campaign-landing-page');
    setHtmlContent(defaultLandingHtml);
  };

  const handleSave = async (publish = false) => {
    setBusy(true);
    try {
      const payload = {
        name: name.trim(),
        slug: slugify(slug || name),
        htmlContent,
        metadata: JSON.stringify({ builder: 'landing-page-studio' }),
        publish,
      };
      const saved = selected
        ? await updateLandingPage(selected.id, payload)
        : await createLandingPage(payload);
      const finalPage = publish ? await publishLandingPage(saved.id) : saved;
      setSelected(finalPage);
      setName(finalPage.name);
      setSlug(finalPage.slug);
      setHtmlContent(finalPage.htmlContent || htmlContent);
      await loadPages();
      addToast({ type: 'success', title: publish ? 'Landing page published' : 'Landing page saved', message: finalPage.name });
    } catch (error: any) {
      addToast({
        type: 'error',
        title: publish ? 'Publish failed' : 'Save failed',
        message: error?.response?.data?.error?.message || 'Landing page operation failed.',
      });
    } finally {
      setBusy(false);
    }
  };

  const handleArchive = async (page: LandingPage) => {
    setBusy(true);
    try {
      await archiveLandingPage(page.id);
      await loadPages();
      addToast({ type: 'success', title: 'Landing page archived', message: page.name });
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Archive failed',
        message: error?.response?.data?.error?.message || 'Unable to archive landing page.',
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Post-click experiences"
        title="Landing Page Studio"
        description="Build public campaign pages with sanitized previews and live route preview."
        action={(
        <div className="flex flex-wrap gap-2">
          <Link href="/app/email/templates"><Button variant="secondary">Template Studio</Button></Link>
          <Button variant="secondary" onClick={handleNew}>New Page</Button>
          <Button variant="secondary" loading={busy} onClick={() => handleSave(false)}>Save Draft</Button>
          <Button loading={busy} onClick={() => handleSave(true)}>Publish</Button>
        </div>
        )}
      />

      <div className="grid gap-6 xl:grid-cols-[320px_1fr]">
        <Card>
          <CardHeader title="Pages" action={<Badge variant="info">{pages.length}</Badge>} />
          {loading ? (
            <div className="space-y-3 p-3">
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} className="h-14 rounded-lg" />
              ))}
            </div>
          ) : pages.length === 0 ? (
            <EmptyState type="empty" title="No landing pages" description="Create the first public campaign page." />
          ) : (
            <div className="divide-y divide-border-default">
              {pages.map((page) => (
                <button
                  key={page.id}
                  onClick={() => handleSelect(page)}
                  className="block w-full p-3 text-left hover:bg-surface-secondary"
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="font-medium text-content-primary">{page.name}</p>
                    <Badge variant={page.status === 'PUBLISHED' ? 'success' : page.status === 'ARCHIVED' ? 'default' : 'info'}>{page.status}</Badge>
                  </div>
                  <p className="mt-1 text-xs text-content-secondary">/lp/{page.slug}</p>
                </button>
              ))}
            </div>
          )}
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader
              title="Builder"
              subtitle="Form controls can be previewed, but browser submission is stripped unless a governed capture flow is added."
              action={selected ? <Button size="sm" variant="secondary" onClick={() => handleArchive(selected)}>Archive</Button> : null}
            />
            <div className="grid gap-4 p-4 md:grid-cols-2">
              <Input label="Name" value={name} onChange={(event) => setName(event.target.value)} />
              <Input label="Slug" value={slug} onChange={(event) => setSlug(slugify(event.target.value))} />
            </div>
            <div className="p-4 pt-0">
              <label className="mb-1 block text-sm font-medium text-content-primary">HTML</label>
              <textarea
                value={htmlContent}
                onChange={(event) => setHtmlContent(event.target.value)}
                rows={16}
                className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 font-mono text-xs"
              />
            </div>
          </Card>

          <Card>
            <CardHeader title="Public Preview" action={<Link href={publicPath} target="_blank"><Button size="sm" variant="secondary">Open Public Route</Button></Link>} />
            <div className="rounded-xl border border-border-default bg-white p-4 text-black shadow-inner">
              <div dangerouslySetInnerHTML={{ __html: sanitizeLandingPageHtml(htmlContent) }} />
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
