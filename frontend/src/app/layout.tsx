import '@/styles/globals.css';
import type { Metadata } from 'next';
import { ThemeInitializer } from '@/components/shared/ThemeInitializer';

export const metadata: Metadata = {
  title: 'Legent: Email Studio',
  description: 'Enterprise Email Marketing Platform',
  icons: {
    icon: [
      { url: '/favicon.ico' },
      { url: '/favicon.svg', type: 'image/svg+xml' },
    ],
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen bg-surface-primary text-content-primary antialiased">
        <ThemeInitializer />
        {children}
      </body>
    </html>
  );
}
