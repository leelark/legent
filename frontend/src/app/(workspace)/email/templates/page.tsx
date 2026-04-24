'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { Plus } from '@phosphor-icons/react/dist/ssr';
import { get, post } from '@/lib/api-client';

interface TemplateSummary {
  id: string;
  name: string;
  subject?: string;
  status: string;
  templateType: string;
  category?: string;
  createdAt?: string;
}

interface PagedResponse<T> {
  success: boolean;
  data: T[];
  pagination?: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

export default function EmailTemplatesPage() {
  const [templates, setTemplates] = useState<TemplateSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [name, setName] = useState('');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  useEffect(() => {
    loadTemplates();
  }, []);

  const loadTemplates = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await get<any>('/templates?page=0&size=20');
      if (Array.isArray(res)) {
        setTemplates(res);
      } else if (res?.content) {
        setTemplates(res.content);
      } else {
        setError('Unable to load templates');
      }
    } catch (e: any) {
      setError(e?.response?.data?.error?.message || 'Unable to load templates');
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreateTemplate = async () => {
    if (!name.trim()) {
      setError('Template name is required.');
      return;
    }
    if (!subject.trim()) {
      setError('Subject is required.');
      return;
    }
    if (!body.trim()) {
      setError('Body is required.');
      return;
    }

    setIsCreating(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const res = await post<any>('/templates', {
          name: name.trim(),
          subject: subject.trim(),
          body: body.trim(),
          textContent: body.trim().replace(/<[^>]*>/g, ''),
          category: 'General',
          tags: [],
          metadata: '{}',
      });
      if (res && res.id) {
        setTemplates((current) => [res, ...current]);
        setName('');
        setSubject('');
        setBody('');
        setSuccessMessage('Template created successfully');
      } else {
        setError('Unable to create template');
      }
    } catch (e: any) {
      const msg = e?.response?.data?.error?.message || e?.message || 'Unable to create template';
      setError(msg);
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Email Templates</h1>
          <p className="mt-1 text-sm text-content-secondary">
            Manage reusable email templates for campaigns and automations.
          </p>
        </div>
        <Link href="/email">
          <Button variant="secondary">Back to Email Studio</Button>
        </Link>
      </div>

      <Card>
        <CardHeader title="Create a template" action={<Plus size={16} />} />
        <div className="grid gap-4 p-6 sm:grid-cols-2">
          <Input
            label="Template name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Spring promotion"
          />
          <Input
            label="Subject"
            value={subject}
            onChange={(event) => setSubject(event.target.value)}
            placeholder="Your next campaign subject"
          />
        </div>
        <div className="p-6">
          <label className="mb-1 block text-sm font-medium text-content-primary">Body (HTML)</label>
          <textarea
            value={body}
            onChange={(event) => setBody(event.target.value)}
            placeholder="<p>Your email content here</p>"
            rows={4}
            className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-all"
          />
        </div>
        <div className="flex flex-wrap gap-3 p-6">
          <Button onClick={handleCreateTemplate} loading={isCreating} disabled={isCreating}>
            Create Template
          </Button>
          <span className="text-sm text-content-muted">Create a draft template and edit it later.</span>
        </div>
        {error ? <p className="px-6 text-sm text-danger">{error}</p> : null}
        {successMessage ? <p className="px-6 text-sm text-success">{successMessage}</p> : null}
      </Card>

      <Card>
        <CardHeader title="Templates" action={<span className="text-sm text-content-secondary">{templates.length} items</span>} />
        <div className="overflow-hidden border-t border-border-default">
          {isLoading ? (
            <div className="p-8 text-sm text-content-secondary">Loading templates…</div>
          ) : templates.length === 0 ? (
            <EmptyState
              type="empty"
              title="No templates yet"
              description="Create your first email template to store the content for your campaigns."
              action={
                <Button onClick={handleCreateTemplate} loading={isCreating}>
                  Create your first template
                </Button>
              }
            />
          ) : (
            <div className="grid gap-0 divide-y divide-border-default">
              {templates.map((template) => (
                <Link
                  key={template.id}
                  href={`/email/templates/${template.id}`}
                  className="block p-4 hover:bg-surface-secondary"
                >
                  <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                    <div>
                      <p className="font-semibold text-content-primary">{template.name}</p>
                      <p className="text-sm text-content-secondary">{template.subject || 'No subject yet'}</p>
                    </div>
                    <div className="flex flex-wrap items-center gap-3 text-sm text-content-secondary">
                      <span>{template.status}</span>
                      <span>{template.templateType}</span>
                      <span>{template.createdAt ? new Date(template.createdAt).toLocaleDateString() : ''}</span>
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}
