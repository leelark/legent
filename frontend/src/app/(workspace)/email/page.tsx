import Link from 'next/link';
import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { EnvelopeSimple, Plus } from '@phosphor-icons/react/dist/ssr';

export const metadata = {
  title: 'Email Studio | Legent',
};

export default function EmailPage() {
  return (
    <div className="space-y-6">
      {/* Page Header */}
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
          <Button icon={<Plus size={16} />}>New Email</Button>
        </div>
      </div>

      {/* Quick Stats */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { label: 'Total Emails', value: '0', change: '-' },
          { label: 'Drafts', value: '0', change: '-' },
          { label: 'Sent This Month', value: '0', change: '-' },
          { label: 'Templates', value: '0', change: '-' },
        ].map((stat) => (
          <Card key={stat.label}>
            <p className="text-xs font-medium text-content-secondary uppercase tracking-wider">
              {stat.label}
            </p>
            <p className="mt-2 text-2xl font-bold text-content-primary">{stat.value}</p>
          </Card>
        ))}
      </div>

      {/* Content */}
      <Card>
        <CardHeader title="Recent Emails" action={<Badge variant="info">Coming Soon</Badge>} />
        <EmptyState
          type="empty"
          title="No emails yet"
          description="Create your first email template to get started with email campaigns."
          action={<Button icon={<EnvelopeSimple size={16} />}>Create Email</Button>}
        />
      </Card>
    </div>
  );
}
