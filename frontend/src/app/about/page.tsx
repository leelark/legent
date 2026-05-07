import { PublicPageView } from '@/components/marketing/PublicPageView';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'About | Legent',
  description: 'Learn why Legent treats lifecycle email as production infrastructure for serious operators.',
};

export default function AboutPage() {
  return <PublicPageView pageKey="about" titleFallback="Built by operators obsessed with message quality at scale." />;
}
