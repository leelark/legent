import { SignupView } from '@/components/marketing/PublicAuthViews';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Create Workspace | Legent',
  description: 'Create a Legent workspace for governed lifecycle email operations.',
};

export default function SignupPage() {
  return <SignupView />;
}
