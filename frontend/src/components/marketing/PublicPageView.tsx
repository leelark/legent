'use client';

import { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import Link from 'next/link';
import { ArrowRight, CheckCircle2, Layers, MailCheck, ShieldCheck, Zap } from 'lucide-react';
import { MarketingShell } from '@/components/marketing/MarketingShell';
import { getPublicPageContent, getPublicPricingContent, type PublicContentRecord } from '@/lib/public-content-api';
import { Button } from '@/components/ui/Button';

type PageViewProps = {
  pageKey: string;
  titleFallback: string;
};

const rise = {
  hidden: { opacity: 0, y: 18 },
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

const proof = [
  { label: 'Campaign build time', value: '-42%' },
  { label: 'Workflow handoffs', value: '1 hub' },
  { label: 'Delivery visibility', value: 'Live' },
];

export function PublicPageView({ pageKey, titleFallback }: PageViewProps) {
  const [record, setRecord] = useState<PublicContentRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeModule, setActiveModule] = useState(0);

  useEffect(() => {
    let active = true;
    const load = async () => {
      setLoading(true);
      try {
        const next = pageKey === 'pricing' ? await getPublicPricingContent() : await getPublicPageContent(pageKey);
        if (active) setRecord(next);
      } catch {
        if (active) setRecord(null);
      } finally {
        if (active) setLoading(false);
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
      'A command center for audience data, templates, campaigns, journeys, delivery, and analytics. Built for teams that need production control without losing speed.'
  );
  const modules = Array.isArray(payload.modules) ? payload.modules.map(String) : defaultModules;

  return (
    <MarketingShell>
      <section className="relative overflow-hidden border-b border-border-default bg-surface-primary">
        <div className="premium-grid absolute inset-0 opacity-50" />
        <div className="relative mx-auto grid max-w-7xl gap-10 px-4 pb-12 pt-12 sm:px-6 md:min-h-[720px] md:grid-cols-[0.92fr_1.08fr] md:items-center md:pb-14 md:pt-10">
          <motion.div initial="hidden" animate="visible" variants={rise} transition={{ duration: 0.45, ease: 'easeOut' }}>
            <p className="mb-4 inline-flex rounded-lg border border-brand-500/30 bg-brand-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-brand-300">
              Enterprise Email Operations
            </p>
            <h1 className="text-balance text-4xl font-semibold leading-tight tracking-normal sm:text-5xl lg:text-6xl">{heroTitle}</h1>
            <p className="mt-5 max-w-2xl text-base leading-relaxed text-content-secondary sm:text-lg">{heroSubtitle}</p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Link href="/signup"><Button size="lg" icon={<ArrowRight size={18} />}>Start Free</Button></Link>
              <Link href="/login"><Button variant="secondary" size="lg">Enter Workspace</Button></Link>
            </div>
            <div className="mt-8 grid max-w-xl grid-cols-3 gap-3">
              {proof.map((item) => (
                <div key={item.label} className="rounded-lg border border-border-default bg-surface-elevated p-3">
                  <p className="text-lg font-semibold text-content-primary">{item.value}</p>
                  <p className="mt-1 text-xs text-content-secondary">{item.label}</p>
                </div>
              ))}
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, scale: 0.98, y: 16 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            transition={{ duration: 0.55, ease: 'easeOut' }}
            className="relative"
          >
            <ProductSurface modules={modules} activeModule={activeModule} setActiveModule={setActiveModule} />
          </motion.div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-14 sm:px-6 md:py-20">
        <motion.div initial="hidden" whileInView="visible" viewport={{ once: true, amount: 0.2 }} variants={rise} transition={{ duration: 0.4 }}>
          <h2 className="text-2xl font-semibold tracking-normal md:text-3xl">One system from audience to inbox.</h2>
          <p className="mt-2 max-w-2xl text-content-secondary">Each module shares context, permissions, telemetry, and workspace state, so teams stop reconciling disconnected tools.</p>
        </motion.div>
        <div className="mt-8 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {modules.map((moduleName, index) => (
            <motion.button
              key={moduleName}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.3, delay: index * 0.04 }}
              onMouseEnter={() => setActiveModule(index)}
              onFocus={() => setActiveModule(index)}
              className="rounded-lg border border-border-default bg-surface-elevated p-5 text-left shadow-soft transition hover:-translate-y-0.5 hover:border-brand-400/60 hover:shadow-elevated"
            >
              <div className="flex items-center gap-3">
                <span className="rounded-lg bg-brand-500/10 p-2 text-brand-300"><Layers size={18} /></span>
                <h3 className="text-base font-semibold">{moduleName}</h3>
              </div>
              <p className="mt-3 text-sm leading-6 text-content-secondary">
                Governed CRUD, validated forms, live feedback, and API-aligned actions inside one responsive module surface.
              </p>
            </motion.button>
          ))}
        </div>
      </section>

      <section className="border-y border-border-default bg-surface-secondary/70">
        <div className="mx-auto grid max-w-7xl gap-8 px-4 py-14 sm:px-6 lg:grid-cols-3">
          {[
            { icon: Zap, title: 'Fast orchestration', body: 'Launch campaigns, pause workflows, replay delivery, and validate templates without context switching.' },
            { icon: ShieldCheck, title: 'Governed execution', body: 'Tenant, workspace, role, approval, and audit controls align with backend contracts.' },
            { icon: MailCheck, title: 'Inbox-aware delivery', body: 'Queue health, provider readiness, authentication status, and replay diagnostics stay visible.' },
          ].map((item) => (
            <div key={item.title} className="rounded-lg border border-border-default bg-surface-elevated p-5">
              <item.icon className="text-brand-300" size={22} />
              <h3 className="mt-4 text-base font-semibold">{item.title}</h3>
              <p className="mt-2 text-sm leading-6 text-content-secondary">{item.body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-14 sm:px-6 md:py-20">
        <div className="grid gap-6 rounded-lg border border-border-default bg-surface-elevated p-6 md:grid-cols-[1fr_auto] md:items-center md:p-8">
          <div>
            <h2 className="text-2xl font-semibold md:text-3xl">Ready for a controlled launch path.</h2>
            <p className="mt-3 max-w-2xl text-content-secondary">
              Move from campaign idea to approved send with shared data, real-time diagnostics, and theme-consistent tools.
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Link href="/signup"><Button size="lg">Create Workspace</Button></Link>
            <Link href="/features"><Button variant="secondary" size="lg">Explore Features</Button></Link>
          </div>
        </div>
        {loading ? <div className="mt-4 text-center text-sm text-content-secondary">Loading content...</div> : null}
      </section>
    </MarketingShell>
  );
}

function ProductSurface({
  modules,
  activeModule,
  setActiveModule,
}: {
  modules: string[];
  activeModule: number;
  setActiveModule: (index: number) => void;
}) {
  return (
    <div className="overflow-hidden rounded-lg border border-border-strong bg-surface-elevated shadow-2xl">
      <div className="flex items-center justify-between border-b border-border-default px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="h-2.5 w-2.5 rounded-full bg-danger" />
          <span className="h-2.5 w-2.5 rounded-full bg-warning" />
          <span className="h-2.5 w-2.5 rounded-full bg-success" />
        </div>
        <div className="rounded-lg border border-border-default bg-surface-secondary px-3 py-1 text-xs text-content-secondary">
          Live workspace
        </div>
      </div>
      <div className="grid min-h-[420px] md:grid-cols-[190px_1fr]">
        <div className="border-b border-border-default bg-surface-primary p-3 md:border-b-0 md:border-r">
          <div className="space-y-1">
            {modules.slice(0, 6).map((item, index) => (
              <button
                key={item}
                onClick={() => setActiveModule(index)}
                className={`w-full rounded-lg px-3 py-2 text-left text-xs font-medium transition ${
                  activeModule === index ? 'bg-brand-500/15 text-brand-300' : 'text-content-secondary hover:bg-surface-secondary'
                }`}
              >
                {item.replace(' Studio', '')}
              </button>
            ))}
          </div>
        </div>
        <div className="p-4">
          <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-xs uppercase tracking-wider text-content-muted">Selected module</p>
              <h3 className="mt-1 text-xl font-semibold">{modules[activeModule] ?? modules[0]}</h3>
            </div>
            <div className="inline-flex w-fit items-center gap-2 rounded-lg border border-brand-500/30 bg-brand-500/10 px-3 py-2 text-xs font-semibold text-brand-300">
              <CheckCircle2 size={15} /> API synced
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            {['Audience', 'Delivery', 'Revenue'].map((label, index) => (
              <div key={label} className="rounded-lg border border-border-default bg-surface-secondary p-3">
                <p className="text-xs text-content-secondary">{label}</p>
                <p className="mt-2 text-2xl font-semibold">{[128420, 99.4, 18.7][index]}{index === 1 ? '%' : index === 2 ? 'k' : ''}</p>
              </div>
            ))}
          </div>
          <div className="mt-4 rounded-lg border border-border-default bg-surface-primary p-4">
            <div className="flex h-40 items-end gap-2">
              {[36, 54, 42, 66, 72, 58, 84, 76, 92, 88].map((height, index) => (
                <motion.div
                  key={index}
                  initial={{ height: 8 }}
                  animate={{ height }}
                  transition={{ duration: 0.45, delay: index * 0.03 }}
                  className="flex-1 rounded-t bg-gradient-to-t from-brand-700 to-brand-300"
                />
              ))}
            </div>
          </div>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-border-default bg-surface-secondary p-3">
              <p className="text-sm font-semibold">Campaign approval</p>
              <p className="mt-1 text-xs text-content-secondary">Review queue clean, next launch ready.</p>
            </div>
            <div className="rounded-lg border border-border-default bg-surface-secondary p-3">
              <p className="text-sm font-semibold">Deliverability guard</p>
              <p className="mt-1 text-xs text-content-secondary">Replay depth normal, providers healthy.</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
