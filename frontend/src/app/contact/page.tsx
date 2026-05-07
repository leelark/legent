import { PublicPageView } from '@/components/marketing/PublicPageView';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Contact | Legent',
  description: 'Plan a Legent workspace rollout, provider setup, migration, deliverability strategy, or enterprise review.',
};

export default function ContactPage() {
  return <PublicPageView pageKey="contact" titleFallback="Talk to product, engineering and solution architects." />;
}
