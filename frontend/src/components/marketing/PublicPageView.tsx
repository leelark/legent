'use client';

import { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { MarketingShell } from '@/components/marketing/MarketingShell';
import { getPublicPageContent, getPublicPricingContent, type PublicContentRecord } from '@/lib/public-content-api';
import { Button } from '@/components/ui/Button';
import Link from 'next/link';

type PageViewProps = {
  pageKey: string;
  titleFallback: string;
};

const rise = {
  hidden: { opacity: 0, y: 24 },
  visible: { opacity: 1, y: 0 },
};

const defaultModules = [
  'Audience Studio',
  'Template Studio',
  'Campaign Studio',
  'Automation Studio',
  'Delivery Studio',
  'Analytics Studio',
];

export function PublicPageView({ pageKey, titleFallback }: PageViewProps) {
  const [record, setRecord] = useState<PublicContentRecord | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    const load = async () => {
      setLoading(true);
      try {
        const next = pageKey === 'pricing'
          ? await getPublicPricingContent()
          : await getPublicPageContent(pageKey);
        if (active) {
          setRecord(next);
        }
      } catch {
        if (active) {
          setRecord(null);
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };
    load();
    return () => {
      active = false;
    };
  }, [pageKey]);

  const payload = useMemo(() => (record?.payload ?? {}) as Record<string, unknown>, [record?.payload]);
  const heroTitle = String(payload.heroTitle ?? titleFallback);
  const heroSubtitle = String(
    payload.heroSubtitle ??
      'Revenue teams design, orchestrate, deliver and optimize every customer message from one premium control plane.'
  );
  const modules = Array.isArray(payload.modules) ? payload.modules.map(String) : defaultModules;

  return (
    <MarketingShell>
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 -z-10 bg-[radial-gradient(circle_at_10%_20%,rgba(79,70,229,0.15),transparent_50%),radial-gradient(circle_at_90%_0%,rgba(34,197,94,0.12),transparent_45%)]" />
        <div className="mx-auto max-w-7xl px-6 pb-16 pt-20 md:pb-24 md:pt-28">
          <motion.div
            initial="hidden"
            animate="visible"
            variants={rise}
            transition={{ duration: 0.45, ease: 'easeOut' }}
            className="max-w-3xl"
          >
            <p className="mb-4 inline-flex rounded-full border border-brand-200/70 bg-brand-50 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-brand-700 dark:border-brand-800/70 dark:bg-brand-900/20 dark:text-brand-300">
              Frontend Studio
            </p>
            <h1 className="text-balance text-4xl font-semibold leading-tight tracking-tight md:text-6xl">{heroTitle}</h1>
            <p className="mt-6 max-w-2xl text-lg leading-relaxed text-content-secondary">{heroSubtitle}</p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link href="/signup">
                <Button size="lg">Start Free</Button>
              </Link>
              <Link href="/login">
                <Button variant="secondary" size="lg">
                  Enter Workspace
                </Button>
              </Link>
            </div>
          </motion.div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-6 py-14 md:py-20">
        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.2 }}
          variants={rise}
          transition={{ duration: 0.45, ease: 'easeOut' }}
        >
          <h2 className="text-2xl font-semibold tracking-tight md:text-3xl">Platform Modules</h2>
          <p className="mt-2 text-content-secondary">Connected workflows, unified data model, one consistent experience.</p>
        </motion.div>
        <div className="mt-8 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {modules.map((moduleName, index) => (
            <motion.div
              key={moduleName}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.3, delay: index * 0.05 }}
              className="rounded-2xl border border-border-default bg-surface-elevated p-5 shadow-soft"
            >
              <h3 className="text-base font-semibold">{moduleName}</h3>
              <p className="mt-2 text-sm text-content-secondary">
                Operational depth with premium UX, real-time feedback loops, and production-grade controls.
              </p>
            </motion.div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-6 pb-20">
        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true }}
          variants={rise}
          transition={{ duration: 0.4 }}
          className="rounded-3xl border border-border-default bg-gradient-to-br from-brand-600 to-brand-800 p-8 text-white shadow-2xl md:p-12"
        >
          <h2 className="text-2xl font-semibold md:text-4xl">From first impression to last conversion.</h2>
          <p className="mt-3 max-w-2xl text-brand-100">
            Replace disconnected tools with one orchestrated customer engagement stack designed for speed, quality and control.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Link href="/signup">
              <Button variant="secondary" size="lg">
                Launch Workspace
              </Button>
            </Link>
            <Link href="/features">
              <Button variant="ghost" size="lg" className="text-white hover:bg-white/10">
                Explore Features
              </Button>
            </Link>
          </div>
        </motion.div>
      </section>
      {loading ? <div className="pb-6 text-center text-sm text-content-secondary">Loading content...</div> : null}
    </MarketingShell>
  );
}
