import { PublicPageView } from '@/components/marketing/PublicPageView';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Pricing | Legent',
  description: 'Interactive INR pricing for lifecycle teams scaling from first sends to governed enterprise programs.',
};

export default function PricingPage() {
  return <PublicPageView pageKey="pricing" titleFallback="Plans that scale from first campaign to global orchestration." />;
}
