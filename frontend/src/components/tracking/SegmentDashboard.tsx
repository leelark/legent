'use client';
import React, { useEffect, useState } from 'react';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/Card';

export const SegmentDashboard: React.FC<{ field: string; value: string }> = ({ field, value }) => {
  const [segment, setSegment] = useState<any[]>([]);

  useEffect(() => {
    apiClient.get('/analytics/segment', { params: { field, value } }).then(res => setSegment(res.data));
  }, [field, value]);

  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Segment: {field} = {value}</h3>
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
