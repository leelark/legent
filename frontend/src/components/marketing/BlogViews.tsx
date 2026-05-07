'use client';

import Link from 'next/link';
import { ArrowLeft, ArrowRight, BookOpen, CalendarDays } from 'lucide-react';
import { MarketingShell } from '@/components/marketing/MarketingShell';
import { MarketingSection } from '@/components/marketing/PublicPageView';
import { blogCategories, blogPosts } from '@/lib/marketing-data';
import { Button } from '@/components/ui/Button';

export function BlogIndexView() {
  const [featured, ...remaining] = blogPosts;

  return (
    <MarketingShell>
      <section className="mx-auto grid max-w-7xl gap-8 px-4 pb-10 pt-14 sm:px-6 md:pb-16 md:pt-20 lg:grid-cols-[0.82fr_1.18fr] lg:items-end">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.22em] text-[var(--public-accent)]">Blog</p>
          <h1 className="public-heading mt-4 text-balance text-4xl font-semibold leading-tight md:text-5xl">Field notes for premium email operators.</h1>
          <p className="public-muted mt-5 text-lg leading-8">Playbooks for audience strategy, delivery control, workflow design, and modern SaaS operations.</p>
          <div className="mt-6 flex flex-wrap gap-2">
            {blogCategories.map((category) => (
              <span key={category} className="rounded-full border public-border bg-[var(--public-panel)] px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">
                {category}
              </span>
            ))}
          </div>
        </div>
        <Link href={`/blog/${featured.slug}`} className="public-panel group rounded-[1.35rem] p-6 transition hover:-translate-y-1 hover:border-fuchsia-300/35">
          <span className="inline-flex items-center gap-2 rounded-full bg-fuchsia-300/12 px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">
            <BookOpen size={14} />
            Featured
          </span>
          <h2 className="public-heading mt-5 text-2xl font-semibold">{featured.title}</h2>
          <p className="public-muted mt-3 leading-7">{featured.summary}</p>
          <span className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-[var(--public-accent)]">
            Read feature <ArrowRight size={15} className="transition group-hover:translate-x-1" />
          </span>
        </Link>
      </section>
      <MarketingSection eyebrow="Latest" title="Practical ideas for better message operations.">
        <div className="grid gap-4 lg:grid-cols-3">
          {remaining.map((post) => (
            <BlogCard key={post.slug} post={post} />
          ))}
        </div>
      </MarketingSection>
      <MarketingSection eyebrow="Library" title="All public field notes.">
        <div className="grid gap-4 lg:grid-cols-3">
          {blogPosts.map((post) => (
            <BlogCard key={post.slug} post={post} />
          ))}
        </div>
      </MarketingSection>
    </MarketingShell>
  );
}

export function BlogPostView({ slug }: { slug: string }) {
  const post = blogPosts.find((item) => item.slug === slug);

  return (
    <MarketingShell>
      <article className="mx-auto max-w-4xl px-4 py-14 sm:px-6 md:py-20">
        <Link href="/blog" className="public-muted inline-flex items-center gap-2 text-sm font-medium hover:text-[var(--public-text)]">
          <ArrowLeft size={16} />
          Back to blog
        </Link>
        {!post ? (
          <div className="public-panel mt-10 rounded-[1.4rem] p-8">
            <h1 className="public-heading text-3xl font-semibold">Article not found</h1>
            <p className="public-muted mt-3">This post is not published yet.</p>
            <Link href="/blog" className="mt-6 inline-block"><Button className="public-button-primary">View Articles</Button></Link>
          </div>
        ) : (
          <>
            <div className="public-muted mt-8 flex flex-wrap items-center gap-3 text-sm">
              <span className="rounded-full bg-fuchsia-300/12 px-3 py-1 text-[var(--public-accent)]">{post.category}</span>
              <span className="inline-flex items-center gap-1"><CalendarDays size={15} /> {post.readTime}</span>
            </div>
            <h1 className="public-heading mt-5 text-balance text-4xl font-semibold leading-tight md:text-5xl">{post.title}</h1>
            <p className="public-muted mt-5 text-lg leading-8">{post.summary}</p>
            <div
              className="public-panel prose mt-10 max-w-none rounded-[1.4rem] p-6 prose-headings:text-[var(--public-text)] prose-p:text-[var(--public-muted)] md:p-8"
              dangerouslySetInnerHTML={{ __html: post.body }}
            />
          </>
        )}
      </article>
    </MarketingShell>
  );
}

function BlogCard({ post }: { post: (typeof blogPosts)[number] }) {
  return (
    <Link
      href={`/blog/${post.slug}`}
      className="public-panel group flex h-full flex-col rounded-[1.2rem] p-5 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)] backdrop-blur-xl transition hover:-translate-y-1 hover:border-fuchsia-300/35"
    >
      <div className="flex items-center justify-between gap-3">
        <span className="inline-flex items-center gap-2 rounded-full bg-fuchsia-300/12 px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">
          <BookOpen size={14} />
          {post.category}
        </span>
        <span className="public-muted text-xs">{post.readTime}</span>
      </div>
      <h2 className="public-heading mt-5 text-xl font-semibold">{post.title}</h2>
      <p className="public-muted mt-3 flex-1 text-sm leading-6">{post.summary}</p>
      <span className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-[var(--public-accent)]">
        Read article <ArrowRight size={15} className="transition group-hover:translate-x-1" />
      </span>
    </Link>
  );
}
