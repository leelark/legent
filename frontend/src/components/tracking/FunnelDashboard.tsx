'use client';
import React, { useEffect, useState } from 'react';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/Card';

export const FunnelDashboard: React.FC<{ campaignId: string }> = ({ campaignId }) => {
  const [funnel, setFunnel] = useState<any[]>([]);

  useEffect(() => {
    apiClient.get('/analytics/funnel', { params: { campaignId } }).then(res => setFunnel(res.data));
  }, [campaignId]);

  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Funnel Analysis</h3>
      <div className="flex gap-8">
        {funnel.map((step) => (
          <div key={step.event_type} className="flex flex-col items-center">
            <span className="text-2xl font-bold">{step.count}</span>
            <span className="text-xs uppercase text-muted-foreground">{step.event_type}</span>
          </div>
        ))}
      </div>
    </Card>
  );
};
