import { LoginView } from '@/components/marketing/PublicAuthViews';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Login | Legent',
  description: 'Secure operator login for the Legent email operations workspace.',
};

export default function LoginPage() {
  return <LoginView />;
}
