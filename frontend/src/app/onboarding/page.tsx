import { OnboardingView } from '@/components/marketing/PublicAuthViews';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Onboarding | Legent',
  description: 'Complete Legent workspace setup for sender, provider, and launch readiness.',
};

export default function OnboardingPage() {
  return <OnboardingView />;
}
