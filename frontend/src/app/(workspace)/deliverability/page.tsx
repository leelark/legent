import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';

export const metadata = { title: 'Deliverability | Legent' };

export default function DeliverabilityPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-content-primary">Deliverability</h1>
        <p className="mt-1 text-sm text-content-secondary">Domain authentication, reputation, and compliance</p>
      </div>
      <Card>
        <CardHeader title="Domain Health" action={<Badge>Module Pending</Badge>} />
        <EmptyState type="empty" title="No domains configured" description="Add your sending domain to get started." />
      </Card>
    </div>
  );
}
