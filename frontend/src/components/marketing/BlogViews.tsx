'use client';

import Link from 'next/link';
import { useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, ArrowRight, BookOpen, CalendarDays, Network, RadioTower, Sparkles } from 'lucide-react';
import { MarketingShell } from '@/components/marketing/MarketingShell';
import { blogCategories, blogPosts } from '@/lib/marketing-data';
import { sanitizeRichContentHtml } from '@/lib/sanitize-html';

export function BlogIndexView() {
  const [featured, ...remaining] = blogPosts;
  const [category, setCategory] = useState<'All' | (typeof blogCategories)[number]>('All');
  const filtered = category === 'All' ? remaining : blogPosts.filter((post) => post.category === category && post.slug !== featured.slug);

  return (
    <MarketingShell>
      <section className="public-hero-shell public-hero-grid mx-auto max-w-7xl px-4 sm:px-6 lg:grid-cols-[0.96fr_1.04fr]">
        <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }}>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--public-accent)]">Field notes</p>
          <h1 className="public-heading mt-4 text-balance text-4xl font-semibold leading-tight md:text-6xl">Operating essays for teams building serious email systems.</h1>
          <p className="public-muted mt-5 max-w-2xl text-lg leading-8">Playbooks for audience strategy, delivery control, automation governance, workflow design, and premium SaaS operations.</p>
          <div className="mt-6 flex flex-wrap gap-2" aria-label="Filter articles by category">
            {(['All', ...blogCategories] as const).map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => setCategory(item)}
                className={`public-border rounded-full border px-3 py-1 text-xs font-semibold transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${category === item ? 'bg-[var(--public-text)] text-[var(--public-bg)]' : 'bg-[var(--public-panel)] text-[var(--public-accent)]'}`}
              >
                {item}
              </button>
            ))}
          </div>
        </motion.div>
        <BlogHeroVisual post={featured} />
      </section>

      <section className="mx-auto max-w-7xl px-4 py-12 sm:px-6 md:py-16">
        <div className="grid gap-5 lg:grid-cols-3">
          {filtered.map((post, index) => (
            <BlogCard key={post.slug} post={post} index={index} />
          ))}
        </div>
      </section>

      <section className="public-bleed-band">
        <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 md:py-16">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--public-accent)]">Library</p>
          <h2 className="public-heading mt-4 max-w-3xl text-3xl font-semibold md:text-5xl">Browse all public field notes.</h2>
          <div className="mt-9 grid gap-4 lg:grid-cols-4">
            {blogPosts.map((post, index) => <BlogCard key={post.slug} post={post} index={index} compact />)}
          </div>
        </div>
      </section>
    </MarketingShell>
  );
}

export function BlogPostView({ slug }: { slug: string }) {
  const post = blogPosts.find((item) => item.slug === slug);
  const related = blogPosts.filter((item) => item.slug !== slug).slice(0, 3);

  return (
    <MarketingShell>
      <article className="mx-auto max-w-4xl px-4 py-10 sm:px-6 md:py-16">
        <Link href="/blog" className="public-muted inline-flex items-center gap-2 rounded-xl text-sm font-medium hover:text-[var(--public-text)] focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]">
          <ArrowLeft size={16} /> Back to blog
        </Link>
        {!post ? (
          <div className="public-panel mt-10 rounded-[1.5rem] p-8">
            <h1 className="public-heading text-3xl font-semibold">Article not found</h1>
            <p className="public-muted mt-3">This post is not published yet.</p>
          </div>
        ) : (
          <>
            <div className="public-muted mt-8 flex flex-wrap items-center gap-3 text-sm">
              <span className="rounded-full bg-fuchsia-300/12 px-3 py-1 text-[var(--public-accent)]">{post.category}</span>
              <span className="inline-flex items-center gap-1"><CalendarDays size={15} /> {post.readTime}</span>
            </div>
            <h1 className="public-heading mt-5 text-balance text-4xl font-semibold leading-tight md:text-6xl">{post.title}</h1>
            <p className="public-muted mt-5 text-lg leading-8">{post.summary}</p>
            <div className="public-panel mt-10 rounded-[1.6rem] p-6 md:p-8">
              <div className="mb-6 inline-flex items-center gap-2 rounded-full bg-fuchsia-300/12 px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">
                <Sparkles size={14} /> Operator note
              </div>
              <div
                className="prose max-w-none prose-headings:text-[var(--public-text)] prose-p:text-[var(--public-muted)]"
                dangerouslySetInnerHTML={{ __html: sanitizeRichContentHtml(post.body) }}
              />
            </div>
            <div className="mt-10">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--public-accent)]">Continue reading</p>
              <div className="mt-5 grid gap-4 md:grid-cols-3">
                {related.map((item, index) => <BlogCard key={item.slug} post={item} index={index} compact />)}
              </div>
            </div>
          </>
        )}
      </article>
    </MarketingShell>
  );
}

function BlogHeroVisual({ post }: { post: (typeof blogPosts)[number] }) {
  const signals = ['Audience model', 'Inbox route', 'Approval state', 'Revenue loop'];
  return (
    <Link href={`/blog/${post.slug}`} className="public-panel public-hero-visual public-art-glow group relative rounded-[1.6rem] p-6 transition hover:-translate-y-2 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]">
      <div className="public-visual-clip"><div className="public-mock-bitmap" aria-hidden="true" /></div>
      <div className="relative">
        <span className="inline-flex items-center gap-2 rounded-full bg-fuchsia-300/12 px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">
          <BookOpen size={14} /> Featured playbook
        </span>
        <h2 className="public-heading mt-5 text-3xl font-semibold">{post.title}</h2>
        <p className="public-muted mt-3 leading-7">{post.summary}</p>
        <div className="mt-7 grid gap-3 sm:grid-cols-2">
          {signals.map((signal, index) => (
            <motion.div key={signal} animate={{ y: [0, index % 2 ? 8 : -8, 0] }} transition={{ duration: 5 + index * 0.2, repeat: Infinity }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
              {index % 2 ? <RadioTower className="text-[var(--public-accent)]" size={18} /> : <Network className="text-[var(--public-accent)]" size={18} />}
              <p className="public-heading mt-3 text-sm font-semibold">{signal}</p>
            </motion.div>
          ))}
        </div>
        <span className="mt-7 inline-flex items-center gap-2 text-sm font-semibold text-[var(--public-accent)]">
          Read feature <ArrowRight size={15} className="transition group-hover:translate-x-1" />
        </span>
      </div>
    </Link>
  );
}

function BlogCard({ post, index, compact = false }: { post: (typeof blogPosts)[number]; index: number; compact?: boolean }) {
  return (
    <motion.div initial={{ opacity: 0, y: 18 }} whileInView={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.05 }} viewport={{ once: true }}>
      <Link href={`/blog/${post.slug}`} className="public-panel group flex h-full flex-col rounded-[1.35rem] p-5 transition hover:-translate-y-2 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]">
        <div className="flex items-center justify-between gap-3">
          <span className="inline-flex items-center gap-2 rounded-full bg-fuchsia-300/12 px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">
            <BookOpen size={14} /> {post.category}
          </span>
          <span className="public-muted text-xs">{post.readTime}</span>
        </div>
        <h2 className={`public-heading mt-5 font-semibold ${compact ? 'text-lg' : 'text-2xl'}`}>{post.title}</h2>
        <p className="public-muted mt-3 flex-1 text-sm leading-6">{post.summary}</p>
        <span className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-[var(--public-accent)]">
          Read article <ArrowRight size={15} className="transition group-hover:translate-x-1" />
        </span>
      </Link>
    </motion.div>
  );
}
