'use client';
import React, { useEffect, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { getFunnel, type TrackingAggregate } from '@/lib/tracking-api';

export const FunnelDashboard: React.FC<{ campaignId: string }> = ({ campaignId }) => {
  const [funnel, setFunnel] = useState<TrackingAggregate[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getFunnel(campaignId)
      .then(setFunnel)
      .catch(() => setFunnel([]))
      .finally(() => setLoading(false));
  }, [campaignId]);

  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Funnel Analysis</h3>
      {loading ? <div className="text-sm text-content-muted">Loading...</div> : null}
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
