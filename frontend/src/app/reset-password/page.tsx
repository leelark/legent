import { ResetPasswordView } from '@/components/marketing/PublicAuthViews';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Reset Password | Legent',
  description: 'Set a new secure password for your Legent workspace account.',
};

export default function ResetPasswordPage() {
  return <ResetPasswordView />;
}
