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
        <div className="public-home-hero-shell public-home-hero-grid mx-auto max-w-7xl px-4 sm:px-6 lg:grid-cols-[0.92fr_1.08fr]">
          <motion.div initial="hidden" animate="visible" variants={fadeUp} transition={{ duration: 0.55 }} className="min-w-0">
            <Pill icon={Sparkles}>{page.eyebrow}</Pill>
            <h1 className="public-heading mt-4 max-w-3xl text-balance text-[clamp(2.65rem,8vw,4.8rem)] font-semibold leading-[0.98] tracking-normal lg:text-[clamp(3.1rem,4.2vw,4.8rem)]">
              {page.title}
            </h1>
            <p className="public-muted mt-5 max-w-2xl text-base leading-7 sm:text-lg">{page.subtitle}</p>
            <div className="mt-5 flex flex-wrap gap-3">
              <PublicLinkButton href="/signup" icon={<ArrowRight size={18} />}>{page.primaryCta}</PublicLinkButton>
              <PublicLinkButton href="/modules" variant="secondary">{page.secondaryCta}</PublicLinkButton>
            </div>
          </motion.div>
          <HomeDashboardTheater active={activeScenario} setActive={setActiveScenario} />
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 pb-6 sm:px-6 md:pb-8" data-gsap-reveal>
        <HomeProofRail highlights={[...page.highlights]} scenario={scenario} />
      </section>

      <MarketingSection eyebrow="Operating proof" title="Measurable lift across launch, delivery, and team velocity.">
        <div className="grid gap-4 md:grid-cols-3">
          {page.metrics.map((metric, index) => (
            <MetricPanel key={metric.label} metric={metric} index={index} />
          ))}
        </div>
      </MarketingSection>

      <section className="public-bleed-band" data-gsap-reveal>
        <div className="mx-auto grid max-w-7xl gap-8 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:items-center">
          <div>
            <Pill icon={Network}>Signal to outcome</Pill>
            <h2 className="public-heading mt-4 text-balance text-3xl font-semibold md:text-5xl">One operating loop from customer signal to business result.</h2>
            <p className="public-muted mt-5 max-w-xl leading-7">
              Customer behavior, AI guidance, approval evidence, provider risk, and revenue feedback move through one visible workflow instead of disconnected tools.
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
        visual={<StudioFabricVisual active={active} setActive={setActive} />}
      />
      <MarketingSection eyebrow="Architecture map" title="Select a module and watch its relationships change.">
        <div className="grid gap-6 lg:grid-cols-2">
          <ModuleRelationshipExplorer active={active} setActive={setActive} />
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
                <motion.div style={{ originX: 0 }} animate={{ scaleX: [0.24, 0.86, 0.42] }} transition={{ duration: 4, repeat: Infinity, delay: index * 0.3 }} className="mt-6 h-1 w-full rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" />
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
      <div className="public-hero-shell public-hero-grid mx-auto max-w-7xl px-4 sm:px-6 lg:grid-cols-[0.94fr_1.06fr]">
        <motion.div initial="hidden" animate="visible" variants={fadeUp} transition={{ duration: 0.55 }}>
          <Pill icon={Sparkles}>{eyebrow}</Pill>
          <h1 className="public-heading mt-4 max-w-4xl text-balance text-4xl font-semibold leading-[1.04] sm:text-5xl lg:text-6xl">{title}</h1>
          <p className="public-muted mt-5 max-w-2xl text-base leading-7 sm:text-lg">{subtitle}</p>
          <div className="mt-6 flex flex-wrap gap-3">
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
    <section className="mx-auto max-w-7xl px-4 py-12 sm:px-6 md:py-16" data-gsap-reveal>
      <Pill icon={Sparkles}>{eyebrow}</Pill>
      <h2 className="public-heading mt-4 max-w-3xl text-balance text-3xl font-semibold md:text-5xl">{title}</h2>
      <div className="mt-7 md:mt-9">{children}</div>
    </section>
  );
}

function VisualTexture() {
  return (
    <div className="public-visual-clip">
      <div className="public-mock-bitmap" aria-hidden="true" />
    </div>
  );
}

function HomeProofRail({ highlights, scenario }: { highlights: string[]; scenario: (typeof homeScenarios)[number] }) {
  return (
    <div className="public-panel grid gap-3 rounded-[1.35rem] p-3 md:grid-cols-[1.1fr_0.9fr_0.9fr_0.8fr] md:items-center">
      {highlights.map((item, index) => (
        <motion.div key={item} whileHover={{ y: -3 }} className="flex min-h-16 items-center gap-3 rounded-2xl bg-[var(--public-panel-strong)] px-4 py-3">
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-fuchsia-400/12 text-[var(--public-accent)]">
            <CheckCircle2 size={16} />
          </span>
          <p className="public-heading text-sm font-semibold leading-5">{item}</p>
        </motion.div>
      ))}
      <motion.div animate={{ opacity: [0.78, 1, 0.78] }} transition={{ duration: 3.5, repeat: Infinity }} className="flex min-h-16 items-center justify-between gap-3 rounded-2xl bg-[var(--public-text)] px-4 py-3 text-[var(--public-bg)]">
        <span>
          <span className="block text-xs uppercase opacity-70">Active scenario</span>
          <span className="block text-sm font-semibold">{scenario.eyebrow}</span>
        </span>
        <span className="text-lg font-semibold">{scenario.metric}</span>
      </motion.div>
    </div>
  );
}

function HomeDashboardTheater({ active, setActive }: { active: number; setActive: (index: number) => void }) {
  const scenario = homeScenarios[active];
  return (
    <div className="public-home-theater relative" data-gsap-parallax="-3">
      <motion.div className="public-panel absolute bottom-4 left-3 z-20 hidden w-40 rounded-2xl p-3 shadow-2xl xl:block" animate={{ y: [0, 10, 0] }} transition={{ duration: 6, repeat: Infinity }}>
        <p className="public-muted text-xs uppercase">Live activity</p>
        <p className="public-heading mt-1 text-lg font-semibold">{scenario.metric}</p>
      </motion.div>
      <motion.div className="public-panel absolute right-3 top-14 z-20 hidden w-36 rounded-2xl p-3 shadow-2xl xl:block" animate={{ y: [0, -10, 0] }} transition={{ duration: 7, repeat: Infinity }}>
        <p className="public-muted text-xs uppercase">Status</p>
        <p className="public-heading mt-1 text-base font-semibold">{scenario.status}</p>
      </motion.div>
      <MacFrame className="h-full">
        <VisualTexture />
        <div className="relative grid h-[calc(100%-57px)] min-h-0 lg:grid-cols-[132px_1fr]">
          <aside className="public-border hidden min-h-0 border-r bg-[var(--public-panel-strong)] p-3 lg:block">
            {scenario.modules.map((module) => (
              <div key={module} className="mb-2 rounded-xl bg-[var(--public-panel)] px-3 py-2 text-xs font-semibold text-[var(--public-text)]">
                {module}
              </div>
            ))}
          </aside>
          <div className="min-h-0 overflow-hidden p-4">
            <AnimatePresence mode="wait">
              <motion.div key={scenario.title} initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -14 }} className="flex h-full min-h-0 flex-col">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="public-muted text-xs uppercase tracking-[0.18em]">{scenario.eyebrow}</p>
                    <h3 className="public-heading mt-1 text-xl font-semibold leading-tight sm:text-2xl">{scenario.title}</h3>
                  </div>
                  <span className="shrink-0 rounded-full bg-emerald-400/12 px-3 py-1 text-xs font-semibold text-emerald-500">{scenario.status}</span>
                </div>
                <p className="public-muted mt-2 hidden max-w-xl text-sm leading-6 sm:block">{scenario.narrative}</p>
                <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
                  {scenario.stages.map((stage, index) => (
                    <button
                      key={stage}
                      type="button"
                      onClick={() => setActive(index % homeScenarios.length)}
                      className="public-border rounded-xl border bg-[var(--public-panel-strong)] p-2 text-left transition hover:-translate-y-1 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]"
                    >
                      <span className="text-[11px] font-semibold text-[var(--public-accent)]">0{index + 1}</span>
                      <p className="public-heading mt-1 text-xs font-semibold leading-4">{stage}</p>
                    </button>
                  ))}
                </div>
                <div className="mt-3 hidden min-h-0 flex-1 gap-3 sm:grid sm:grid-cols-[1.05fr_0.95fr]">
                  <div className="public-border rounded-2xl border bg-[var(--public-bg-soft)] p-3">
                    <div className="mb-3 flex items-center justify-between gap-3">
                      <div className="h-2 flex-1 overflow-hidden rounded-full bg-[var(--public-panel)]">
                        <motion.div key={scenario.metric} initial={{ width: '18%' }} animate={{ width: scenario.metric.includes('%') ? scenario.metric : '82%' }} className="h-full rounded-full bg-gradient-to-r from-emerald-400 via-violet-400 to-fuchsia-400" />
                      </div>
                      <span className="text-xs font-semibold text-[var(--public-accent)]">{scenario.metric}</span>
                    </div>
                    <div className="flex h-24 items-end gap-2">
                      {scenario.bars.map((height, index) => (
                        <motion.div key={index} initial={{ height: 12 }} animate={{ height: `${height}%` }} transition={{ duration: 0.6, delay: index * 0.04 }} className="flex-1 rounded-t-lg bg-gradient-to-t from-violet-800 via-fuchsia-500 to-emerald-300" />
                      ))}
                    </div>
                  </div>
                  <div className="grid content-start gap-2">
                    {scenario.activity.slice(0, 2).map((item) => (
                      <div key={item} className="rounded-2xl bg-[var(--public-panel-strong)] p-3 text-xs leading-5">
                        <CheckCircle2 className="mb-1 text-emerald-500" size={15} />
                        {item}
                      </div>
                    ))}
                    <div className="hidden gap-2 sm:flex">
                      {homeScenarios.map((item, index) => (
                        <button
                          key={item.title}
                          type="button"
                          aria-label={`Show ${item.title}`}
                          onClick={() => setActive(index)}
                          className={`public-border grid h-8 flex-1 place-items-center rounded-xl border text-xs font-semibold transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${active === index ? 'bg-[var(--public-panel-strong)] text-[var(--public-accent)]' : 'bg-[var(--public-panel)] text-[var(--public-text)]'}`}
                        >
                          {index + 1}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </motion.div>
            </AnimatePresence>
          </div>
        </div>
      </MacFrame>
    </div>
  );
}

function FeatureArchitecture({ active, setActive }: { active: number; setActive: (index: number) => void }) {
  const features = marketingPages.features.features;
  const activeFeature = features[active];
  const ActiveIcon = activeFeature.icon;
  return (
    <div className="public-panel public-hero-visual public-art-glow relative rounded-[1.8rem] p-5">
      <VisualTexture />
      <WindowHeader label="Interactive architecture" />
      <div className="relative mt-5 grid gap-4">
        <div className="public-panel relative rounded-[1.4rem] p-4">
          <div className="absolute left-8 right-8 top-1/2 hidden h-px bg-gradient-to-r from-emerald-400/40 via-[var(--public-accent)] to-blue-400/40 sm:block" />
          <div className="relative grid gap-3 sm:grid-cols-3">
            {features.map((feature, index) => {
              const Icon = feature.icon;
              const selected = active === index;
              return (
                <button
                  key={feature.title}
                  type="button"
                  onClick={() => setActive(index)}
                  className={`public-border relative min-h-24 rounded-2xl border p-3 text-left transition hover:-translate-y-1 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${selected ? 'bg-[var(--public-text)] text-[var(--public-bg)]' : 'bg-[var(--public-panel-strong)] text-[var(--public-text)]'}`}
                >
                  <motion.span animate={{ scaleX: selected ? [0.32, 1, 0.52] : 0.22 }} transition={{ duration: 3, repeat: Infinity, delay: index * 0.08 }} className="absolute inset-x-3 bottom-2 h-1 origin-left rounded-full bg-gradient-to-r from-emerald-400 via-fuchsia-500 to-blue-400" />
                  <Icon className={selected ? 'text-[var(--public-bg)]' : 'text-[var(--public-accent)]'} size={18} />
                  <p className={`mt-3 text-sm font-semibold leading-5 ${selected ? '' : 'public-heading'}`}>{feature.title}</p>
                </button>
              );
            })}
          </div>
        </div>
        <motion.div key={activeFeature.title} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="public-panel rounded-[1.4rem] p-5">
          <div className="flex items-start gap-4">
            <span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-fuchsia-400/12 text-[var(--public-accent)]"><ActiveIcon size={22} /></span>
            <div>
              <p className="public-muted text-xs uppercase tracking-[0.18em]">Signal spine</p>
              <h3 className="public-heading mt-1 text-2xl font-semibold">{activeFeature.title}</h3>
              <p className="public-muted mt-2 text-sm leading-6">{activeFeature.body}</p>
            </div>
          </div>
          <div className="mt-4 grid gap-2 sm:grid-cols-3">
            {['Policy', 'Evidence', 'Outcome'].map((item, index) => (
              <motion.div key={item} animate={{ opacity: [0.7, 1, 0.7] }} transition={{ duration: 3.4, repeat: Infinity, delay: index * 0.16 }} className="rounded-2xl bg-[var(--public-panel-strong)] p-3 text-sm font-semibold text-[var(--public-text)]">
                {item}
              </motion.div>
            ))}
          </div>
        </motion.div>
      </div>
    </div>
  );
}

function StudioFabricVisual({ active, setActive }: { active: number; setActive: (index: number) => void }) {
  const selected = studios[active];
  return (
    <div className="public-panel public-hero-visual public-art-glow relative rounded-[1.8rem] p-5">
      <VisualTexture />
      <WindowHeader label="Studio fabric" />
      <div className="relative mt-5 grid gap-4">
        <div className="public-panel relative rounded-[1.35rem] p-4">
          <svg className="pointer-events-none absolute inset-0 h-full w-full text-[var(--public-accent)]" viewBox="0 0 640 220" aria-hidden="true" preserveAspectRatio="none">
            {[
              'M 70 62 C 250 96, 280 110, 320 110',
              'M 145 62 C 250 96, 280 110, 320 110',
              'M 220 62 C 250 96, 280 110, 320 110',
              'M 420 158 C 390 124, 360 110, 320 110',
              'M 495 158 C 390 124, 360 110, 320 110',
              'M 570 158 C 390 124, 360 110, 320 110',
            ].map((path, index) => (
              <motion.path
                key={path}
                d={path}
                fill="none"
                stroke="currentColor"
                strokeWidth="1.4"
                strokeDasharray="7 8"
                initial={{ pathLength: 0.2, opacity: 0.18 }}
                animate={{ pathLength: [0.25, 1, 0.45], opacity: [0.18, 0.52, 0.24] }}
                transition={{ duration: 4.2, repeat: Infinity, delay: index * 0.14 }}
              />
            ))}
          </svg>
          <div className="relative grid gap-2 sm:grid-cols-3">
            {studios.map((studio, index) => {
              const Icon = studio.icon;
              const selectedState = active === index;
              return (
                <button
                  key={studio.name}
                  type="button"
                  onClick={() => setActive(index)}
                  className={`public-border relative overflow-hidden rounded-2xl border bg-[var(--public-panel-strong)] p-3 text-left transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${selectedState ? 'border-[var(--public-accent)] bg-[var(--public-panel)]' : ''}`}
                >
                  <motion.span animate={{ scaleX: selectedState ? [0.4, 1, 0.65] : [0.2, 0.42, 0.2] }} transition={{ duration: 3.2, repeat: Infinity, delay: index * 0.12 }} className="absolute bottom-0 left-0 h-1 w-full origin-left bg-gradient-to-r from-emerald-400 via-fuchsia-500 to-blue-400" />
                  <div className="flex items-center gap-3">
                    <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-fuchsia-400/12 text-[var(--public-accent)]"><Icon size={18} /></span>
                    <div>
                      <p className="public-heading text-sm font-semibold">{studio.short}</p>
                      <p className="public-muted text-xs">{studio.proof}</p>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>
        <div className="public-panel rounded-[1.4rem] p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="public-muted text-xs uppercase tracking-[0.18em]">Shared runtime</p>
              <h3 className="public-heading mt-1 text-2xl font-semibold">{selected.name}</h3>
            </div>
            <span className="rounded-full bg-emerald-400/12 px-3 py-1 text-xs font-semibold text-emerald-500">Context synced</span>
          </div>
          <div className="mt-5 grid grid-cols-3 gap-2">
            {['Roles', 'Config', 'Audit'].map((item, index) => (
              <motion.div key={item} animate={{ y: [0, index % 2 ? 6 : -6, 0] }} transition={{ duration: 4.4 + index * 0.2, repeat: Infinity }} className="rounded-2xl bg-[var(--public-panel-strong)] p-3 text-center">
                <p className="public-heading text-sm font-semibold">{item}</p>
                <p className="public-muted mt-1 text-[11px]">live</p>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function ModuleRelationshipExplorer({ active, setActive }: { active: number; setActive: (index: number) => void }) {
  const selected = studios[active];
  const before = studios[(active + studios.length - 1) % studios.length];
  const after = studios[(active + 1) % studios.length];
  return (
    <div className="public-panel public-art-glow relative rounded-[1.8rem] p-5">
      <VisualTexture />
      <WindowHeader label="Relationship explorer" />
      <div className="relative mt-5 grid gap-4">
        <div className="public-panel relative rounded-[1.35rem] p-4">
          <svg className="pointer-events-none absolute inset-0 h-full w-full text-[var(--public-accent)]" viewBox="0 0 640 180" aria-hidden="true" preserveAspectRatio="none">
            <motion.path d="M 70 90 C 190 30, 250 30, 320 90 C 390 150, 450 150, 570 90" fill="none" stroke="currentColor" strokeWidth="1.6" strokeDasharray="9 8" animate={{ pathLength: [0.2, 1, 0.4], opacity: [0.2, 0.58, 0.25] }} transition={{ duration: 3.8, repeat: Infinity }} />
          </svg>
          <div className="relative grid grid-cols-[1fr_auto_1fr] items-center gap-3">
            {[before, selected, after].map((studio, index) => {
              const Icon = studio.icon;
              const center = index === 1;
              return (
                <motion.button
                  key={studio.name}
                  type="button"
                  onClick={() => setActive(studios.findIndex((item) => item.name === studio.name))}
                  whileHover={{ y: -4 }}
                  className={`public-border rounded-2xl border p-3 text-center focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${center ? 'bg-[var(--public-text)] text-[var(--public-bg)]' : 'bg-[var(--public-panel-strong)] text-[var(--public-text)]'}`}
                >
                  <Icon className={`mx-auto ${center ? 'text-[var(--public-bg)]' : 'text-[var(--public-accent)]'}`} size={20} />
                  <p className={`mt-2 text-xs font-semibold ${center ? '' : 'public-heading'}`}>{studio.short}</p>
                </motion.button>
              );
            })}
          </div>
          <div className="relative mt-4 h-3 overflow-hidden rounded-full bg-[var(--public-panel-strong)]">
            <motion.div animate={{ x: ['-20%', '110%'] }} transition={{ duration: 2.6, repeat: Infinity, ease: 'linear' }} className="absolute inset-y-0 left-0 w-1/3 rounded-full bg-gradient-to-r from-transparent via-[var(--public-accent)] to-transparent" />
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          {selected.capabilities.slice(0, 4).map((capability, index) => (
            <motion.div key={capability} animate={{ opacity: [0.75, 1, 0.75] }} transition={{ duration: 3, repeat: Infinity, delay: index * 0.16 }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
              <CircleDot className="text-[var(--public-accent)]" size={16} />
              <p className="public-heading mt-2 text-sm font-semibold">{capability}</p>
            </motion.div>
          ))}
        </div>
      </div>
    </div>
  );
}

function PricingConsole({ billing, setBilling }: { billing: 'monthly' | 'yearly'; setBilling: (billing: 'monthly' | 'yearly') => void }) {
  const workload = billing === 'yearly' ? ['12 launches', '5 workspaces', '20% saved'] : ['1 launch', '1 workspace', 'monthly control'];
  return (
    <div className="public-panel public-hero-visual public-art-glow relative rounded-[1.8rem] p-5">
      <VisualTexture />
      <WindowHeader label="Value model" />
      <div className="relative mt-5 grid gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="public-muted text-xs uppercase tracking-[0.18em]">Billing signal</p>
            <h3 className="public-heading mt-1 text-2xl font-semibold">{billing === 'yearly' ? 'Annual scale posture' : 'Monthly launch posture'}</h3>
          </div>
          <div className="inline-flex rounded-2xl bg-[var(--public-panel-strong)] p-1">
            {(['monthly', 'yearly'] as const).map((mode) => (
              <button key={mode} type="button" onClick={() => setBilling(mode)} className={`rounded-xl px-4 py-2 text-sm font-semibold capitalize ${billing === mode ? 'bg-[var(--public-text)] text-[var(--public-bg)]' : 'public-muted'}`}>
                {mode}
              </button>
            ))}
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          {workload.map((item, index) => (
            <motion.div key={item} animate={{ y: [0, index % 2 ? 7 : -7, 0] }} transition={{ duration: 4.4 + index * 0.2, repeat: Infinity }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
              <p className="public-heading text-sm font-semibold">{item}</p>
              <p className="public-muted mt-1 text-xs">capacity input</p>
            </motion.div>
          ))}
        </div>
        <div className="public-panel rounded-[1.4rem] p-5">
          {marketingPages.pricing.roi.map((item, index) => (
            <div key={item.label} className="mb-4 last:mb-0">
              <div className="mb-2 flex items-center justify-between gap-3 text-sm">
                <span className="public-heading font-semibold">{item.label}</span>
                <span className="text-[var(--public-accent)]">{item.value}</span>
              </div>
              <div className="h-3 overflow-hidden rounded-full bg-[var(--public-panel-strong)]">
                <motion.div style={{ originX: 0 }} animate={{ scaleX: billing === 'yearly' ? 0.72 + index * 0.1 : 0.48 + index * 0.12 }} className="h-full w-full rounded-full bg-gradient-to-r from-emerald-400 via-[var(--public-accent)] to-blue-400" />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function AboutStoryVisual() {
  return (
    <div className="public-panel public-hero-visual public-art-glow relative rounded-[1.8rem] p-6">
      <VisualTexture />
      <WindowHeader label="Company story" />
      <div className="relative mt-5">
        <div className="rounded-[1.4rem] bg-[var(--public-panel-strong)] p-5">
          <p className="public-muted text-xs uppercase tracking-[0.18em]">Mission line</p>
          <h3 className="public-heading mt-2 text-2xl font-semibold">Make messaging operations visible, governed, and measurable.</h3>
        </div>
        <div className="relative mt-6">
          <div className="absolute left-4 right-4 top-5 hidden h-px bg-gradient-to-r from-emerald-400 via-[var(--public-accent)] to-blue-400 sm:block" />
          <div className="relative grid gap-3 sm:grid-cols-4">
          {marketingPages.about.timeline.map((item, index) => (
            <motion.div
              key={item.title}
              animate={{ y: [0, index % 2 ? -5 : 5, 0] }}
              transition={{ duration: 5.2, repeat: Infinity, delay: index * 0.18 }}
              className="rounded-2xl bg-[var(--public-panel)] p-4"
            >
              <span className="grid h-9 w-9 place-items-center rounded-full bg-[var(--public-accent)] text-xs font-semibold text-white">{item.year}</span>
              <h3 className="public-heading mt-3 text-sm font-semibold leading-5">{item.title}</h3>
              <p className="public-muted mt-1 text-xs leading-5">{item.body}</p>
            </motion.div>
          ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function ContactRoutingVisual() {
  return (
    <div className="public-panel public-hero-visual public-art-glow relative rounded-[1.8rem] p-5">
      <VisualTexture />
      <WindowHeader label="Support routing" />
      <div className="relative mt-5 grid gap-4 sm:grid-cols-[0.9fr_1.1fr] sm:items-center">
        <div className="public-panel relative grid min-h-64 place-items-center overflow-hidden rounded-[1.4rem] p-5 text-center">
          <motion.div animate={{ scale: [1, 1.08, 1] }} transition={{ duration: 5.5, repeat: Infinity }} className="absolute h-40 w-40 rounded-full border border-[var(--public-accent)]/30" />
          <motion.div animate={{ rotate: [0, 360] }} transition={{ duration: 16, repeat: Infinity, ease: 'linear' }} className="absolute h-52 w-52 rounded-full border border-dashed border-emerald-400/35" />
          <div className="relative">
            <MousePointerClick className="mx-auto text-[var(--public-accent)]" size={28} />
            <h3 className="public-heading mt-3 text-2xl font-semibold">Route request to right operator</h3>
            <p className="public-muted mt-1 text-sm">Architecture, delivery, migration, or security review.</p>
          </div>
        </div>
        <div className="grid gap-3">
        {contactRoutes.map((route, index) => {
          const Icon = route.icon;
          return (
            <motion.div key={route.label} animate={{ x: [0, index % 2 ? 7 : -7, 0] }} transition={{ duration: 5 + index * 0.3, repeat: Infinity }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="grid h-11 w-11 place-items-center rounded-2xl bg-fuchsia-400/12 text-[var(--public-accent)]"><Icon size={20} /></span>
                  <div>
                    <p className="public-heading font-semibold">{route.label}</p>
                    <p className="public-muted text-sm">{route.path}</p>
                  </div>
                </div>
                <ChevronRight className="text-[var(--public-accent)]" size={16} />
              </div>
            </motion.div>
          );
        })}
        </div>
      </div>
    </div>
  );
}

function MetricPanel({ metric, index }: { metric: { label: string; value: string; detail: string }; index: number }) {
  return (
    <motion.div whileHover={{ y: -8 }} className="public-panel flex h-full min-h-[196px] flex-col rounded-[1.35rem] p-6">
      <div>
        <p className="public-heading flex min-h-12 items-end text-4xl font-semibold leading-none">{metric.value}</p>
        <p className="public-heading mt-4 min-h-6 font-semibold">{metric.label}</p>
        <p className="public-muted mt-2 text-sm leading-5">{metric.detail}</p>
      </div>
      <motion.div style={{ originX: 0 }} animate={{ scaleX: [0.28, 0.86, 0.42] }} transition={{ duration: 4, repeat: Infinity, delay: index * 0.3 }} className="mt-auto h-1 w-full rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" />
    </motion.div>
  );
}

function ScenarioLayerGrid() {
  const layers = marketingPages.home.solutionLayers;
  const [active, setActive] = useState(0);
  const selected = layers[active];
  return (
    <div className="grid gap-6 lg:grid-cols-[0.86fr_1.14fr] lg:items-stretch">
      <div className="public-panel relative rounded-[1.5rem] p-5">
        <div className="absolute bottom-8 left-[2.05rem] top-8 w-px bg-gradient-to-b from-emerald-400 via-[var(--public-accent)] to-blue-400" />
        {layers.map((layer, index) => (
          <button key={layer.title} type="button" onClick={() => setActive(index)} className="relative mb-5 flex w-full gap-4 text-left last:mb-0 focus:outline-none">
            <span className={`relative z-10 grid h-10 w-10 shrink-0 place-items-center rounded-full border text-sm font-semibold transition ${active === index ? 'border-[var(--public-accent)] bg-[var(--public-accent)] text-white' : 'public-border bg-[var(--public-panel-strong)] text-[var(--public-accent)]'}`}>
              {index + 1}
            </span>
            <span className={`public-border block flex-1 rounded-2xl border px-4 py-3 transition hover:-translate-y-1 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] ${active === index ? 'bg-[var(--public-panel-strong)]' : 'bg-[var(--public-panel)]'}`}>
              <span className="flex items-center justify-between gap-3">
                <span className="public-heading font-semibold">{layer.title}</span>
                <span className="text-lg font-semibold text-[var(--public-accent)]">{layer.metric}</span>
              </span>
              <span className="public-muted mt-1 block text-sm leading-6">{layer.body}</span>
            </span>
          </button>
        ))}
      </div>
      <motion.div key={selected.title} initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} className="public-panel public-art-glow relative overflow-hidden rounded-[1.5rem] p-6">
        <VisualTexture />
        <div className="relative">
          <Pill icon={Target}>Operating model</Pill>
          <div className="mt-5 grid gap-5 md:grid-cols-[1fr_auto] md:items-start">
            <div>
              <h3 className="public-heading text-3xl font-semibold">{selected.title}</h3>
              <p className="public-muted mt-4 max-w-2xl leading-7">{selected.body}</p>
            </div>
            <motion.div animate={{ rotate: [0, 4, -3, 0] }} transition={{ duration: 7, repeat: Infinity }} className="grid h-24 w-24 place-items-center rounded-full bg-[var(--public-text)] text-center text-sm font-semibold text-[var(--public-bg)]">
              {selected.metric}
            </motion.div>
          </div>
        </div>
        <div className="relative mt-8 grid gap-3 sm:grid-cols-3">
          {selected.items.map((item, index) => (
            <motion.div key={item} whileHover={{ y: -6 }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
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
    <div className="public-panel public-art-glow relative overflow-hidden rounded-[1.5rem] p-5">
      <VisualTexture />
      <WindowHeader label="Connected product surfaces" />
      <div className="relative mt-6 grid gap-5 lg:grid-cols-[1.2fr_0.8fr] lg:items-center">
        <div className="relative rounded-[1.4rem] bg-[var(--public-panel-strong)] p-4">
          <div className="absolute left-8 right-8 top-1/2 h-px bg-gradient-to-r from-emerald-400 via-[var(--public-accent)] to-blue-400" />
          <div className="relative grid gap-3 sm:grid-cols-4">
            {scenario.modules.map((module, index) => (
              <motion.div key={module} animate={{ y: [0, index % 2 ? 8 : -8, 0] }} transition={{ duration: 5 + index * 0.25, repeat: Infinity }} className="public-border min-h-28 rounded-2xl border bg-[var(--public-panel)] p-4">
                <Sparkles className="text-[var(--public-accent)]" size={18} />
                <p className="public-heading mt-4 text-sm font-semibold">{module}</p>
                <p className="public-muted mt-1 text-xs">live state</p>
              </motion.div>
            ))}
          </div>
        </div>
        <div className="relative">
          <Pill icon={LineChart}>Outcome panel</Pill>
          <h3 className="public-heading mt-4 text-2xl font-semibold">{scenario.title}</h3>
          <p className="public-muted mt-3 text-sm leading-6">{scenario.narrative}</p>
          <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
          {proofCards.slice(0, 4).map((card, index) => {
            const Icon = card.icon;
            return (
              <motion.div key={card.label} whileHover={{ x: 6 }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
                <Icon className="text-[var(--public-accent)]" size={20} />
                <p className="public-heading mt-3 text-xl font-semibold">{card.value}</p>
                <p className="public-muted mt-1 text-sm">{card.label}</p>
              </motion.div>
            );
          })}
          </div>
        </div>
      </div>
    </div>
  );
}

function SignalRibbon({ steps }: { steps: string[] }) {
  return (
    <div className="public-panel rounded-[1.4rem] p-5">
      <div className="grid gap-3 sm:grid-cols-5 sm:[grid-template-columns:repeat(5,minmax(0,1fr))]">
        {steps.map((step, index) => (
          <motion.div key={step} whileHover={{ y: -6 }} className="rounded-2xl bg-[var(--public-panel-strong)] p-3 text-center sm:min-h-[128px]">
            <span className="mx-auto grid h-10 w-10 place-items-center rounded-full bg-[var(--public-accent)] text-sm font-semibold text-white">{index + 1}</span>
            <p className="public-heading mt-3 break-words text-sm font-semibold leading-5">{step}</p>
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
    <motion.div whileHover={{ y: -10 }} className={`public-panel h-full rounded-[1.5rem] p-6 ${featured ? 'border-[var(--public-accent)] bg-fuchsia-400/[0.08]' : ''}`}>
      {featured ? <span className="rounded-full bg-[var(--public-accent)] px-3 py-1 text-xs font-semibold text-white">Most popular</span> : null}
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
              <motion.div style={{ originX: 0 }} initial={{ scaleX: 0 }} whileInView={{ scaleX: (58 + index * 16) / 100 }} className="h-full w-full rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" />
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
      <motion.div style={{ originX: 0 }} animate={{ scaleX: [0.2, 0.78, 0.34] }} transition={{ duration: 4, repeat: Infinity, delay: index * 0.3 }} className="mt-5 h-1 w-full rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" />
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

function MacFrame({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`public-panel relative overflow-hidden rounded-[1.7rem] shadow-[0_28px_100px_rgba(55,18,105,0.20)] ${className}`}>
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
