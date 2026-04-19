'use client';

import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { Plus, Database } from '@phosphor-icons/react';

export default function DataExtensionsPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Data Extensions</h1>
          <p className="mt-1 text-sm text-content-secondary">Dynamic schema tables for custom data</p>
        </div>
        <Button icon={<Plus size={16} />}>Create Data Extension</Button>
      </div>
      <Card>
        <CardHeader title="All Data Extensions" />
        <EmptyState
          type="empty"
          title="No data extensions yet"
          description="Create data extensions to store custom subscriber data."
          action={<Button icon={<Database size={16} />}>Create Data Extension</Button>}
        />
      </Card>
    </div>
  );
}
