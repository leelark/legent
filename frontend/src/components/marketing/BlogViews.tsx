'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { MarketingShell } from '@/components/marketing/MarketingShell';
import { getPublicBlogPost, listPublicBlogPosts, type PublicContentRecord } from '@/lib/public-content-api';

export function BlogIndexView() {
  const [posts, setPosts] = useState<PublicContentRecord[]>([]);

  useEffect(() => {
    let mounted = true;
    listPublicBlogPosts()
      .then((next) => {
        if (mounted) {
          setPosts(next ?? []);
        }
      })
      .catch(() => {
        if (mounted) {
          setPosts([]);
        }
      });
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <MarketingShell>
      <div className="mx-auto max-w-5xl px-6 py-16">
        <h1 className="text-4xl font-semibold tracking-tight">Legent Blog</h1>
        <p className="mt-3 text-content-secondary">Product updates, deliverability playbooks, automation strategy.</p>
        <div className="mt-10 grid gap-4">
          {posts.length === 0 ? (
            <div className="rounded-2xl border border-border-default bg-surface-elevated p-6 text-sm text-content-secondary">
              No posts published yet.
            </div>
          ) : (
            posts.map((post) => (
              <Link
                key={post.id ?? post.slug ?? post.title}
                href={`/blog/${post.slug ?? ''}`}
                className="rounded-2xl border border-border-default bg-surface-elevated p-6 transition hover:border-brand-300 hover:shadow-soft"
              >
                <h2 className="text-xl font-semibold">{post.title ?? 'Untitled Post'}</h2>
                <p className="mt-2 text-sm text-content-secondary">
                  {String((post.payload ?? {}).summary ?? 'Open article')}
                </p>
              </Link>
            ))
          )}
        </div>
      </div>
    </MarketingShell>
  );
}

export function BlogPostView({ slug }: { slug: string }) {
  const [post, setPost] = useState<PublicContentRecord | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    getPublicBlogPost(slug)
      .then((next) => {
        if (mounted) {
          setPost(next);
          setError(null);
        }
      })
      .catch(() => {
        if (mounted) {
          setError('Blog post not found.');
          setPost(null);
        }
      });
    return () => {
      mounted = false;
    };
  }, [slug]);

  const body = String((post?.payload ?? {}).body ?? '');

  return (
    <MarketingShell>
      <article className="mx-auto max-w-4xl px-6 py-16">
        <Link href="/blog" className="text-sm text-brand-600 hover:text-brand-700">
          ← Back to blog
        </Link>
        <h1 className="mt-4 text-4xl font-semibold tracking-tight">{post?.title ?? 'Blog'}</h1>
        {error ? (
          <p className="mt-6 rounded-xl border border-border-default bg-surface-elevated p-4 text-content-secondary">{error}</p>
        ) : (
          <div className="prose prose-slate mt-8 max-w-none dark:prose-invert">
            {body ? <div dangerouslySetInnerHTML={{ __html: body }} /> : <p className="text-content-secondary">Loading post...</p>}
          </div>
        )}
      </article>
    </MarketingShell>
  );
}

