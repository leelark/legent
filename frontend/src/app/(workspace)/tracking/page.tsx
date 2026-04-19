import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';

export const metadata = { title: 'Tracking | Legent' };

export default function TrackingPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-content-primary">Tracking & Analytics</h1>
        <p className="mt-1 text-sm text-content-secondary">Monitor email performance and engagement</p>
      </div>
      <Card>
        <CardHeader title="Dashboard" action={<Badge>Module Pending</Badge>} />
        <EmptyState type="empty" title="No data yet" description="Send your first campaign to see analytics here." />
      </Card>
    </div>
  );
}
