import { PublicPageView } from '@/components/marketing/PublicPageView';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Legent | Lifecycle Email Operating System',
  description: 'Run lifecycle email from customer signal to inbox with governed workflows, delivery control, and live operating visibility.',
};

export default function RootPage() {
  return <PublicPageView pageKey="home" titleFallback="Lifecycle Email Operating System" />;
}
