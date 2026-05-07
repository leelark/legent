'use client';

import Link from 'next/link';
import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import {
  ArrowRight,
  BarChart3,
  BrainCircuit,
  CheckCircle2,
  ChevronRight,
  CircleDot,
  Clock3,
  Database,
  LineChart,
  MailCheck,
  MousePointerClick,
  Network,
  ShieldCheck,
  Sparkles,
  Target,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import { MarketingShell } from '@/components/marketing/MarketingShell';
import { Button } from '@/components/ui/Button';
import { post } from '@/lib/api-client';
import {
  contactRoutes,
  homeScenarios,
  marketingPages,
  proofCards,
  studios,
  type MarketingPageKey,
} from '@/lib/marketing-data';

type PageViewProps = {
  pageKey: string;
  titleFallback: string;
};

const fadeUp = {
  hidden: { opacity: 0, y: 24 },
  visible: { opacity: 1, y: 0 },
};

export function PublicPageView({ pageKey, titleFallback }: PageViewProps) {
  const key = normalizePageKey(pageKey);
  const page = marketingPages[key];

  return (
    <MarketingShell>
      {key === 'home' && <HomePage />}
      {key === 'features' && <FeaturesPage />}
      {key === 'modules' && <ModulesPage />}
      {key === 'pricing' && <PricingPage />}
      {key === 'about' && <AboutPage />}
      {key === 'contact' && <ContactPage />}
      {!page.title && <span className="sr-only">{titleFallback}</span>}
    </MarketingShell>
  );
}

function useGsapReveals() {
  const scope = useRef<HTMLDivElement | null>(null);
  const reducedMotion = useReducedMotion();

  useEffect(() => {
    if (!scope.current || reducedMotion) return;
    gsap.registerPlugin(ScrollTrigger);
    const ctx = gsap.context(() => {
      gsap.utils.toArray<HTMLElement>('[data-gsap-reveal]').forEach((element) => {
        gsap.fromTo(
          element,
          { autoAlpha: 0, y: 42 },
          {
            autoAlpha: 1,
            y: 0,
            duration: 0.75,
            ease: 'power3.out',
            scrollTrigger: { trigger: element, start: 'top 84%' },
          }
        );
      });
      gsap.utils.toArray<HTMLElement>('[data-gsap-parallax]').forEach((element) => {
        gsap.to(element, {
          yPercent: Number(element.dataset.gsapParallax || -10),
          ease: 'none',
          scrollTrigger: { trigger: element, start: 'top bottom', end: 'bottom top', scrub: true },
        });
      });
    }, scope);

    return () => ctx.revert();
  }, [reducedMotion]);

  return scope;
}

function HomePage() {
  const page = marketingPages.home;
  const scope = useGsapReveals();
  const [activeScenario, setActiveScenario] = useState(0);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setActiveScenario((current) => (current + 1) % homeScenarios.length);
    }, 5600);
    return () => window.clearInterval(timer);
  }, []);

  const scenario = homeScenarios[activeScenario];

  return (
    <div ref={scope}>
      <section className="relative overflow-hidden">
        <div className="mx-auto grid min-h-[calc(100vh-4rem)] max-w-7xl gap-10 px-4 pb-16 pt-10 sm:px-6 lg:grid-cols-2 lg:items-center">
          <motion.div initial="hidden" animate="visible" variants={fadeUp} transition={{ duration: 0.55 }}>
            <Pill icon={Sparkles}>{page.eyebrow}</Pill>
            <h1 className="public-heading mt-5 max-w-4xl text-balance text-4xl font-semibold leading-[1.02] sm:text-5xl lg:text-7xl">
              {page.title}
            </h1>
            <p className="public-muted mt-6 max-w-2xl text-base leading-8 sm:text-lg">{page.subtitle}</p>
            <div className="mt-8 flex flex-wrap gap-3">
              <PublicLinkButton href="/signup" icon={<ArrowRight size={18} />}>{page.primaryCta}</PublicLinkButton>
              <PublicLinkButton href="/modules" variant="secondary">{page.secondaryCta}</PublicLinkButton>
            </div>
            <div className="mt-8 grid gap-3 sm:grid-cols-3">
              {page.highlights.map((item) => (
                <div key={item} className="public-panel rounded-2xl px-4 py-3 text-sm font-medium">
                  <CheckCircle2 className="mb-2 text-[var(--public-accent)]" size={16} />
                  {item}
                </div>
              ))}
            </div>
          </motion.div>
          <HomeDashboardTheater active={activeScenario} setActive={setActiveScenario} />
        </div>
      </section>

      <MarketingSection eyebrow="Operating proof" title="Measurable lift across launch, delivery, and team velocity.">
        <div className="grid gap-4 md:grid-cols-3">
          {page.metrics.map((metric, index) => (
            <MetricPanel key={metric.label} metric={metric} index={index} />
          ))}
        </div>
      </MarketingSection>

      <section className="public-border border-y bg-[var(--public-panel)]" data-gsap-reveal>
        <div className="mx-auto grid max-w-7xl gap-8 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:items-center">
          <div>
            <Pill icon={Network}>Signal to outcome</Pill>
            <h2 className="public-heading mt-4 text-balance text-3xl font-semibold md:text-5xl">One operating loop from customer signal to business result.</h2>
            <p className="public-muted mt-5 max-w-xl leading-7">
              Customer behavior, AI recommendations, approval evidence, provider risk, and revenue feedback move through a visible workflow instead of disconnected tools.
            </p>
          </div>
          <SignalRibbon steps={[...page.signalFlow]} />
        </div>
      </section>

      <MarketingSection eyebrow="Scenario control" title="Click a layer to see how Legent changes the operating model.">
        <ScenarioLayerGrid />
      </MarketingSection>

      <MarketingSection eyebrow="Product surfaces" title="Multiple live surfaces move together as a launch progresses.">
        <SurfaceLoop scenario={scenario} />
      </MarketingSection>

      <FinalCta title="Build a workspace that turns email operations into enterprise execution." body="Start free, then scale into the studios, governance, delivery control, and analytics your team needs." />
    </div>
  );
}

function FeaturesPage() {
  const page = marketingPages.features;
  const scope = useGsapReveals();
  const [active, setActive] = useState(0);
  const activeFeature = page.features[active];
  const ActiveIcon = activeFeature.icon;

  return (
    <div ref={scope}>
      <PageHero
        eyebrow={page.eyebrow}
        title={page.title}
        subtitle={page.subtitle}
        primaryCta={page.primaryCta}
        secondaryCta={page.secondaryCta}
        secondaryHref="/pricing"
        visual={<FeatureArchitecture active={active} setActive={setActive} />}
      />
      <MarketingSection eyebrow="Feature system" title="Capabilities reveal their operational impact as you explore.">
        <div className="grid gap-5 lg:grid-cols-2">
          <div className="grid gap-3">
            {page.features.map((feature, index) => {
              const Icon = feature.icon;
              return (
                <button
                  key={feature.title}
                  type="button"
                  onClick={() => setActive(index)}
                  className={`public-border rounded-2xl border p-4 text-left transition hover:-translate-y-1 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${active === index ? 'bg-[var(--public-panel-strong)]' : 'bg-[var(--public-panel)]'}`}
                >
                  <div className="flex items-start gap-3">
                    <Icon className="mt-1 text-[var(--public-accent)]" size={20} />
                    <div>
                      <h3 className="public-heading font-semibold">{feature.title}</h3>
                      <p className="public-muted mt-1 text-sm leading-6">{feature.body}</p>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
          <motion.div
            key={activeFeature.title}
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            className="public-panel min-h-[520px] overflow-hidden rounded-[1.6rem] p-6"
          >
            <Pill icon={ActiveIcon}>Expanded capability</Pill>
            <h3 className="public-heading mt-5 text-3xl font-semibold">{activeFeature.title}</h3>
            <p className="public-muted mt-4 max-w-2xl leading-7">{activeFeature.body}</p>
            <div className="mt-8 grid gap-4 sm:grid-cols-2">
              {['State visible', 'Risk scored', 'Action governed', 'Outcome measured'].map((item, index) => (
                <motion.div
                  key={item}
                  initial={{ opacity: 0, y: 16 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.06 }}
                  className="rounded-2xl bg-[var(--public-panel-strong)] p-5"
                >
                  <span className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-400/15 text-emerald-500">{index + 1}</span>
                  <p className="public-heading mt-4 font-semibold">{item}</p>
                  <p className="public-muted mt-2 text-sm">Operators can see why this step is safe before it runs.</p>
                </motion.div>
              ))}
            </div>
          </motion.div>
        </div>
      </MarketingSection>
      <MarketingSection eyebrow="Process flow" title="Every feature lands inside a full operating lifecycle.">
        <ProcessFlow steps={[...page.process]} />
      </MarketingSection>
      <FinalCta title="See how governed features become faster launches." body="Create a workspace, connect studios, and give every team a clearer path from idea to inbox." />
    </div>
  );
}

function ModulesPage() {
  const page = marketingPages.modules;
  const scope = useGsapReveals();
  const [active, setActive] = useState(0);
  const selected = studios[active];
  const SelectedIcon = selected.icon;

  return (
    <div ref={scope}>
      <PageHero
        eyebrow={page.eyebrow}
        title={page.title}
        subtitle={page.subtitle}
        primaryCta={page.primaryCta}
        secondaryCta={page.secondaryCta}
        secondaryHref="/features"
        visual={<ModuleSystemMap active={active} setActive={setActive} />}
      />
      <MarketingSection eyebrow="Architecture map" title="Select a module and watch its relationships change.">
        <div className="grid gap-6 lg:grid-cols-2">
          <ModuleSystemMap active={active} setActive={setActive} large />
          <AnimatePresence mode="wait">
            <motion.div
              key={selected.name}
              initial={{ opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
              className="public-panel rounded-[1.5rem] p-6"
            >
              <Pill icon={SelectedIcon}>{selected.proof}</Pill>
              <h2 className="public-heading mt-5 text-3xl font-semibold">{selected.name}</h2>
              <p className="public-muted mt-4 leading-7">{selected.desc}</p>
              <p className="mt-5 rounded-2xl bg-[var(--public-panel-strong)] p-4 text-sm font-semibold text-[var(--public-text)]">{selected.outcome}</p>
              <div className="mt-6 grid gap-3">
                {selected.capabilities.map((capability, index) => (
                  <motion.div
                    key={capability}
                    initial={{ opacity: 0, x: 20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: index * 0.06 }}
                    className="flex items-center justify-between rounded-2xl bg-[var(--public-panel-strong)] px-4 py-3"
                  >
                    <span className="public-heading text-sm font-semibold">{capability}</span>
                    <ChevronRight className="text-[var(--public-accent)]" size={16} />
                  </motion.div>
                ))}
              </div>
            </motion.div>
          </AnimatePresence>
        </div>
      </MarketingSection>
      <MarketingSection eyebrow="Animated data flow" title="Shared runtime context removes disconnected handoffs.">
        <DataFlowRail steps={[...page.flow]} />
      </MarketingSection>
      <FinalCta title="Open the product around your real operating model." body="Use dedicated studios without losing shared permissions, audit, configuration, or analytics context." />
    </div>
  );
}

function PricingPage() {
  const page = marketingPages.pricing;
  const scope = useGsapReveals();
  const [billing, setBilling] = useState<'monthly' | 'yearly'>('monthly');
  const annual = billing === 'yearly';

  return (
    <div ref={scope}>
      <PageHero
        eyebrow={page.eyebrow}
        title={page.title}
        subtitle={page.subtitle}
        primaryCta={page.primaryCta}
        secondaryCta={page.secondaryCta}
        secondaryHref="/contact"
        visual={<PricingConsole billing={billing} setBilling={setBilling} />}
      />
      <MarketingSection eyebrow="Plan workspace" title="Pricing responds to how your operation matures.">
        <div className="mb-6 inline-flex rounded-2xl border public-border bg-[var(--public-panel)] p-1">
          {(['monthly', 'yearly'] as const).map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => setBilling(mode)}
              className={`rounded-xl px-4 py-2 text-sm font-semibold capitalize transition focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${billing === mode ? 'bg-[var(--public-text)] text-[var(--public-bg)]' : 'public-muted hover:text-[var(--public-text)]'}`}
            >
              {mode}
            </button>
          ))}
        </div>
        <div className="grid gap-4 lg:grid-cols-3">
          {page.plans.map((plan, index) => (
            <PricingPlan key={plan.name} plan={plan} annual={annual} index={index} />
          ))}
        </div>
      </MarketingSection>
      <MarketingSection eyebrow="ROI model" title="Operational discipline compounds as teams scale.">
        <RoiVisualizer />
      </MarketingSection>
      <MarketingSection eyebrow="Comparison" title="Feature depth by workspace tier.">
        <ComparisonTable rows={[...page.comparison]} />
      </MarketingSection>
      <FinalCta title="Find the plan that matches your launch risk." body="Start focused, or talk with us about workspaces, providers, audit controls, and enterprise rollout." />
    </div>
  );
}

function AboutPage() {
  const page = marketingPages.about;
  const scope = useGsapReveals();

  return (
    <div ref={scope}>
      <PageHero
        eyebrow={page.eyebrow}
        title={page.title}
        subtitle={page.subtitle}
        primaryCta={page.primaryCta}
        secondaryCta={page.secondaryCta}
        secondaryHref="/contact"
        visual={<AboutStoryVisual />}
      />
      <MarketingSection eyebrow="Principles" title="The product philosophy behind the operating system.">
        <div className="grid gap-4 md:grid-cols-3">
          {page.principles.map((principle, index) => {
            const Icon = principle.icon;
            return <PrincipleCard key={principle.title} item={principle} icon={Icon} index={index} />;
          })}
        </div>
      </MarketingSection>
      <section className="public-border border-y bg-[var(--public-panel)]" data-gsap-reveal>
        <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6">
          <Pill icon={Clock3}>Story timeline</Pill>
          <h2 className="public-heading mt-4 max-w-3xl text-balance text-3xl font-semibold md:text-5xl">From launch control to enterprise messaging infrastructure.</h2>
          <div className="mt-10 grid gap-5 lg:grid-cols-4">
            {page.timeline.map((item, index) => (
              <motion.div key={item.title} whileHover={{ y: -8 }} className="public-panel min-h-[250px] rounded-[1.4rem] p-5">
                <span className="text-sm font-semibold text-[var(--public-accent)]">{item.year}</span>
                <h3 className="public-heading mt-5 text-xl font-semibold">{item.title}</h3>
                <p className="public-muted mt-3 text-sm leading-6">{item.body}</p>
                <motion.div animate={{ width: ['24%', '86%', '42%'] }} transition={{ duration: 4, repeat: Infinity, delay: index * 0.3 }} className="mt-6 h-1 rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" />
              </motion.div>
            ))}
          </div>
        </div>
      </section>
      <FinalCta title="Email should feel as reliable as the systems around it." body="Legent makes state, ownership, risk, and outcomes visible before every customer message moves." />
    </div>
  );
}

function ContactPage() {
  const page = marketingPages.contact;
  const scope = useGsapReveals();

  return (
    <div ref={scope}>
      <PageHero
        eyebrow={page.eyebrow}
        title={page.title}
        subtitle={page.subtitle}
        primaryCta={page.primaryCta}
        secondaryCta={page.secondaryCta}
        secondaryHref="/blog"
        visual={<ContactRoutingVisual />}
      />
      <MarketingSection eyebrow="Solution intake" title="Tell us where the operating model needs to become reliable.">
        <div className="grid gap-5 lg:grid-cols-2">
          <div className="grid gap-4">
            {page.contactCards.map((card, index) => {
              const Icon = card.icon;
              return <SupportBlock key={card.title} card={card} icon={Icon} index={index} />;
            })}
            <div className="public-panel rounded-[1.3rem] p-5">
              <h3 className="public-heading font-semibold">Response expectations</h3>
              <div className="mt-4 grid gap-3">
                {page.expectations.map((item) => (
                  <div key={item.label} className="flex items-center justify-between gap-3 rounded-2xl bg-[var(--public-panel-strong)] px-4 py-3 text-sm">
                    <span className="public-muted">{item.label}</span>
                    <span className="public-heading font-semibold">{item.value}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
          <PublicContactForm interests={[...page.formInterests]} />
        </div>
      </MarketingSection>
    </div>
  );
}

function PageHero({
  eyebrow,
  title,
  subtitle,
  primaryCta,
  secondaryCta,
  secondaryHref,
  visual,
}: {
  eyebrow: string;
  title: string;
  subtitle: string;
  primaryCta: string;
  secondaryCta: string;
  secondaryHref: string;
  visual: ReactNode;
}) {
  return (
    <section className="relative overflow-hidden">
      <div className="mx-auto grid min-h-[680px] max-w-7xl gap-10 px-4 py-14 sm:px-6 lg:grid-cols-2 lg:items-center">
        <motion.div initial="hidden" animate="visible" variants={fadeUp} transition={{ duration: 0.55 }}>
          <Pill icon={Sparkles}>{eyebrow}</Pill>
          <h1 className="public-heading mt-5 max-w-4xl text-balance text-4xl font-semibold leading-[1.04] sm:text-5xl lg:text-6xl">{title}</h1>
          <p className="public-muted mt-6 max-w-2xl text-base leading-8 sm:text-lg">{subtitle}</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <PublicLinkButton href="/signup" icon={<ArrowRight size={18} />}>{primaryCta}</PublicLinkButton>
            <PublicLinkButton href={secondaryHref} variant="secondary">{secondaryCta}</PublicLinkButton>
          </div>
        </motion.div>
        <motion.div initial={{ opacity: 0, y: 28, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} transition={{ duration: 0.65 }}>
          {visual}
        </motion.div>
      </div>
    </section>
  );
}

export function MarketingSection({ eyebrow, title, children }: { eyebrow: string; title: string; children: ReactNode }) {
  return (
    <section className="mx-auto max-w-7xl px-4 py-16 sm:px-6 md:py-20" data-gsap-reveal>
      <Pill icon={Sparkles}>{eyebrow}</Pill>
      <h2 className="public-heading mt-4 max-w-3xl text-balance text-3xl font-semibold md:text-5xl">{title}</h2>
      <div className="mt-9">{children}</div>
    </section>
  );
}

function HomeDashboardTheater({ active, setActive }: { active: number; setActive: (index: number) => void }) {
  const scenario = homeScenarios[active];
  return (
    <div className="relative" data-gsap-parallax="-7">
      <motion.div className="public-panel absolute -left-4 bottom-20 z-20 hidden w-44 rounded-2xl p-4 shadow-2xl md:block" animate={{ y: [0, 14, 0] }} transition={{ duration: 6, repeat: Infinity }}>
        <p className="public-muted text-xs uppercase">Live activity</p>
        <p className="public-heading mt-2 text-xl font-semibold">{scenario.metric}</p>
      </motion.div>
      <motion.div className="public-panel absolute -right-4 top-14 z-20 hidden w-40 rounded-2xl p-4 shadow-2xl sm:block" animate={{ y: [0, -12, 0] }} transition={{ duration: 7, repeat: Infinity }}>
        <p className="public-muted text-xs uppercase">Status</p>
        <p className="public-heading mt-2 text-lg font-semibold">{scenario.status}</p>
      </motion.div>
      <MacFrame>
        <div className="grid min-h-[520px] lg:grid-cols-[190px_1fr]">
          <aside className="public-border hidden border-r bg-[var(--public-panel-strong)] p-4 lg:block">
            {scenario.modules.map((module) => (
              <div key={module} className="mb-2 rounded-xl bg-[var(--public-panel)] px-3 py-2 text-xs font-semibold text-[var(--public-text)]">
                {module}
              </div>
            ))}
          </aside>
          <div className="p-5">
            <AnimatePresence mode="wait">
              <motion.div key={scenario.title} initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -14 }}>
                <p className="public-muted text-xs uppercase tracking-[0.22em]">{scenario.eyebrow}</p>
                <div className="mt-2 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <h3 className="public-heading text-2xl font-semibold">{scenario.title}</h3>
                  <span className="rounded-full bg-emerald-400/12 px-3 py-1 text-xs font-semibold text-emerald-500">{scenario.status}</span>
                </div>
                <p className="public-muted mt-3 max-w-xl text-sm leading-6">{scenario.narrative}</p>
                <div className="mt-5 grid gap-3 sm:grid-cols-4">
                  {scenario.stages.map((stage, index) => (
                    <button
                      key={stage}
                      type="button"
                      onClick={() => setActive(index % homeScenarios.length)}
                      className="public-border rounded-2xl border bg-[var(--public-panel-strong)] p-3 text-left transition hover:-translate-y-1 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]"
                    >
                      <span className="text-xs font-semibold text-[var(--public-accent)]">0{index + 1}</span>
                      <p className="public-heading mt-2 text-sm font-semibold">{stage}</p>
                    </button>
                  ))}
                </div>
                <div className="public-border mt-5 rounded-2xl border bg-[var(--public-bg-soft)] p-4">
                  <div className="mb-4 flex items-center justify-between gap-3">
                    <div className="h-2 flex-1 overflow-hidden rounded-full bg-[var(--public-panel)]">
                      <motion.div key={scenario.metric} initial={{ width: '18%' }} animate={{ width: scenario.metric.includes('%') ? scenario.metric : '82%' }} className="h-full rounded-full bg-gradient-to-r from-emerald-400 via-violet-400 to-fuchsia-400" />
                    </div>
                    <span className="text-xs font-semibold text-[var(--public-accent)]">{scenario.metric}</span>
                  </div>
                  <div className="flex h-32 items-end gap-2 sm:h-44">
                    {scenario.bars.map((height, index) => (
                      <motion.div key={index} initial={{ height: 12 }} animate={{ height: `${height}%` }} transition={{ duration: 0.6, delay: index * 0.04 }} className="flex-1 rounded-t-xl bg-gradient-to-t from-violet-800 via-fuchsia-500 to-emerald-300" />
                    ))}
                  </div>
                </div>
                <div className="mt-5 grid gap-3 sm:grid-cols-2">
                  {scenario.activity.slice(0, 4).map((item) => (
                    <div key={item} className="rounded-2xl bg-[var(--public-panel-strong)] p-4 text-sm">
                      <CheckCircle2 className="mb-2 text-emerald-500" size={17} />
                      {item}
                    </div>
                  ))}
                </div>
              </motion.div>
            </AnimatePresence>
          </div>
        </div>
      </MacFrame>
      <div className="mt-4 grid gap-2 sm:grid-cols-3">
        {homeScenarios.map((item, index) => (
          <button
            key={item.title}
            type="button"
            onClick={() => setActive(index)}
            className={`public-border rounded-2xl border px-4 py-3 text-left text-sm transition hover:-translate-y-1 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${active === index ? 'bg-[var(--public-panel-strong)]' : 'bg-[var(--public-panel)]'}`}
          >
            <span className="public-muted text-xs uppercase">{item.eyebrow}</span>
            <p className="public-heading mt-1 font-semibold">{item.title}</p>
          </button>
        ))}
      </div>
    </div>
  );
}

function FeatureArchitecture({ active, setActive }: { active: number; setActive: (index: number) => void }) {
  const features = marketingPages.features.features;
  return (
    <div className="public-panel relative min-h-[500px] overflow-hidden rounded-[1.8rem] p-5">
      <WindowHeader label="Interactive architecture" />
      <div className="relative mt-8 grid min-h-[385px] place-items-center">
        <motion.div animate={{ rotate: 360 }} transition={{ duration: 26, repeat: Infinity, ease: 'linear' }} className="absolute h-72 w-72 rounded-full border border-dashed border-fuchsia-400/40" />
        <div className="public-panel z-10 grid h-36 w-36 place-items-center rounded-full text-center">
          <ShieldCheck className="text-[var(--public-accent)]" size={26} />
          <p className="public-heading text-sm font-semibold">Governed runtime</p>
        </div>
        {features.map((feature, index) => {
          const Icon = feature.icon;
          const angle = (index / features.length) * Math.PI * 2;
          return (
            <button
              key={feature.title}
              type="button"
              onClick={() => setActive(index)}
              className={`public-panel absolute w-36 rounded-2xl p-3 text-center transition focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${active === index ? 'scale-110 border-fuchsia-300/60' : ''}`}
              style={{ transform: `translate(${Math.cos(angle) * 170}px, ${Math.sin(angle) * 125}px)` }}
            >
              <Icon className="mx-auto text-[var(--public-accent)]" size={18} />
              <p className="public-heading mt-2 text-xs font-semibold">{feature.title}</p>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function ModuleSystemMap({ active, setActive, large = false }: { active: number; setActive: (index: number) => void; large?: boolean }) {
  return (
    <div className={`public-panel relative overflow-hidden rounded-[1.8rem] p-5 ${large ? 'min-h-[520px]' : 'min-h-[500px]'}`}>
      <WindowHeader label="Runtime system map" />
      <div className="relative mt-8 grid min-h-[390px] place-items-center">
        <motion.div animate={{ scale: [1, 1.04, 1] }} transition={{ duration: 5, repeat: Infinity }} className="absolute h-72 w-72 rounded-full bg-gradient-to-br from-emerald-400/12 via-violet-400/12 to-rose-400/12" />
        <div className="public-panel z-10 grid h-32 w-32 place-items-center rounded-full text-center">
          <Database className="text-[var(--public-accent)]" size={24} />
          <p className="public-heading text-sm font-semibold">Shared runtime</p>
        </div>
        {studios.map((studio, index) => {
          const Icon = studio.icon;
          const angle = (index / studios.length) * Math.PI * 2;
          return (
            <button
              key={studio.name}
              type="button"
              onClick={() => setActive(index)}
              className={`public-panel absolute w-32 rounded-2xl p-3 text-center transition hover:-translate-y-1 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${active === index ? 'border-fuchsia-300/60 bg-[var(--public-panel-strong)]' : ''}`}
              style={{ transform: `translate(${Math.cos(angle) * 170}px, ${Math.sin(angle) * 130}px)` }}
            >
              <Icon className="mx-auto text-[var(--public-accent)]" size={18} />
              <p className="public-heading mt-2 text-xs font-semibold">{studio.short}</p>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function PricingConsole({ billing, setBilling }: { billing: 'monthly' | 'yearly'; setBilling: (billing: 'monthly' | 'yearly') => void }) {
  return (
    <div className="public-panel overflow-hidden rounded-[1.8rem] p-5">
      <WindowHeader label="Pricing simulator" />
      <div className="mt-7 grid gap-4">
        <div className="inline-flex w-fit rounded-2xl bg-[var(--public-panel-strong)] p-1">
          {(['monthly', 'yearly'] as const).map((mode) => (
            <button key={mode} type="button" onClick={() => setBilling(mode)} className={`rounded-xl px-4 py-2 text-sm font-semibold capitalize ${billing === mode ? 'bg-[var(--public-text)] text-[var(--public-bg)]' : 'public-muted'}`}>
              {mode}
            </button>
          ))}
        </div>
        {marketingPages.pricing.plans.map((plan, index) => (
          <motion.div key={plan.name} whileHover={{ x: 8 }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="public-heading font-semibold">{plan.name}</p>
                <p className="public-muted text-sm">{plan.fit}</p>
              </div>
              <p className="public-heading text-lg font-semibold">{formatPlanPrice(plan.monthly, billing)}</p>
            </div>
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-[var(--public-panel)]">
              <motion.div animate={{ width: `${38 + index * 28}%` }} className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" />
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
}

function AboutStoryVisual() {
  return (
    <div className="public-panel relative min-h-[500px] overflow-hidden rounded-[1.8rem] p-6">
      <div className="absolute inset-y-10 left-1/2 w-px bg-gradient-to-b from-transparent via-fuchsia-400/50 to-transparent" />
      {marketingPages.about.timeline.map((item, index) => (
        <motion.div
          key={item.title}
          animate={{ y: [0, index % 2 ? -8 : 8, 0] }}
          transition={{ duration: 5.5, repeat: Infinity, delay: index * 0.2 }}
          className={`public-panel relative mb-4 w-[84%] rounded-2xl p-4 ${index % 2 ? 'ml-auto' : ''}`}
        >
          <p className="text-xs font-semibold text-[var(--public-accent)]">{item.year}</p>
          <h3 className="public-heading mt-2 font-semibold">{item.title}</h3>
          <p className="public-muted mt-1 text-sm leading-6">{item.body}</p>
        </motion.div>
      ))}
    </div>
  );
}

function ContactRoutingVisual() {
  return (
    <div className="public-panel relative overflow-hidden rounded-[1.8rem] p-5">
      <WindowHeader label="Routing intelligence" />
      <div className="mt-8 grid gap-4">
        {contactRoutes.map((route, index) => {
          const Icon = route.icon;
          return (
            <motion.div key={route.label} animate={{ x: [0, index % 2 ? 14 : -14, 0] }} transition={{ duration: 5 + index * 0.3, repeat: Infinity }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
              <div className="flex items-center gap-3">
                <span className="grid h-11 w-11 place-items-center rounded-2xl bg-fuchsia-400/12 text-[var(--public-accent)]"><Icon size={20} /></span>
                <div>
                  <p className="public-heading font-semibold">{route.label}</p>
                  <p className="public-muted text-sm">{route.path}</p>
                </div>
              </div>
            </motion.div>
          );
        })}
      </div>
      <motion.div animate={{ scale: [1, 1.08, 1] }} transition={{ duration: 2.8, repeat: Infinity }} className="absolute bottom-5 right-5 grid h-24 w-24 place-items-center rounded-full bg-fuchsia-500 text-white shadow-2xl">
        <MousePointerClick size={26} />
      </motion.div>
    </div>
  );
}

function MetricPanel({ metric, index }: { metric: { label: string; value: string; detail: string }; index: number }) {
  return (
    <motion.div whileHover={{ y: -8 }} className="public-panel h-full rounded-[1.35rem] p-6">
      <p className="public-heading text-4xl font-semibold">{metric.value}</p>
      <p className="public-heading mt-4 font-semibold">{metric.label}</p>
      <p className="public-muted mt-2 text-sm leading-6">{metric.detail}</p>
      <motion.div animate={{ width: ['28%', '86%', '42%'] }} transition={{ duration: 4, repeat: Infinity, delay: index * 0.3 }} className="mt-5 h-1 rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" />
    </motion.div>
  );
}

function ScenarioLayerGrid() {
  const layers = marketingPages.home.solutionLayers;
  const [active, setActive] = useState(0);
  const selected = layers[active];
  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <div className="grid gap-3">
        {layers.map((layer, index) => (
          <button key={layer.title} type="button" onClick={() => setActive(index)} className={`public-border rounded-2xl border p-5 text-left transition hover:-translate-y-1 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${active === index ? 'bg-[var(--public-panel-strong)]' : 'bg-[var(--public-panel)]'}`}>
            <div className="flex items-end justify-between gap-3">
              <h3 className="public-heading text-lg font-semibold">{layer.title}</h3>
              <span className="text-2xl font-semibold text-[var(--public-accent)]">{layer.metric}</span>
            </div>
            <p className="public-muted mt-2 text-sm leading-6">{layer.body}</p>
          </button>
        ))}
      </div>
      <motion.div key={selected.title} initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} className="public-panel relative min-h-[360px] overflow-hidden rounded-[1.5rem] p-6">
        <Pill icon={Target}>Expanded operating detail</Pill>
        <h3 className="public-heading mt-5 text-3xl font-semibold">{selected.title}</h3>
        <p className="public-muted mt-4 leading-7">{selected.body}</p>
        <div className="mt-8 grid grid-cols-2 gap-3">
          {selected.items.map((item, index) => (
            <motion.div key={item} whileHover={{ rotate: index % 2 ? -2 : 2, scale: 1.04 }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
              <CheckCircle2 className="text-emerald-500" size={18} />
              <p className="public-heading mt-3 text-sm font-semibold">{item}</p>
            </motion.div>
          ))}
        </div>
      </motion.div>
    </div>
  );
}

function SurfaceLoop({ scenario }: { scenario: (typeof homeScenarios)[number] }) {
  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <div className="public-panel overflow-hidden rounded-[1.4rem] p-5">
        <WindowHeader label="Auto-loop surfaces" />
        <div className="mt-6 flex gap-4 overflow-hidden">
          {[...scenario.modules, ...scenario.modules].map((module, index) => (
            <motion.div key={`${module}-${index}`} animate={{ x: ['0%', '-125%'] }} transition={{ duration: 18, repeat: Infinity, ease: 'linear' }} className="public-border min-w-[220px] rounded-2xl border bg-[var(--public-panel-strong)] p-4">
              <Sparkles className="text-[var(--public-accent)]" size={20} />
              <p className="public-heading mt-4 text-lg font-semibold">{module}</p>
              <p className="public-muted mt-1 text-sm">State changes with launch progress.</p>
            </motion.div>
          ))}
        </div>
      </div>
      <div className="public-panel rounded-[1.4rem] p-6">
        <div className="grid gap-3 sm:grid-cols-2">
          {proofCards.slice(0, 4).map((card, index) => {
            const Icon = card.icon;
            return (
              <motion.div key={card.label} animate={{ y: [0, index % 2 ? 12 : -12, 0] }} transition={{ duration: 5 + index * 0.3, repeat: Infinity }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
                <Icon className="text-[var(--public-accent)]" size={20} />
                <p className="public-heading mt-3 text-xl font-semibold">{card.value}</p>
                <p className="public-muted mt-1 text-sm">{card.label}</p>
              </motion.div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function SignalRibbon({ steps }: { steps: string[] }) {
  return (
    <div className="public-panel overflow-hidden rounded-[1.4rem] p-5">
      <div className="grid gap-3 sm:grid-cols-5">
        {steps.map((step, index) => (
          <motion.div key={step} whileHover={{ y: -6 }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4 text-center">
            <span className="mx-auto grid h-10 w-10 place-items-center rounded-full bg-fuchsia-300 text-sm font-semibold text-purple-950">{index + 1}</span>
            <p className="public-heading mt-3 text-sm font-semibold">{step}</p>
          </motion.div>
        ))}
      </div>
    </div>
  );
}

function ProcessFlow({ steps }: { steps: string[] }) {
  return (
    <div className="public-panel rounded-[1.5rem] p-5">
      <div className="grid gap-3 lg:grid-cols-6">
        {steps.map((step, index) => (
          <motion.div key={step} whileHover={{ y: -8 }} className="relative overflow-hidden rounded-2xl bg-[var(--public-panel-strong)] p-4">
            <span className="text-xs font-semibold text-[var(--public-accent)]">0{index + 1}</span>
            <p className="public-heading mt-3 font-semibold">{step}</p>
            <motion.span animate={{ x: ['-110%', '180%'] }} transition={{ duration: 3.5, repeat: Infinity, delay: index * 0.2 }} className="absolute bottom-0 left-0 h-1 w-20 bg-gradient-to-r from-transparent via-fuchsia-400 to-transparent" />
          </motion.div>
        ))}
      </div>
    </div>
  );
}

function DataFlowRail({ steps }: { steps: string[] }) {
  return (
    <div className="public-panel overflow-hidden rounded-[1.5rem] p-5">
      <div className="grid gap-3 md:grid-cols-3 lg:grid-cols-6">
        {steps.map((step, index) => (
          <motion.div key={step} animate={{ opacity: [0.72, 1, 0.72] }} transition={{ duration: 3, repeat: Infinity, delay: index * 0.18 }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
            <CircleDot className="text-[var(--public-accent)]" size={18} />
            <p className="public-heading mt-3 font-semibold">{step}</p>
            <p className="public-muted mt-1 text-xs">Shared context</p>
          </motion.div>
        ))}
      </div>
    </div>
  );
}

function PricingPlan({ plan, annual, index }: { plan: (typeof marketingPages.pricing.plans)[number]; annual: boolean; index: number }) {
  const featured = 'featured' in plan && plan.featured;
  return (
    <motion.div whileHover={{ y: -10 }} className={`public-panel h-full rounded-[1.5rem] p-6 ${featured ? 'border-fuchsia-300/60 bg-fuchsia-400/[0.08]' : ''}`}>
      {featured ? <span className="rounded-full bg-fuchsia-300 px-3 py-1 text-xs font-semibold text-purple-950">Most popular</span> : null}
      <h3 className="public-heading mt-4 text-2xl font-semibold">{plan.name}</h3>
      <p className="public-muted mt-2 text-sm">{plan.desc}</p>
      <p className="public-heading mt-6 text-4xl font-semibold">{formatPlanPrice(plan.monthly, annual ? 'yearly' : 'monthly')}</p>
      {plan.monthly && annual ? <p className="mt-2 text-sm font-semibold text-emerald-500">20% annual savings included</p> : null}
      <div className="mt-6 grid gap-3">
        {plan.features.map((feature, featureIndex) => (
          <motion.p key={feature} initial={{ opacity: 0, x: -10 }} whileInView={{ opacity: 1, x: 0 }} transition={{ delay: featureIndex * 0.04 + index * 0.03 }} className="public-muted flex gap-2 text-sm">
            <CheckCircle2 className="mt-0.5 shrink-0 text-[var(--public-accent)]" size={16} />
            {feature}
          </motion.p>
        ))}
      </div>
    </motion.div>
  );
}

function RoiVisualizer() {
  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <div className="public-panel rounded-[1.4rem] p-6">
        <Pill icon={LineChart}>ROI visualization</Pill>
        <h3 className="public-heading mt-5 text-2xl font-semibold">Less coordination waste, more accountable launches.</h3>
        <p className="public-muted mt-4 leading-7">Legent reduces unclear handoffs by making readiness, approval, provider health, and performance signals part of the same workspace.</p>
      </div>
      <div className="public-panel rounded-[1.4rem] p-6">
        {marketingPages.pricing.roi.map((item, index) => (
          <div key={item.label} className="mb-5 last:mb-0">
            <div className="mb-2 flex items-center justify-between text-sm">
              <span className="public-heading font-semibold">{item.label}</span>
              <span className="text-[var(--public-accent)]">{item.value}</span>
            </div>
            <div className="h-3 overflow-hidden rounded-full bg-[var(--public-panel-strong)]">
              <motion.div initial={{ width: 0 }} whileInView={{ width: `${58 + index * 16}%` }} className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function PrincipleCard({ item, icon: Icon, index }: { item: { title: string; body: string }; icon: LucideIcon; index: number }) {
  return (
    <motion.div whileHover={{ y: -8 }} className="public-panel h-full rounded-[1.4rem] p-6">
      <motion.div animate={{ rotate: [0, 4, -4, 0] }} transition={{ duration: 5, repeat: Infinity, delay: index * 0.25 }} className="grid h-12 w-12 place-items-center rounded-2xl bg-fuchsia-400/12 text-[var(--public-accent)]">
        <Icon size={22} />
      </motion.div>
      <h3 className="public-heading mt-5 text-lg font-semibold">{item.title}</h3>
      <p className="public-muted mt-3 text-sm leading-6">{item.body}</p>
    </motion.div>
  );
}

function SupportBlock({ card, icon: Icon, index }: { card: { title: string; body: string }; icon: LucideIcon; index: number }) {
  return (
    <motion.div whileHover={{ x: 8 }} className="public-panel rounded-[1.3rem] p-5">
      <Icon className="text-[var(--public-accent)]" size={22} />
      <h3 className="public-heading mt-4 font-semibold">{card.title}</h3>
      <p className="public-muted mt-2 text-sm leading-6">{card.body}</p>
      <motion.div animate={{ width: ['20%', '78%', '34%'] }} transition={{ duration: 4, repeat: Infinity, delay: index * 0.3 }} className="mt-5 h-1 rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" />
    </motion.div>
  );
}

function PublicContactForm({ interests }: { interests: string[] }) {
  const [form, setForm] = useState({ name: '', workEmail: '', company: '', interest: interests[0] ?? '', message: '' });
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const updateField = (key: keyof typeof form, value: string) => setForm((current) => ({ ...current, [key]: value }));

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoading(true);
    setStatus(null);
    try {
      const response = await post<{ id: string; status: string; message: string }>('/public/contact', { ...form, sourcePage: 'contact' });
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
    <motion.div whileHover={{ y: -4 }} className="public-panel rounded-[1.5rem] p-6 md:p-7">
      <form className="grid gap-4" onSubmit={submit}>
        <div className="grid gap-4 md:grid-cols-2">
          <PublicInput label="Name" value={form.name} onChange={(value) => updateField('name', value)} placeholder="Ada Lovelace" />
          <PublicInput label="Work email" type="email" required value={form.workEmail} onChange={(value) => updateField('workEmail', value)} placeholder="ada@company.com" />
        </div>
        <PublicInput label="Company" required value={form.company} onChange={(value) => updateField('company', value)} placeholder="Company name" />
        <label className="grid gap-2 text-sm font-medium text-[var(--public-text)]">
          Interest
          <select value={form.interest} onChange={(event) => updateField('interest', event.target.value)} className="public-field">
            {interests.map((interest) => <option key={interest} value={interest}>{interest}</option>)}
          </select>
        </label>
        <label className="grid gap-2 text-sm font-medium text-[var(--public-text)]">
          Message
          <textarea required rows={6} value={form.message} onChange={(event) => updateField('message', event.target.value)} placeholder="Tell us about your rollout, provider setup, migration, or review." className="public-field min-h-36 py-3" />
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
    </motion.div>
  );
}

function PublicInput({ label, value, onChange, placeholder, type = 'text', required = false }: { label: string; value: string; onChange: (value: string) => void; placeholder: string; type?: string; required?: boolean }) {
  return (
    <label className="grid gap-2 text-sm font-medium text-[var(--public-text)]">
      {label}
      <input type={type} required={required} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} className="public-field" />
    </label>
  );
}

function ComparisonTable({ rows }: { rows: ReadonlyArray<{ feature: string; launch: string; scale: string; enterprise: string }> }) {
  return (
    <div className="public-panel overflow-x-auto rounded-[1.3rem]">
      <table className="w-full min-w-[760px] text-left text-sm">
        <thead className="bg-[var(--public-panel-strong)]">
          <tr>{['Feature', 'Launch', 'Scale', 'Enterprise'].map((heading) => <th key={heading} className="public-heading px-5 py-4 font-semibold">{heading}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.feature} className="public-border border-t transition hover:bg-[var(--public-panel-strong)]">
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

function MacFrame({ children }: { children: ReactNode }) {
  return (
    <div className="public-panel relative overflow-hidden rounded-[1.7rem] shadow-[0_28px_100px_rgba(55,18,105,0.20)]">
      <WindowHeader label="Live workspace" />
      {children}
    </div>
  );
}

function WindowHeader({ label }: { label: string }) {
  return (
    <div className="public-border flex items-center justify-between border-b px-5 py-4">
      <WindowDots />
      <span className="rounded-full border border-fuchsia-300/25 bg-fuchsia-300/10 px-3 py-1 text-xs font-semibold text-[var(--public-accent)]">{label}</span>
    </div>
  );
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

function Pill({ icon: Icon, children }: { icon: LucideIcon; children: ReactNode }) {
  return (
    <span className="public-border inline-flex items-center gap-2 rounded-full border bg-[var(--public-panel)] px-3 py-1 text-xs font-semibold uppercase tracking-normal text-[var(--public-text)]">
      <Icon size={14} className="text-[var(--public-accent)]" />
      {children}
    </span>
  );
}

function PublicLinkButton({ href, children, icon, variant = 'primary' }: { href: string; children: ReactNode; icon?: ReactNode; variant?: 'primary' | 'secondary' }) {
  return (
    <Link href={href} className={`${variant === 'primary' ? 'public-button-primary' : 'public-button-secondary'} inline-flex items-center justify-center gap-2 rounded-xl px-5 py-3 text-base font-semibold transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] active:translate-y-0`}>
      {icon}
      {children}
    </Link>
  );
}

function FinalCta({ title, body }: { title: string; body: string }) {
  return (
    <section className="mx-auto max-w-7xl px-4 pb-20 sm:px-6" data-gsap-reveal>
      <div className="public-panel overflow-hidden rounded-[1.6rem] p-6 md:p-9">
        <div className="grid gap-6 md:grid-cols-[1fr_auto] md:items-center">
          <div>
            <Pill icon={Zap}>Ready</Pill>
            <h2 className="public-heading mt-4 max-w-3xl text-2xl font-semibold md:text-4xl">{title}</h2>
            <p className="public-muted mt-3 max-w-2xl leading-7">{body}</p>
          </div>
          <div className="flex flex-wrap gap-3">
            <PublicLinkButton href="/signup">Create Workspace</PublicLinkButton>
            <PublicLinkButton href="/contact" variant="secondary">Talk to Team</PublicLinkButton>
          </div>
        </div>
      </div>
    </section>
  );
}

function formatPlanPrice(monthly: number | null, billing: 'monthly' | 'yearly') {
  if (!monthly) return 'Custom INR';
  if (billing === 'monthly') return `INR ${monthly.toLocaleString('en-IN')}`;
  const annualTotal = Math.round(monthly * 12 * 0.8);
  return `INR ${annualTotal.toLocaleString('en-IN')}/yr`;
}

function normalizePageKey(pageKey: string): MarketingPageKey {
  if (pageKey === 'features' || pageKey === 'modules' || pageKey === 'pricing' || pageKey === 'about' || pageKey === 'contact') return pageKey;
  return 'home';
}
