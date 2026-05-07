import { PublicPageView } from '@/components/marketing/PublicPageView';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Legent | Enterprise Email Operations Command Center',
  description: 'Operate lifecycle email with AI orchestration, realtime visibility, inbox-safe delivery, and enterprise collaboration.',
};

export default function RootPage() {
  return <PublicPageView pageKey="home" titleFallback="Enterprise Email Marketing. Reimagined." />;
}
