'use client';
import React, { useEffect, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { getSegment, type TrackingAggregate } from '@/lib/tracking-api';

export const SegmentDashboard: React.FC<{ field: string; value: string }> = ({ field, value }) => {
  const [segment, setSegment] = useState<TrackingAggregate[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getSegment(field, value)
      .then(setSegment)
      .catch(() => setSegment([]))
      .finally(() => setLoading(false));
  }, [field, value]);

  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Segment: {field} = {value}</h3>
      {loading ? <div className="text-sm text-content-muted">Loading...</div> : null}
      <div className="flex gap-8">
        {segment.map((step) => (
          <div key={step.event_type} className="flex flex-col items-center">
            <span className="text-2xl font-bold">{step.count}</span>
            <span className="text-xs uppercase text-muted-foreground">{step.event_type}</span>
          </div>
        ))}
      </div>
    </Card>
  );
};
