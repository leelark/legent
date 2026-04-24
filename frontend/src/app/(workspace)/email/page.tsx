'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { EnvelopeSimple, Plus, ArrowRight } from '@phosphor-icons/react';
import { get, post } from '@/lib/api-client';

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

  const loadEmails = async () => {
    setLoading(true);
    try {
      const res = await get<any>('/emails/recent');
      const data = Array.isArray(res) ? res : (res?.data || []);
      setEmails(data);
    } catch (e: any) {
      setError(e?.response?.data?.error?.message || 'Failed to load emails');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadEmails();
  }, []);

  const handleCreate = async () => {
    if (!newName.trim() || !newSubject.trim() || !newBody.trim()) {
      setError('Name, subject, and body are required');
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
    } catch (e: any) {
      setError(e?.response?.data?.error?.message || 'Failed to create email');
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Email Studio</h1>
          <p className="mt-1 text-sm text-content-secondary">
            Create, manage, and send email campaigns
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link href="/email/templates">
            <Button variant="secondary">Manage Templates</Button>
          </Link>
          <Button icon={<Plus size={16} />} onClick={() => setShowCreate(!showCreate)}>
            New Email
          </Button>
        </div>
      </div>

      {showCreate && (
        <Card>
          <CardHeader title="Create New Email" />
          <div className="p-6 space-y-4">
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
            <div className="flex gap-3">
              <Button onClick={handleCreate} loading={creating} disabled={creating}>Create</Button>
              <Button variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button>
            </div>
          </div>
        </Card>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { label: 'Total Emails', value: emails.length.toString(), change: '-' },
          { label: 'Drafts', value: emails.filter(e => e.status === 'DRAFT').length.toString(), change: '-' },
          { label: 'Sent', value: emails.filter(e => e.status === 'SENT').length.toString(), change: '-' },
          { label: 'Templates', value: '-', change: '-' },
        ].map((stat) => (
          <Card key={stat.label}>
            <p className="text-xs font-medium text-content-secondary uppercase tracking-wider">
              {stat.label}
            </p>
            <p className="mt-2 text-2xl font-bold text-content-primary">{stat.value}</p>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader title="Recent Emails" action={<Badge variant="info">{emails.length} items</Badge>} />
        {loading ? (
          <div className="p-8 text-sm text-content-secondary">Loading emails...</div>
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
              <div key={email.id} className="flex items-center justify-between p-4 hover:bg-surface-secondary">
                <div>
                  <p className="font-semibold text-content-primary">{email.name}</p>
                  <p className="text-sm text-content-secondary">{email.subject}</p>
                </div>
                <div className="flex items-center gap-3">
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
