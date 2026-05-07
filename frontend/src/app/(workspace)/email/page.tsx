'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { EnvelopeSimple, Plus } from '@phosphor-icons/react';
import { get, post } from '@/lib/api-client';
import { useToast } from '@/components/ui/Toast';
import { Skeleton, StatCardSkeleton } from '@/components/ui/Skeleton';

interface EmailItem {
  id: string;
  name: string;
  subject: string;
  status: string;
  createdAt?: string;
}

export default function EmailPage() {
  const [emails, setEmails] = useState<EmailItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState('');
  const [newSubject, setNewSubject] = useState('');
  const [newBody, setNewBody] = useState('');
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [templateCount, setTemplateCount] = useState(0);
  const { addToast } = useToast();

  const loadEmails = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [emailRes, templateRes] = await Promise.allSettled([
        get<any>('/emails/recent'),
        get<any>('/templates?page=0&size=1'),
      ]);
      const res = emailRes.status === 'fulfilled' ? emailRes.value : [];
      const data = Array.isArray(res) ? res : (res?.data || []);
      setEmails(data);
      if (templateRes.status === 'fulfilled') {
        const templates = templateRes.value as any;
        setTemplateCount(templates?.totalElements ?? templates?.content?.length ?? 0);
      }
    } catch (e: any) {
      const message = e?.response?.data?.error?.message || 'Failed to load emails';
      setError(message);
      addToast({
        type: 'error',
        title: 'Failed to load emails',
        message,
        duration: 5000
      });
    } finally {
      setLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    loadEmails();
  }, [loadEmails]);

  const handleCreate = async () => {
    if (!newName.trim() || !newSubject.trim() || !newBody.trim()) {
      const message = 'Name, subject, and body are required';
      setError(message);
      addToast({ type: 'warning', title: 'Validation Error', message });
      return;
    }
    setCreating(true);
    setError(null);
    try {
      await post('/emails', {
        name: newName.trim(),
        subject: newSubject.trim(),
        body: newBody.trim()
      });
      setNewName('');
      setNewSubject('');
      setNewBody('');
      setShowCreate(false);
      loadEmails();
      addToast({
        type: 'success',
        title: 'Email created',
        message: `"${newName.trim()}" has been created successfully`,
        duration: 3000
      });
    } catch (e: any) {
      const message = e?.response?.data?.error?.message || 'Failed to create email';
      setError(message);
      addToast({
        type: 'error',
        title: 'Failed to create email',
        message,
        duration: 5000
      });
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-brand-600 dark:text-brand-300">Content command center</p>
          <h1 className="mt-1 text-2xl font-semibold text-content-primary md:text-3xl">Email Studio</h1>
          <p className="mt-1 text-sm text-content-secondary">
            Create, manage, and send email campaigns
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link href="/app/email/templates">
            <Button variant="secondary">Manage Templates</Button>
          </Link>
          <Button icon={<Plus size={16} />} onClick={() => setShowCreate(!showCreate)}>
            New Email
          </Button>
        </div>
      </div>

      {showCreate && (
        <Card className="overflow-hidden">
          <CardHeader title="Create New Email" />
          <div className="space-y-4">
            {error && <p className="text-sm text-danger">{error}</p>}
            <Input label="Email Name" value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Welcome Email" />
            <Input label="Subject" value={newSubject} onChange={(e) => setNewSubject(e.target.value)} placeholder="Welcome to our newsletter" />
            <div>
              <label className="mb-1 block text-sm font-medium text-content-primary">Body (HTML)</label>
              <textarea
                value={newBody}
                onChange={(e) => setNewBody(e.target.value)}
                placeholder="<p>Your email content here</p>"
                rows={5}
                className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-all"
              />
            </div>
            <div className="flex flex-wrap gap-3">
              <Button onClick={handleCreate} loading={creating} disabled={creating}>Create</Button>
              <Button variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button>
            </div>
          </div>
        </Card>
      )}

      {loading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCardSkeleton />
          <StatCardSkeleton />
          <StatCardSkeleton />
          <StatCardSkeleton />
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[
            { label: 'Total Emails', value: emails.length.toString(), change: 'All recent email records' },
            { label: 'Drafts', value: emails.filter(e => e.status === 'DRAFT').length.toString(), change: 'Needs review' },
            { label: 'Sent', value: emails.filter(e => e.status === 'SENT').length.toString(), change: 'Delivered content' },
            { label: 'Templates', value: templateCount.toString(), change: 'Template library' },
          ].map((stat) => (
            <Card key={stat.label}>
              <p className="text-xs font-medium text-content-secondary uppercase tracking-wider">
                {stat.label}
              </p>
              <p className="mt-2 text-3xl font-semibold text-content-primary">{stat.value}</p>
              <p className="mt-1 text-xs text-content-muted">{stat.change}</p>
            </Card>
          ))}
        </div>
      )}

      <Card>
        <CardHeader title="Recent Emails" action={!loading && <Badge variant="info">{emails.length} items</Badge>} />
        {loading ? (
          <div className="p-6 space-y-4">
            <Skeleton variant="text" width="100%" height={60} />
            <Skeleton variant="text" width="100%" height={60} />
            <Skeleton variant="text" width="100%" height={60} />
          </div>
        ) : emails.length === 0 ? (
          <EmptyState
            type="empty"
            title="No emails yet"
            description="Create your first email to get started with email campaigns."
            action={<Button icon={<EnvelopeSimple size={16} />} onClick={() => setShowCreate(true)}>Create Email</Button>}
          />
        ) : (
          <div className="grid gap-0 divide-y divide-border-default">
            {emails.map((email) => (
              <div key={email.id} className="flex flex-col gap-3 p-4 hover:bg-surface-secondary md:flex-row md:items-center md:justify-between">
                <div className="min-w-0">
                  <p className="font-semibold text-content-primary">{email.name}</p>
                  <p className="truncate text-sm text-content-secondary">{email.subject}</p>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  <Badge variant={email.status === 'SENT' ? 'success' : 'default'}>{email.status}</Badge>
                  <span className="text-sm text-content-muted">
                    {email.createdAt ? new Date(email.createdAt).toLocaleDateString() : ''}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
