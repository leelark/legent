'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { get } from '@/lib/api-client';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { useParams } from 'next/navigation';

interface TemplateDetail {
  id: string;
  name: string;
  subject?: string;
  status: string;
  templateType: string;
  category?: string;
  tags?: string[];
  metadata?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: { message?: string };
}

export default function TemplateDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = params?.id as string;

  const [template, setTemplate] = useState<TemplateDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    const loadTemplate = async () => {
      setIsLoading(true);
      setError(null);
      try {
        const res = await get<TemplateDetail>(`/templates/${id}`);
        if (res) {
          setTemplate(res);
        } else {
          setError('Failed to load template');
        }
      } catch (e: any) {
        setError(e.message || 'Failed to load template');
      } finally {
        setIsLoading(false);
      }
    };
    loadTemplate();
  }, [id]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Template details</h1>
          <p className="mt-1 text-sm text-content-secondary">
            View and manage the metadata for this email template.
          </p>
        </div>
        <div className="flex gap-2">
          <Link href="/email/templates">
            <Button variant="secondary">Back to templates</Button>
          </Link>
          <Button onClick={() => router.refresh()}>Refresh</Button>
        </div>
      </div>

      <Card>
        {isLoading ? (
          <div className="p-8 text-sm text-content-secondary">Loading template…</div>
        ) : error ? (
          <EmptyState type="error" title="Unable to load template" description={error} />
        ) : template ? (
          <div className="space-y-4 p-6">
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <p className="text-sm text-content-secondary">Name</p>
                <p className="mt-1 text-base font-medium text-content-primary">{template.name}</p>
              </div>
              <div>
                <p className="text-sm text-content-secondary">Status</p>
                <p className="mt-1 text-base font-medium text-content-primary">{template.status}</p>
              </div>
              <div>
                <p className="text-sm text-content-secondary">Template Type</p>
                <p className="mt-1 text-base font-medium text-content-primary">{template.templateType}</p>
              </div>
              <div>
                <p className="text-sm text-content-secondary">Category</p>
                <p className="mt-1 text-base font-medium text-content-primary">{template.category || 'General'}</p>
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <p className="text-sm text-content-secondary">Subject</p>
                <p className="mt-1 text-base text-content-primary">{template.subject || 'No subject set'}</p>
              </div>
              <div>
                <p className="text-sm text-content-secondary">Tags</p>
                <p className="mt-1 text-base text-content-primary">{template.tags?.join(', ') || 'None'}</p>
              </div>
            </div>

            <div>
              <p className="text-sm text-content-secondary">Metadata</p>
              <pre className="mt-2 overflow-auto rounded-md border border-border-default bg-surface-secondary p-3 text-sm text-content-primary">{template.metadata || '{}'}</pre>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <p className="text-sm text-content-secondary">Created at</p>
                <p className="mt-1 text-base text-content-primary">{template.createdAt ?? 'Unknown'}</p>
              </div>
              <div>
                <p className="text-sm text-content-secondary">Updated at</p>
                <p className="mt-1 text-base text-content-primary">{template.updatedAt ?? 'Unknown'}</p>
              </div>
            </div>
          </div>
        ) : (
          <EmptyState
            type="empty"
            title="Template not found"
            description="This template may have been deleted or does not exist."
            action={<Link href="/email/templates"><Button variant="secondary">Back to templates</Button></Link>}
          />
        )}
      </Card>
    </div>
  );
}
