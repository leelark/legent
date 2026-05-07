import { ForgotPasswordView } from '@/components/marketing/PublicAuthViews';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Forgot Password | Legent',
  description: 'Request a secure password reset for your Legent workspace account.',
};

export default function ForgotPasswordPage() {
  return <ForgotPasswordView />;
}
