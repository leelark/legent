import { redirect } from 'next/navigation';

/**
 * Root page redirects to the workspace dashboard.
 */
export default function RootPage() {
  redirect('/email');
}
