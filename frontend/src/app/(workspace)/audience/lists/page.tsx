'use client';

import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { Plus, ListBullets } from '@phosphor-icons/react';

export default function ListsPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Lists</h1>
          <p className="mt-1 text-sm text-content-secondary">Manage subscriber lists</p>
        </div>
        <Button icon={<Plus size={16} />}>Create List</Button>
      </div>
      <Card>
        <CardHeader title="All Lists" />
        <EmptyState
          type="empty"
          title="No lists yet"
          description="Create subscriber lists to organize your audience."
          action={<Button icon={<ListBullets size={16} />}>Create List</Button>}
        />
      </Card>
    </div>
  );
}
