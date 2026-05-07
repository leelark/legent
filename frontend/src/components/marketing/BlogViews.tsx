'use client';

import Link from 'next/link';
import { motion } from 'framer-motion';
import { ArrowLeft, ArrowRight, BookOpen, CalendarDays, Sparkles } from 'lucide-react';
import { MarketingShell } from '@/components/marketing/MarketingShell';
import { blogCategories, blogPosts } from '@/lib/marketing-data';

export function BlogIndexView() {
  const [featured, ...remaining] = blogPosts;

  return (
    <MarketingShell>
      <section className="mx-auto grid max-w-7xl gap-8 px-4 pb-12 pt-14 sm:px-6 md:py-20 lg:grid-cols-2 lg:items-end">
        <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }}>
          <p className="text-xs font-semibold uppercase tracking-[0.22em] text-[var(--public-accent)]">Field notes</p>
          <h1 className="public-heading mt-4 text-balance text-4xl font-semibold leading-tight md:text-6xl">Operating essays for teams building serious email systems.</h1>
          <p className="public-muted mt-5 max-w-2xl text-lg leading-8">Playbooks for audience strategy, delivery control, automation governance, workflow design, and premium SaaS operations.</p>
          <div className="mt-6 flex flex-wrap gap-2">
            {blogCategories.map((category) => (
              <span key={category} className="public-border rounded-full border bg-[var(--public-panel)] px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">
                {category}
              </span>
            ))}
          </div>
        </motion.div>
        <Link href={`/blog/${featured.slug}`} className="public-panel group rounded-[1.6rem] p-6 transition hover:-translate-y-2 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]">
          <span className="inline-flex items-center gap-2 rounded-full bg-fuchsia-300/12 px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">
            <BookOpen size={14} /> Featured
          </span>
          <h2 className="public-heading mt-5 text-3xl font-semibold">{featured.title}</h2>
          <p className="public-muted mt-3 leading-7">{featured.summary}</p>
          <span className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-[var(--public-accent)]">
            Read feature <ArrowRight size={15} className="transition group-hover:translate-x-1" />
          </span>
        </Link>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-16 sm:px-6">
        <div className="grid gap-5 lg:grid-cols-3">
          {remaining.map((post, index) => (
            <BlogCard key={post.slug} post={post} index={index} />
          ))}
        </div>
      </section>

      <section className="public-border border-y bg-[var(--public-panel)]">
        <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6">
          <p className="text-xs font-semibold uppercase tracking-[0.22em] text-[var(--public-accent)]">Library</p>
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

  return (
    <MarketingShell>
      <article className="mx-auto max-w-4xl px-4 py-14 sm:px-6 md:py-20">
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
                dangerouslySetInnerHTML={{ __html: post.body }}
              />
            </div>
          </>
        )}
      </article>
    </MarketingShell>
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
