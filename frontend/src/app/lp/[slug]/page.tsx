'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { getPublicLandingPage, LandingPage } from '@/lib/template-studio-api';

export default function PublicLandingPageRoute() {
  const params = useParams();
  const slug = params?.slug as string;
  const [page, setPage] = useState<LandingPage | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!slug) return;
    getPublicLandingPage(slug)
      .then(setPage)
      .catch(() => setError('This landing page is not published.'));
  }, [slug]);

  if (error) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-surface-secondary p-6">
        <div className="max-w-md rounded-lg border border-border-default bg-surface-primary p-6 text-center">
          <h1 className="text-xl font-semibold text-content-primary">Page unavailable</h1>
          <p className="mt-2 text-sm text-content-secondary">{error}</p>
        </div>
      </main>
    );
  }

  if (!page) {
    return <main className="min-h-screen bg-white p-6 text-sm text-slate-600">Loading...</main>;
  }

  return (
    <main className="min-h-screen bg-white text-slate-950">
      <div dangerouslySetInnerHTML={{ __html: page.htmlContent || '' }} />
    </main>
  );
}
