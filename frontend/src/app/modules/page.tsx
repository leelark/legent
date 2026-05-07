import { PublicPageView } from '@/components/marketing/PublicPageView';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Modules | Legent',
  description: 'See how Legent studios connect audience, template, campaign, automation, delivery, and analytics workflows.',
};

export default function ModulesPage() {
  return <PublicPageView pageKey="modules" titleFallback="Purpose-built studios for every messaging workflow." />;
}
