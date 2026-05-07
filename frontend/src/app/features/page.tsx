import { PublicPageView } from '@/components/marketing/PublicPageView';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Features | Legent',
  description: 'Explore governed audience, campaign, automation, delivery, analytics, and runtime-control capabilities.',
};

export default function FeaturesPage() {
  return <PublicPageView pageKey="features" titleFallback="Everything teams need to orchestrate lifecycle messaging." />;
}
