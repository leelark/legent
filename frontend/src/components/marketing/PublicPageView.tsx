'use client';

import { useState, type FormEvent } from 'react';
import { motion } from 'framer-motion';
import Link from 'next/link';
import {
  ArrowRight,
  BarChart3,
  CheckCircle2,
  CircleDot,
  Clock3,
  Database,
  GitBranch,
  MailCheck,
  MousePointerClick,
  RadioTower,
  ShieldCheck,
  Sparkles,
  Target,
  Users,
  Wand2,
  Zap,
} from 'lucide-react';
import { MarketingShell } from '@/components/marketing/MarketingShell';
import { Button } from '@/components/ui/Button';
import { post } from '@/lib/api-client';
import { marketingPages, proofCards, studios, type MarketingPageKey } from '@/lib/marketing-data';

type PageViewProps = {
  pageKey: string;
  titleFallback: string;
};

const rise = {
  hidden: { opacity: 0, y: 22 },
  visible: { opacity: 1, y: 0 },
};

export function PublicPageView({ pageKey, titleFallback }: PageViewProps) {
  const key = normalizePageKey(pageKey);
  const page = marketingPages[key];

  return (
    <MarketingShell>
      <MarketingHero
        pageKey={key}
        eyebrow={page.eyebrow}
        title={page.title ?? titleFallback}
        subtitle={page.subtitle}
        primaryCta={page.primaryCta}
        secondaryCta={page.secondaryCta}
        secondaryHref={key === 'home' ? '/modules' : key === 'pricing' ? '/contact' : '/features'}
        highlights={'highlights' in page ? [...page.highlights] : []}
      />

      {key === 'home' && <HomeSections />}
      {key === 'features' && <FeatureSections />}
      {key === 'modules' && <ModuleSections />}
      {key === 'pricing' && <PricingSections />}
      {key === 'about' && <AboutSections />}
      {key === 'contact' && <ContactSections />}
    </MarketingShell>
  );
}

function MarketingHero({
  eyebrow,
  title,
  subtitle,
  primaryCta,
  secondaryCta,
  secondaryHref,
  highlights,
  pageKey,
}: {
  eyebrow: string;
  title: string;
  subtitle: string;
  primaryCta: string;
  secondaryCta: string;
  secondaryHref: string;
  highlights: string[];
  pageKey: MarketingPageKey;
}) {
  return (
    <section className="relative overflow-hidden">
      <div className="mx-auto grid max-w-7xl gap-10 px-4 pb-14 pt-10 sm:px-6 lg:min-h-[660px] lg:grid-cols-[0.94fr_1.06fr] lg:items-center lg:pb-20">
        <motion.div initial="hidden" animate="visible" variants={rise} transition={{ duration: 0.5, ease: 'easeOut' }}>
          <div className="public-border mb-5 inline-flex items-center gap-2 rounded-full border bg-[var(--public-panel)] px-3 py-1 text-xs font-semibold uppercase text-[var(--public-text)] shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]">
            <Sparkles size={14} className="text-[var(--public-accent)]" />
            {eyebrow}
          </div>
          <h1 className="public-heading max-w-4xl text-balance text-4xl font-semibold leading-[1.05] tracking-normal sm:text-5xl lg:text-6xl">
            {title}
          </h1>
          <p className="public-muted mt-5 max-w-2xl text-base leading-8 sm:text-lg">{subtitle}</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link href="/signup">
              <Button size="lg" className="public-button-primary" icon={<ArrowRight size={18} />}>
                {primaryCta}
              </Button>
            </Link>
            <Link href={secondaryHref}>
              <Button variant="secondary" size="lg" className="public-button-secondary">
                {secondaryCta}
              </Button>
            </Link>
          </div>
          <div className="mt-8 grid max-w-2xl gap-2 sm:grid-cols-3">
            {highlights.map((item) => (
              <div key={item} className="public-panel rounded-2xl px-4 py-3 text-sm backdrop-blur">
                <CheckCircle2 className="mb-2 text-[var(--public-accent)]" size={16} />
                {item}
              </div>
            ))}
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, scale: 0.97, y: 24 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          transition={{ duration: 0.62, ease: 'easeOut' }}
          className="relative"
        >
          <HeroVisual variant={pageKey} />
        </motion.div>
      </div>
    </section>
  );
}

function HomeSections() {
  const page = marketingPages.home;

  return (
    <>
      <MarketingSection eyebrow="Proof" title="Designed for high-confidence execution.">
        <div className="grid gap-4 md:grid-cols-3">
          {page.metrics.map((metric) => (
            <PublicBentoCard key={metric.label}>
              <p className="public-heading text-3xl font-semibold sm:text-4xl">{metric.value}</p>
              <p className="public-muted mt-2 text-sm">{metric.label}</p>
            </PublicBentoCard>
          ))}
        </div>
      </MarketingSection>

      <PublicFeatureBand
        eyebrow="Signal to send"
        title="One controlled path from customer signal to inbox result."
        body="Legent turns audience readiness, template fit, approval state, delivery safety, and analytics feedback into one visible operating loop."
        visual={<SignalFlow steps={[...page.signalFlow]} />}
      />

      <SolutionLayers />
      <MarketingSection eyebrow="Live surfaces" title="Campaign views shift together as operators prepare a launch.">
        <LiveSurfaceShowcase />
      </MarketingSection>
      <FinalCta title="Build a workspace that feels as controlled as it looks." body="Start with the public experience, then move directly into a polished operator cockpit." />
    </>
  );
}

function FeatureSections() {
  const page = marketingPages.features;

  return (
    <>
      <MarketingSection eyebrow="Capabilities" title="Built for practical operators, not demo-only dashboards.">
        <div className="grid auto-rows-fr gap-4 md:grid-cols-2 xl:grid-cols-3">
          {page.features.map((feature, index) => {
            const Icon = feature.icon;
            return (
              <motion.div key={feature.title} variants={rise} initial="hidden" whileInView="visible" viewport={{ once: true }} transition={{ delay: index * 0.04 }}>
                <PublicBentoCard className={index === 0 || index === 3 ? 'md:row-span-2' : ''}>
                  <Icon className="text-[var(--public-accent)]" size={24} />
                  <h3 className="public-heading mt-5 text-lg font-semibold">{feature.title}</h3>
                  <p className="public-muted mt-3 text-sm leading-6">{feature.body}</p>
                </PublicBentoCard>
              </motion.div>
            );
          })}
        </div>
      </MarketingSection>

      <PublicFeatureBand
        eyebrow="Governance"
        title="Security, configuration, and telemetry stay in the operator view."
        body="Feature depth matters only when teams can see ownership, risk, permissions, and runtime behavior before they act."
        visual={<FeatureSignalMap />}
      />

      <MarketingSection eyebrow="Operator workflows" title="The features connect into complete work patterns.">
        <div className="grid gap-4 lg:grid-cols-3">
          {page.operatorWorkflows.map((item, index) => (
            <PublicBentoCard key={item.title}>
              <p className="flex h-9 w-9 items-center justify-center rounded-full bg-fuchsia-300 text-sm font-semibold text-purple-950">{index + 1}</p>
              <h3 className="public-heading mt-5 text-lg font-semibold">{item.title}</h3>
              <p className="public-muted mt-3 text-sm leading-6">{item.body}</p>
            </PublicBentoCard>
          ))}
        </div>
      </MarketingSection>

      <FinalCta title="See how governed features become faster launches." body="Start with a workspace, then connect audience, campaign, delivery, and analytics controls." />
    </>
  );
}

function ModuleSections() {
  const page = marketingPages.modules;

  return (
    <>
      <MarketingSection eyebrow="Studios" title="Dedicated spaces with shared runtime context.">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {page.drilldowns.map((studio, index) => (
            <PublicBentoCard key={studio.title}>
              <div className="flex items-center justify-between gap-3">
                <h3 className="public-heading text-lg font-semibold">{studio.title}</h3>
                <span className="rounded-full bg-fuchsia-300/12 px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">{studio.proof}</span>
              </div>
              <p className="public-muted mt-4 text-sm leading-6">{studio.body}</p>
              <motion.div
                initial={{ width: 28 }}
                whileInView={{ width: `${48 + index * 7}%` }}
                viewport={{ once: true }}
                transition={{ duration: 0.7, delay: index * 0.04 }}
                className="mt-5 h-2 rounded-full bg-gradient-to-r from-violet-500 to-fuchsia-400"
              />
            </PublicBentoCard>
          ))}
        </div>
      </MarketingSection>

      <PublicFeatureBand
        eyebrow="Connected flow"
        title="How studios work together without disconnected handoffs."
        body="Each studio owns a deep workflow, but the system keeps tenant, workspace, permissions, config, activity, and analytics context shared."
        visual={<StudioRelay steps={[...page.flow]} />}
      />

      <FinalCta title="Open the workspace around your real operating model." body="Use dedicated studios without losing shared ownership, audit, or launch state." />
    </>
  );
}

function PricingSections() {
  const page = marketingPages.pricing;

  return (
    <>
      <MarketingSection eyebrow="Plans" title="Choose the operating model that matches your team.">
        <div className="grid gap-4 lg:grid-cols-3">
          {page.plans.map((plan) => {
            const featured = 'featured' in plan && plan.featured;
            return (
              <PublicBentoCard key={plan.name} className={featured ? 'border-fuchsia-300/40 bg-fuchsia-400/[0.09] shadow-[0_0_60px_rgba(217,70,239,0.18)]' : ''}>
                {featured && <p className="mb-4 inline-flex rounded-full bg-fuchsia-300 px-3 py-1 text-xs font-semibold text-purple-950">Most popular</p>}
                <h3 className="public-heading text-xl font-semibold">{plan.name}</h3>
                <p className="public-heading mt-3 text-3xl font-semibold sm:text-4xl">{plan.price}</p>
                <p className="public-muted mt-2 text-sm">{plan.desc}</p>
                <div className="mt-6 space-y-3">
                  {plan.features.map((feature) => (
                    <p key={feature} className="public-muted flex gap-2 text-sm">
                      <CheckCircle2 className="mt-0.5 shrink-0 text-[var(--public-accent)]" size={16} />
                      {feature}
                    </p>
                  ))}
                </div>
              </PublicBentoCard>
            );
          })}
        </div>
      </MarketingSection>

      <MarketingSection eyebrow="Compare" title="What changes as your program scales.">
        <PricingScaleVisual />
      </MarketingSection>

      <MarketingSection eyebrow="Plan details" title="Capability depth by workspace tier.">
        <PublicComparisonTable rows={[...page.comparison]} />
      </MarketingSection>

      <MarketingSection eyebrow="Add-ons" title="Expert help when the launch path needs more certainty.">
        <div className="grid gap-4 md:grid-cols-3">
          {page.addons.map((item) => (
            <PublicBentoCard key={item.title}>
              <Sparkles size={22} className="text-[var(--public-accent)]" />
              <h3 className="public-heading mt-4 font-semibold">{item.title}</h3>
              <p className="public-muted mt-2 text-sm leading-6">{item.body}</p>
            </PublicBentoCard>
          ))}
        </div>
      </MarketingSection>

      <MarketingSection eyebrow="FAQ" title="Clear answers before launch.">
        <div className="grid gap-4 md:grid-cols-3">
          {page.faqs.map((faq) => (
            <PublicBentoCard key={faq.q}>
              <h3 className="public-heading font-semibold">{faq.q}</h3>
              <p className="public-muted mt-3 text-sm leading-6">{faq.a}</p>
            </PublicBentoCard>
          ))}
        </div>
      </MarketingSection>
    </>
  );
}

function AboutSections() {
  const page = marketingPages.about;

  return (
    <>
      <PublicFeatureBand
        eyebrow="Mission"
        title="Email deserves production-grade operating discipline."
        body="Legent is shaped around a simple principle: teams should not need separate tools to understand data readiness, creative state, delivery health, and business results."
        visual={<OperatingTimeline items={[...page.timeline]} />}
      />

      <MarketingSection eyebrow="Principles" title="A product philosophy for serious email teams.">
        <div className="grid gap-4 md:grid-cols-3">
          {page.principles.map((item) => (
            <PublicBentoCard key={item.title}>
              <CircleDot className="text-[var(--public-accent)]" size={22} />
              <h3 className="public-heading mt-5 text-lg font-semibold">{item.title}</h3>
              <p className="public-muted mt-3 text-sm leading-6">{item.body}</p>
            </PublicBentoCard>
          ))}
        </div>
      </MarketingSection>

      <MarketingSection eyebrow="Quality bar" title="What product maturity means here.">
        <div className="grid gap-4 lg:grid-cols-3">
          {page.quality.map((item) => (
            <PublicBentoCard key={item.title}>
              <ShieldCheck size={22} className="text-[var(--public-accent)]" />
              <h3 className="public-heading mt-4 font-semibold">{item.title}</h3>
              <p className="public-muted mt-2 text-sm leading-6">{item.body}</p>
            </PublicBentoCard>
          ))}
        </div>
      </MarketingSection>
    </>
  );
}

function ContactSections() {
  const page = marketingPages.contact;

  return (
    <>
      <MarketingSection eyebrow="Solution intake" title="Tell us the launch path you need to make reliable.">
        <div className="grid gap-5 lg:grid-cols-[0.85fr_1.15fr]">
          <div className="grid gap-4">
            <ContactRouteVisual />
            {page.contactCards.map((card) => {
              const Icon = card.icon;
              return (
                <PublicBentoCard key={card.title}>
                  <Icon className="text-[var(--public-accent)]" size={22} />
                  <h3 className="public-heading mt-4 font-semibold">{card.title}</h3>
                  <p className="public-muted mt-2 text-sm leading-6">{card.body}</p>
                </PublicBentoCard>
              );
            })}
            <PublicBentoCard>
              <h3 className="public-heading font-semibold">Response expectations</h3>
              <div className="mt-4 grid gap-3">
                {page.expectations.map((item) => (
                  <div key={item.label} className="flex items-center justify-between rounded-xl bg-[var(--public-panel-strong)] px-3 py-2 text-sm">
                    <span className="public-muted">{item.label}</span>
                    <span className="public-heading font-semibold">{item.value}</span>
                  </div>
                ))}
              </div>
            </PublicBentoCard>
          </div>
          <PublicContactForm interests={[...page.formInterests]} />
        </div>
      </MarketingSection>
    </>
  );
}

export function MarketingSection({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="mx-auto max-w-7xl px-4 py-14 sm:px-6 md:py-20">
      <motion.div initial="hidden" whileInView="visible" viewport={{ once: true, amount: 0.2 }} variants={rise} transition={{ duration: 0.45 }}>
        <p className="text-xs font-semibold uppercase tracking-[0.22em] text-[var(--public-accent)]">{eyebrow}</p>
        <h2 className="public-heading mt-3 max-w-3xl text-balance text-2xl font-semibold tracking-normal md:text-4xl">{title}</h2>
      </motion.div>
      <div className="mt-8">{children}</div>
    </section>
  );
}

function PublicFeatureBand({
  eyebrow,
  title,
  body,
  visual,
}: {
  eyebrow: string;
  title: string;
  body: string;
  visual: React.ReactNode;
}) {
  return (
    <section className="public-border border-y bg-[var(--public-panel)]">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-14 sm:px-6 md:py-20 lg:grid-cols-[0.88fr_1.12fr] lg:items-center">
        <motion.div initial="hidden" whileInView="visible" viewport={{ once: true, amount: 0.25 }} variants={rise} transition={{ duration: 0.45 }}>
          <p className="text-xs font-semibold uppercase tracking-[0.22em] text-[var(--public-accent)]">{eyebrow}</p>
          <h2 className="public-heading mt-3 max-w-3xl text-balance text-2xl font-semibold tracking-normal md:text-4xl">{title}</h2>
          <p className="public-muted mt-4 max-w-2xl leading-7">{body}</p>
        </motion.div>
        {visual}
      </div>
    </section>
  );
}

function PublicBentoCard({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={`public-panel h-full rounded-[1.2rem] p-5 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)] backdrop-blur-xl transition hover:-translate-y-1 hover:border-fuchsia-300/35 ${className}`}>
      {children}
    </div>
  );
}

function HeroVisual({ variant }: { variant: MarketingPageKey }) {
  if (variant === 'home') return <HomeDashboardVisual />;
  if (variant === 'features') return <FeatureHeroVisual />;
  if (variant === 'modules') return <ModuleHeroVisual />;
  if (variant === 'pricing') return <PricingHeroVisual />;
  if (variant === 'about') return <AboutHeroVisual />;
  return <ContactHeroVisual />;
}

function WindowDots() {
  return (
    <div className="flex items-center gap-2" aria-hidden="true">
      <span className="h-3 w-3 rounded-full bg-[#ff5f57]" />
      <span className="h-3 w-3 rounded-full bg-[#ffbd2e]" />
      <span className="h-3 w-3 rounded-full bg-[#28c840]" />
    </div>
  );
}

function HomeDashboardVisual() {
  const visual = {
    active: 2,
    label: 'Campaign Studio',
    title: 'Spring launch orchestration',
    status: 'Approved',
    ready: '76% ready',
  };

  return (
    <div className="relative">
      <div className="absolute -inset-4 rounded-[2rem] bg-fuchsia-500/18 blur-3xl" />
      <motion.div
        animate={{ y: [0, -14, 0], rotate: [0, 1.5, 0] }}
        transition={{ duration: 7, repeat: Infinity, ease: 'easeInOut' }}
        className="public-panel absolute -right-4 top-12 z-20 hidden w-36 rounded-2xl p-3 shadow-2xl sm:block"
      >
        <p className="public-muted text-[10px] uppercase">Inbox safety</p>
        <p className="public-heading mt-1 text-lg font-semibold">99.2%</p>
      </motion.div>
      <motion.div
        animate={{ y: [0, 16, 0], x: [0, 8, 0] }}
        transition={{ duration: 8, repeat: Infinity, ease: 'easeInOut', delay: 0.4 }}
        className="public-panel absolute -left-5 bottom-16 z-20 hidden w-40 rounded-2xl p-3 shadow-2xl md:block"
      >
        <p className="public-muted text-[10px] uppercase">Audience fit</p>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-[var(--public-panel-strong)]">
          <motion.div animate={{ width: ['42%', '78%', '56%', '88%'] }} transition={{ duration: 6, repeat: Infinity }} className="h-full rounded-full bg-emerald-400" />
        </div>
      </motion.div>
      <div className="public-panel relative overflow-hidden rounded-[1.65rem] shadow-[0_24px_100px_rgba(55,18,105,0.22)] backdrop-blur-xl">
        <div className="public-border flex items-center justify-between border-b px-5 py-4">
          <WindowDots />
          <span className="rounded-full border border-fuchsia-300/25 bg-fuchsia-300/10 px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">Live workspace</span>
        </div>
        <div className="grid lg:min-h-[430px] lg:grid-cols-[190px_1fr]">
          <div className="public-border hidden border-b bg-[var(--public-panel-strong)] p-4 lg:block lg:border-b-0 lg:border-r">
            {studios.slice(0, 6).map((studio, index) => (
              <div key={studio.name} className={`mb-2 rounded-xl px-3 py-2 text-xs ${index === visual.active - 1 ? 'bg-fuchsia-300/15 text-[var(--public-text)]' : 'public-muted'}`}>
                {studio.name.replace(' Studio', '')}
              </div>
            ))}
          </div>
          <div className="p-5">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <p className="public-muted text-xs uppercase tracking-[0.2em]">{visual.label}</p>
                <h3 className="public-heading mt-1 text-xl font-semibold sm:text-2xl">{visual.title}</h3>
              </div>
              <span className="rounded-full bg-emerald-300/12 px-3 py-1 text-xs font-semibold text-emerald-500">{visual.status}</span>
            </div>
            <div className="mt-5 grid gap-3 sm:grid-cols-3">
              {proofCards.map((card) => {
                const Icon = card.icon;
                return (
                  <div key={card.label} className="public-panel rounded-2xl p-4">
                    <Icon size={17} className="text-[var(--public-accent)]" />
                    <p className="public-heading mt-3 text-2xl font-semibold">{card.value}</p>
                    <p className="public-muted mt-1 text-xs">{card.label}</p>
                  </div>
                );
              })}
            </div>
            <div className="public-border mt-5 rounded-2xl border bg-[var(--public-bg-soft)] p-4">
              <div className="mb-4 grid grid-cols-[1fr_auto] items-center gap-3">
                <div className="h-2 overflow-hidden rounded-full bg-[var(--public-panel)]">
                  <motion.div initial={{ width: '18%' }} animate={{ width: '76%' }} transition={{ duration: 1.1 }} className="h-full rounded-full bg-gradient-to-r from-violet-400 to-fuchsia-300" />
                </div>
                <span className="text-xs font-semibold text-[var(--public-accent)]">{visual.ready}</span>
              </div>
              <div className="flex h-28 items-end gap-2 sm:h-40">
                {[38, 56, 48, 72, 63, 86, 78, 92, 74, 98].map((height, index) => (
                  <motion.div
                    key={index}
                    initial={{ height: 10 }}
                    animate={{ height }}
                    transition={{ duration: 0.7, delay: index * 0.04 }}
                    className="flex-1 rounded-t-xl bg-gradient-to-t from-violet-900 via-fuchsia-500 to-violet-200"
                  />
                ))}
              </div>
            </div>
            <div className="mt-5 hidden gap-3 sm:grid sm:grid-cols-2">
              {['Suppression check passed', 'Provider health stable'].map((item) => (
                <div key={item} className="public-panel rounded-2xl p-4 text-sm">
                  <CheckCircle2 className="mb-2 text-[var(--public-accent)]" size={17} />
                  {item}
                </div>
              ))}
            </div>
            <div className="public-border mt-5 hidden overflow-hidden rounded-2xl border bg-[var(--public-panel-strong)] p-4 sm:block">
              <div className="grid grid-cols-[56px_1fr] gap-4">
                <div className="relative h-14 w-14 overflow-hidden rounded-2xl bg-gradient-to-br from-fuchsia-300 via-violet-300 to-purple-700">
                  <div className="absolute inset-x-2 top-3 h-2 rounded-full bg-white/70" />
                  <div className="absolute inset-x-3 bottom-3 h-5 rounded-t-2xl bg-white/28" />
                </div>
                <div>
                  <p className="public-heading text-sm font-semibold">Workspace preview</p>
                  <p className="public-muted mt-1 text-xs leading-5">Audience targeting, approved creative, delivery health, personalization checks, and engagement tracking move in one launch view.</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function FeatureHeroVisual() {
  return (
    <div className="public-panel relative min-h-[500px] overflow-hidden rounded-[1.65rem] p-5">
      <div className="public-border flex items-center justify-between border-b pb-4">
        <WindowDots />
        <span className="public-muted text-xs font-semibold uppercase tracking-[0.18em]">Feature graph</span>
      </div>
      <div className="relative mt-8 grid min-h-[380px] place-items-center">
        {['Audience', 'Template', 'Campaign', 'Delivery', 'Analytics'].map((label, index) => {
          const angle = (index / 5) * Math.PI * 2;
          return (
            <motion.div
              key={label}
              animate={{ x: Math.cos(angle) * 150, y: Math.sin(angle) * 120, scale: [1, 1.05, 1] }}
              transition={{ scale: { duration: 2.5, repeat: Infinity, delay: index * 0.2 } }}
              whileHover={{ scale: 1.12, rotate: 2 }}
              className="public-panel absolute w-36 rounded-2xl p-4 text-center"
            >
              <p className="public-heading text-sm font-semibold">{label}</p>
              <p className="public-muted mt-1 text-xs">Synced</p>
            </motion.div>
          );
        })}
        <motion.div animate={{ rotate: 360 }} transition={{ duration: 22, repeat: Infinity, ease: 'linear' }} className="absolute h-72 w-72 rounded-full border border-dashed border-fuchsia-400/45" />
        <div className="public-panel relative z-10 grid h-36 w-36 place-items-center rounded-full text-center">
          <ShieldCheck className="text-[var(--public-accent)]" size={28} />
          <p className="public-heading mt-2 text-sm font-semibold">Governed runtime</p>
        </div>
      </div>
    </div>
  );
}

function ModuleHeroVisual() {
  return (
    <div className="public-panel relative overflow-hidden rounded-[1.65rem] p-5">
      <div className="public-border flex items-center justify-between border-b pb-4">
        <WindowDots />
        <span className="public-muted text-xs font-semibold uppercase tracking-[0.18em]">Studio relay</span>
      </div>
      <div className="mt-7 grid gap-3">
        {studios.map((studio, index) => {
          const Icon = studio.icon;
          return (
            <motion.div
              key={studio.name}
              animate={{ x: [0, index % 2 ? 18 : -18, 0] }}
              transition={{ duration: 5 + index * 0.35, repeat: Infinity, ease: 'easeInOut' }}
              whileHover={{ x: 0, scale: 1.03 }}
              className="public-border grid grid-cols-[42px_1fr_auto] items-center gap-3 rounded-2xl border bg-[var(--public-panel-strong)] p-3"
            >
              <span className="grid h-10 w-10 place-items-center rounded-xl bg-fuchsia-400/14 text-[var(--public-accent)]"><Icon size={18} /></span>
              <div>
                <p className="public-heading text-sm font-semibold">{studio.name}</p>
                <p className="public-muted text-xs">{studio.desc}</p>
              </div>
              <span className="h-2 w-2 rounded-full bg-emerald-400" />
            </motion.div>
          );
        })}
      </div>
    </div>
  );
}

function PricingHeroVisual() {
  const plans = marketingPages.pricing.plans;
  return (
    <div className="public-panel relative overflow-hidden rounded-[1.65rem] p-5">
      <div className="public-border flex items-center justify-between border-b pb-4">
        <WindowDots />
        <span className="public-muted text-xs font-semibold uppercase tracking-[0.18em]">Capacity model</span>
      </div>
      <div className="mt-7 grid gap-4">
        {plans.map((plan, index) => (
          <motion.div
            key={plan.name}
            whileHover={{ scale: 1.03, y: -4 }}
            className={`public-border rounded-2xl border p-4 ${index === 1 ? 'bg-fuchsia-400/12' : 'bg-[var(--public-panel-strong)]'}`}
          >
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="public-heading text-lg font-semibold">{plan.name}</p>
                <p className="public-muted text-sm">{plan.price}</p>
              </div>
              <motion.span animate={{ rotate: [0, 8, -8, 0] }} transition={{ duration: 3, repeat: Infinity, delay: index * 0.4 }} className="grid h-12 w-12 place-items-center rounded-full bg-white/70 text-[var(--public-accent)]">
                <Zap size={20} />
              </motion.span>
            </div>
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-[var(--public-panel)]">
              <motion.div animate={{ width: [`${34 + index * 20}%`, `${48 + index * 22}%`, `${34 + index * 20}%`] }} transition={{ duration: 4, repeat: Infinity }} className="h-full rounded-full bg-gradient-to-r from-emerald-400 via-violet-400 to-fuchsia-400" />
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
}

function AboutHeroVisual() {
  return (
    <div className="public-panel relative overflow-hidden rounded-[1.65rem] p-6">
      <div className="absolute inset-y-8 left-1/2 w-px bg-gradient-to-b from-transparent via-fuchsia-400/50 to-transparent" />
      {marketingPages.about.timeline.map((item, index) => (
        <motion.div
          key={item.title}
          animate={{ y: [0, index % 2 ? -10 : 10, 0] }}
          transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut', delay: index * 0.35 }}
          className={`public-panel relative mb-5 w-[82%] rounded-2xl p-4 ${index % 2 ? 'ml-auto' : ''}`}
        >
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--public-accent)]">0{index + 1}</p>
          <h3 className="public-heading mt-2 font-semibold">{item.title}</h3>
          <p className="public-muted mt-2 text-sm leading-6">{item.body}</p>
        </motion.div>
      ))}
    </div>
  );
}

function ContactHeroVisual() {
  return (
    <div className="public-panel relative overflow-hidden rounded-[1.65rem] p-5">
      <div className="public-border flex items-center justify-between border-b pb-4">
        <WindowDots />
        <span className="public-muted text-xs font-semibold uppercase tracking-[0.18em]">Intake routing</span>
      </div>
      <div className="relative mt-8 min-h-[390px]">
        {['Workspace rollout', 'Provider review', 'Migration', 'Security'].map((item, index) => (
          <motion.div
            key={item}
            animate={{ x: [0, 18, 0], y: [0, -8, 0] }}
            transition={{ duration: 4 + index * 0.4, repeat: Infinity, ease: 'easeInOut' }}
            whileHover={{ scale: 1.06 }}
            className="public-panel mb-4 rounded-2xl p-4"
          >
            <p className="public-heading font-semibold">{item}</p>
            <p className="public-muted mt-1 text-sm">Routed to product and solution review.</p>
          </motion.div>
        ))}
        <motion.div animate={{ scale: [1, 1.08, 1] }} transition={{ duration: 2.6, repeat: Infinity }} className="absolute bottom-3 right-3 grid h-24 w-24 place-items-center rounded-full bg-fuchsia-500 text-white shadow-2xl">
          <MousePointerClick size={26} />
        </motion.div>
      </div>
    </div>
  );
}

function SignalFlow({ steps }: { steps: string[] }) {
  return (
    <div className="public-panel rounded-[1.35rem] p-5">
      <div className="grid gap-3 sm:grid-cols-5">
        {steps.map((step, index) => (
          <div key={step} className="rounded-2xl bg-[var(--public-panel-strong)] p-4 text-center">
            <p className="mx-auto flex h-9 w-9 items-center justify-center rounded-full bg-fuchsia-300 text-sm font-semibold text-purple-950">{index + 1}</p>
            <p className="public-heading mt-3 text-sm font-semibold">{step}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function LiveSurfaceShowcase() {
  const surfaces = [
    { title: 'Audience', value: '42.8k', icon: Users, color: 'from-emerald-400 to-teal-300' },
    { title: 'Creative', value: '12', icon: Wand2, color: 'from-fuchsia-400 to-pink-300' },
    { title: 'Delivery', value: '99.2%', icon: RadioTower, color: 'from-violet-400 to-indigo-300' },
    { title: 'Tracking', value: 'Live', icon: BarChart3, color: 'from-amber-300 to-orange-300' },
  ];

  return (
    <div className="grid gap-5 lg:grid-cols-[0.9fr_1.1fr]">
      <div className="public-panel overflow-hidden rounded-[1.35rem] p-5">
        <div className="public-border flex items-center justify-between border-b pb-4">
          <WindowDots />
          <span className="public-muted text-xs font-semibold uppercase tracking-[0.18em]">Auto loop</span>
        </div>
        <div className="mt-6 flex gap-4 overflow-hidden">
          {[...surfaces, ...surfaces].map((surface, index) => {
            const Icon = surface.icon;
            return (
              <motion.div
                key={`${surface.title}-${index}`}
                animate={{ x: ['0%', '-120%'] }}
                transition={{ duration: 16, repeat: Infinity, ease: 'linear' }}
                className="public-border min-w-[220px] rounded-2xl border bg-[var(--public-panel-strong)] p-4"
              >
                <span className={`grid h-11 w-11 place-items-center rounded-2xl bg-gradient-to-br ${surface.color} text-white`}>
                  <Icon size={20} />
                </span>
                <p className="public-heading mt-4 text-lg font-semibold">{surface.title}</p>
                <p className="public-muted mt-1 text-sm">{surface.value} workspace signal</p>
              </motion.div>
            );
          })}
        </div>
      </div>
      <div className="public-panel relative min-h-[300px] overflow-hidden rounded-[1.35rem] p-6">
        <div className="grid gap-3 sm:grid-cols-2">
          {surfaces.map((surface, index) => {
            const Icon = surface.icon;
            return (
              <motion.div
                key={surface.title}
                animate={{ y: [0, index % 2 ? 14 : -14, 0] }}
                transition={{ duration: 5 + index * 0.3, repeat: Infinity, ease: 'easeInOut' }}
                whileHover={{ scale: 1.05, rotate: index % 2 ? -1.5 : 1.5 }}
                className="public-border rounded-2xl border bg-[var(--public-panel-strong)] p-4"
              >
                <Icon className="text-[var(--public-accent)]" size={20} />
                <p className="public-heading mt-3 font-semibold">{surface.title}</p>
                <p className="public-muted mt-1 text-sm">Moves with launch status.</p>
              </motion.div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function SolutionLayers() {
  const cards = marketingPages.home.solutionLayers;
  const [activeIndex, setActiveIndex] = useState(0);
  const active = cards[activeIndex];

  return (
    <MarketingSection eyebrow="Solution layers" title="Three operating layers that keep every launch under control.">
      <div className="grid gap-5 lg:grid-cols-[1.05fr_0.95fr] lg:items-stretch">
        <div className="grid gap-4">
        {cards.map((card, index) => (
          <motion.div
            key={card.title}
            initial={{ opacity: 0, y: 18 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.25 }}
            transition={{ duration: 0.42, delay: index * 0.05 }}
          >
            <button type="button" onClick={() => setActiveIndex(index)} className="block w-full text-left">
            <PublicBentoCard className={`overflow-hidden p-0 ${activeIndex === index ? 'border-fuchsia-300/55 bg-fuchsia-400/[0.08]' : ''}`}>
              <div className="public-border border-b bg-[var(--public-panel-strong)] p-5">
                <p className="public-muted text-xs font-semibold uppercase tracking-[0.2em]">Operating layer</p>
                <div className="mt-3 flex items-end justify-between gap-3">
                  <h3 className="public-heading text-lg font-semibold">{card.title}</h3>
                  <span className="text-3xl font-semibold text-[var(--public-accent)]">{card.metric}</span>
                </div>
                <p className="public-muted mt-3 text-sm leading-6">{card.body}</p>
              </div>
              <div className="grid gap-2 p-4">
                {card.items.map((row, rowIndex) => (
                  <div key={row} className="flex items-center justify-between rounded-xl bg-[var(--public-panel-strong)] px-3 py-2 text-sm">
                    <span className="public-heading font-medium">{row}</span>
                    <motion.span
                      initial={{ width: 18 }}
                      whileInView={{ width: 42 + rowIndex * 16 }}
                      viewport={{ once: true }}
                      transition={{ duration: 0.7, delay: 0.12 + rowIndex * 0.06 }}
                      className="h-2 rounded-full bg-gradient-to-r from-violet-500 to-fuchsia-400"
                    />
                  </div>
                ))}
              </div>
            </PublicBentoCard>
            </button>
          </motion.div>
        ))}
        </div>
        <motion.div
          key={active.title}
          initial={{ opacity: 0, scale: 0.96, y: 18 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          transition={{ duration: 0.35 }}
          className="public-panel relative min-h-[360px] overflow-hidden rounded-[1.35rem] p-6"
        >
          <div className="absolute inset-x-8 top-24 h-px bg-gradient-to-r from-transparent via-fuchsia-400/50 to-transparent" />
          <div className="absolute inset-y-12 left-1/2 w-px bg-gradient-to-b from-transparent via-violet-400/50 to-transparent" />
          <p className="text-xs font-semibold uppercase tracking-[0.22em] text-[var(--public-accent)]">Clicked detail</p>
          <h3 className="public-heading mt-3 text-2xl font-semibold">{active.title}</h3>
          <p className="public-muted mt-3 leading-7">{active.body}</p>
          <div className="mt-8 grid grid-cols-2 gap-3">
            {active.items.map((item, index) => (
              <motion.div
                key={item}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.08 }}
                whileHover={{ rotate: index % 2 ? -2 : 2, scale: 1.04 }}
                className="public-border rounded-2xl border bg-[var(--public-panel-strong)] p-4"
              >
                <p className="public-heading text-sm font-semibold">{item}</p>
                <p className="public-muted mt-2 text-xs">Checked before send</p>
              </motion.div>
            ))}
          </div>
          <motion.div animate={{ x: ['-20%', '115%'] }} transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut' }} className="absolute bottom-8 left-0 h-10 w-24 rounded-full bg-gradient-to-r from-violet-400 to-fuchsia-400 opacity-70 blur-xl" />
        </motion.div>
      </div>
    </MarketingSection>
  );
}

function FeatureSignalMap() {
  const nodes = [
    { label: 'RBAC', icon: ShieldCheck },
    { label: 'Config', icon: Database },
    { label: 'Campaign', icon: GitBranch },
    { label: 'Inbox', icon: MailCheck },
    { label: 'Telemetry', icon: BarChart3 },
  ];
  return (
    <div className="public-panel relative min-h-[390px] overflow-hidden rounded-[1.35rem] p-5">
      <motion.div animate={{ rotate: 360 }} transition={{ duration: 28, repeat: Infinity, ease: 'linear' }} className="absolute left-1/2 top-1/2 h-72 w-72 -translate-x-1/2 -translate-y-1/2 rounded-full border border-dashed border-fuchsia-400/40" />
      <div className="relative grid min-h-[340px] place-items-center">
        <div className="public-panel z-10 grid h-32 w-32 place-items-center rounded-full text-center">
          <Sparkles className="text-[var(--public-accent)]" size={24} />
          <p className="public-heading text-sm font-semibold">Operator view</p>
        </div>
        {nodes.map((node, index) => {
          const Icon = node.icon;
          const positions = ['left-4 top-8', 'right-8 top-10', 'left-10 bottom-12', 'right-4 bottom-8', 'left-1/2 top-1 -translate-x-1/2'];
          return (
            <motion.div key={node.label} whileHover={{ scale: 1.1 }} animate={{ y: [0, -10, 0] }} transition={{ duration: 4, repeat: Infinity, delay: index * 0.25 }} className={`public-panel absolute ${positions[index]} rounded-2xl p-4`}>
              <Icon className="text-[var(--public-accent)]" size={20} />
              <p className="public-heading mt-2 text-sm font-semibold">{node.label}</p>
            </motion.div>
          );
        })}
      </div>
    </div>
  );
}

function StudioRelay({ steps }: { steps: string[] }) {
  return (
    <div className="public-panel overflow-hidden rounded-[1.35rem] p-5">
      <div className="grid gap-3 md:grid-cols-3">
        {steps.map((step, index) => (
          <motion.div
            key={step}
            whileHover={{ y: -6, scale: 1.04 }}
            animate={{ opacity: [0.72, 1, 0.72] }}
            transition={{ duration: 3.2, repeat: Infinity, delay: index * 0.22 }}
            className="public-border relative min-h-28 overflow-hidden rounded-2xl border bg-[var(--public-panel-strong)] p-4"
          >
            <p className="public-heading font-semibold">{step}</p>
            <p className="public-muted mt-2 text-sm">Shared context</p>
            <motion.span animate={{ x: ['-120%', '220%'] }} transition={{ duration: 3.8, repeat: Infinity, delay: index * 0.25 }} className="absolute bottom-0 left-0 h-1 w-24 bg-gradient-to-r from-transparent via-fuchsia-400 to-transparent" />
          </motion.div>
        ))}
      </div>
    </div>
  );
}

function PricingScaleVisual() {
  const rows = [
    ['Workspace depth', 38, 70, 96],
    ['Delivery governance', 26, 76, 100],
    ['Automation control', 32, 82, 100],
    ['Audit readiness', 44, 78, 100],
  ] as const;
  return (
    <div className="public-panel overflow-hidden rounded-[1.35rem] p-5">
      <div className="grid gap-5 lg:grid-cols-[220px_1fr]">
        <div>
          <p className="public-heading text-xl font-semibold">Launch to Enterprise</p>
          <p className="public-muted mt-3 text-sm leading-6">Plan value grows by adding governance, workspace scale, provider strategy, and review depth.</p>
        </div>
        <div className="grid gap-4">
          {rows.map(([label, launch, scale, enterprise]) => (
            <div key={label}>
              <div className="mb-2 flex items-center justify-between text-sm">
                <span className="public-heading font-semibold">{label}</span>
                <span className="public-muted">Launch / Scale / Enterprise</span>
              </div>
              <div className="grid gap-2 sm:grid-cols-3">
                {[launch, scale, enterprise].map((value, index) => (
                  <div key={index} className="h-3 overflow-hidden rounded-full bg-[var(--public-panel-strong)]">
                    <motion.div initial={{ width: 0 }} whileInView={{ width: `${value}%` }} viewport={{ once: true }} transition={{ duration: 0.8, delay: index * 0.08 }} className="h-full rounded-full bg-gradient-to-r from-violet-500 to-fuchsia-400" />
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function OperatingTimeline({ items }: { items: ReadonlyArray<{ title: string; body: string }> }) {
  return (
    <div className="public-panel rounded-[1.35rem] p-5">
      <div className="relative grid gap-4">
        {items.map((item, index) => (
          <motion.div key={item.title} whileHover={{ x: 8 }} className="grid grid-cols-[44px_1fr] gap-4 rounded-2xl bg-[var(--public-panel-strong)] p-4">
            <motion.p animate={{ scale: [1, 1.12, 1] }} transition={{ duration: 2.8, repeat: Infinity, delay: index * 0.25 }} className="flex h-11 w-11 items-center justify-center rounded-full bg-fuchsia-300 text-sm font-semibold text-purple-950">{index + 1}</motion.p>
            <div>
              <h3 className="public-heading font-semibold">{item.title}</h3>
              <p className="public-muted mt-1 text-sm leading-6">{item.body}</p>
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
}

function ContactRouteVisual() {
  return (
    <PublicBentoCard className="relative min-h-[230px] overflow-hidden">
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[var(--public-accent)]">Routing map</p>
      <h3 className="public-heading mt-3 text-lg font-semibold">Every request lands with context.</h3>
      <div className="mt-6 grid grid-cols-3 gap-2">
        {[Target, Clock3, ShieldCheck].map((Icon, index) => (
          <motion.div key={index} animate={{ y: [0, -8, 0] }} transition={{ duration: 3, repeat: Infinity, delay: index * 0.25 }} className="grid h-20 place-items-center rounded-2xl bg-[var(--public-panel-strong)]">
            <Icon className="text-[var(--public-accent)]" size={22} />
          </motion.div>
        ))}
      </div>
      <motion.div animate={{ x: ['-20%', '110%'] }} transition={{ duration: 4.2, repeat: Infinity, ease: 'easeInOut' }} className="absolute bottom-5 left-0 h-2 w-24 rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" />
    </PublicBentoCard>
  );
}

function PublicComparisonTable({ rows }: { rows: ReadonlyArray<{ feature: string; launch: string; scale: string; enterprise: string }> }) {
  return (
    <div className="public-panel overflow-x-auto rounded-[1.2rem]">
      <table className="min-w-[720px] w-full text-left text-sm">
        <thead className="bg-[var(--public-panel-strong)]">
          <tr>
            {['Feature', 'Launch', 'Scale', 'Enterprise'].map((heading) => (
              <th key={heading} className="public-heading px-5 py-4 font-semibold">{heading}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.feature} className="public-border border-t">
              <td className="public-heading px-5 py-4 font-semibold">{row.feature}</td>
              <td className="public-muted px-5 py-4">{row.launch}</td>
              <td className="public-muted px-5 py-4">{row.scale}</td>
              <td className="public-muted px-5 py-4">{row.enterprise}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PublicContactForm({ interests }: { interests: string[] }) {
  const [form, setForm] = useState({
    name: '',
    workEmail: '',
    company: '',
    interest: interests[0] ?? '',
    message: '',
  });
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const updateField = (key: keyof typeof form, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoading(true);
    setStatus(null);
    try {
      const response = await post<{ id: string; status: string; message: string }>('/public/contact', {
        ...form,
        sourcePage: 'contact',
      });
      setStatus({ type: 'success', message: response.message || 'Request received. We will follow up shortly.' });
      setForm({ name: '', workEmail: '', company: '', interest: interests[0] ?? '', message: '' });
    } catch (error: any) {
      const message = error?.normalized?.message || error?.response?.data?.error?.message || 'Could not submit request. Please try again.';
      setStatus({ type: 'error', message });
    } finally {
      setLoading(false);
    }
  };

  return (
    <PublicBentoCard className="p-6 md:p-7">
      <form className="grid gap-4" onSubmit={submit}>
        <div className="grid gap-4 md:grid-cols-2">
          <PublicInput label="Name" value={form.name} onChange={(value) => updateField('name', value)} placeholder="Ada Lovelace" />
          <PublicInput label="Work email" type="email" required value={form.workEmail} onChange={(value) => updateField('workEmail', value)} placeholder="ada@company.com" />
        </div>
        <PublicInput label="Company" required value={form.company} onChange={(value) => updateField('company', value)} placeholder="Company name" />
        <label className="grid gap-2 text-sm font-medium text-[var(--public-text)]">
          Interest
          <select
            value={form.interest}
            onChange={(event) => updateField('interest', event.target.value)}
            className="public-border h-12 rounded-2xl border bg-[var(--public-button-soft)] px-4 text-[var(--public-text)] outline-none transition focus:border-fuchsia-300/60"
          >
            {interests.map((interest) => (
              <option key={interest} value={interest}>{interest}</option>
            ))}
          </select>
        </label>
        <label className="grid gap-2 text-sm font-medium text-[var(--public-text)]">
          Message
          <textarea
            required
            rows={5}
            value={form.message}
            onChange={(event) => updateField('message', event.target.value)}
            placeholder="Tell us about your rollout, provider setup, migration, or review."
            className="public-border rounded-2xl border bg-[var(--public-button-soft)] px-4 py-3 text-[var(--public-text)] outline-none transition placeholder:text-[var(--public-soft)] focus:border-fuchsia-300/60"
          />
        </label>
        {status ? (
          <p className={`rounded-2xl px-4 py-3 text-sm ${status.type === 'success' ? 'bg-emerald-300/12 text-emerald-500' : 'bg-red-400/12 text-red-500'}`}>
            {status.message}
          </p>
        ) : null}
        <Button type="submit" size="lg" loading={loading} disabled={loading} className="public-button-primary" icon={<ArrowRight size={18} />}>
          Request Consultation
        </Button>
      </form>
    </PublicBentoCard>
  );
}

function PublicInput({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
  required = false,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  type?: string;
  required?: boolean;
}) {
  return (
    <label className="grid gap-2 text-sm font-medium text-[var(--public-text)]">
      {label}
      <input
        type={type}
        required={required}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="public-border h-12 rounded-2xl border bg-[var(--public-button-soft)] px-4 text-[var(--public-text)] outline-none transition placeholder:text-[var(--public-soft)] focus:border-fuchsia-300/60"
      />
    </label>
  );
}

function FinalCta({ title, body }: { title: string; body: string }) {
  return (
    <section className="mx-auto max-w-7xl px-4 pb-20 sm:px-6">
      <div className="public-panel rounded-[1.4rem] bg-gradient-to-br from-fuchsia-500/14 to-violet-500/10 p-6 shadow-[0_0_90px_rgba(168,85,247,0.16)] md:p-9">
        <div className="grid gap-6 md:grid-cols-[1fr_auto] md:items-center">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.22em] text-[var(--public-accent)]">Ready</p>
            <h2 className="public-heading mt-3 text-2xl font-semibold md:text-3xl">{title}</h2>
            <p className="public-muted mt-3 max-w-2xl">{body}</p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Link href="/signup"><Button size="lg" className="public-button-primary">Create Workspace</Button></Link>
            <Link href="/contact"><Button variant="secondary" size="lg" className="public-button-secondary">Talk to Team</Button></Link>
          </div>
        </div>
      </div>
    </section>
  );
}

function normalizePageKey(pageKey: string): MarketingPageKey {
  if (pageKey === 'features' || pageKey === 'modules' || pageKey === 'pricing' || pageKey === 'about' || pageKey === 'contact') {
    return pageKey;
  }
  return 'home';
}
