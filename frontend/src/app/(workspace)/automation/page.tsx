import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';

export const metadata = { title: 'Automation | Legent' };

export default function AutomationPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-content-primary">Automation</h1>
        <p className="mt-1 text-sm text-content-secondary">Build and manage customer journeys</p>
      </div>
      <Card>
        <CardHeader title="Workflows" action={<Badge>Module Pending</Badge>} />
        <EmptyState type="empty" title="No workflows yet" description="Create automated journeys for your subscribers." />
      </Card>
    </div>
  );
}
