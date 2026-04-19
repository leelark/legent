import React, { useEffect, useState } from 'react';
import { getEventCounts, getEventTimeline } from '@/lib/tracking-api';
import { Card } from '@/components/ui/Card';
import { Line } from 'react-chartjs-2';
import { subscribeAnalytics } from '@/lib/analytics-ws';

export const AnalyticsDashboard: React.FC = () => {
  const [counts, setCounts] = useState<any[]>([]);
  const [timeline, setTimeline] = useState<any[]>([]);
  const [selectedType, setSelectedType] = useState('open');

  useEffect(() => {
    getEventCounts().then(setCounts);
    const unsub = subscribeAnalytics((data) => setCounts(data));
    return unsub;
  }, []);

  useEffect(() => {
    getEventTimeline(selectedType).then(setTimeline);
  }, [selectedType]);

  return (
    <div className="space-y-6">
      <Card className="p-4">
        <h3 className="font-bold mb-2">Event Counts</h3>
        <div className="flex gap-6">
          {counts.map((c) => (
            <div key={c.event_type} className="flex flex-col items-center">
              <span className="text-2xl font-bold">{c.count}</span>
              <span className="text-xs uppercase text-muted-foreground">{c.event_type}</span>
            </div>
          ))}
        </div>
      </Card>
      <Card className="p-4">
        <div className="flex items-center gap-4 mb-2">
          <h3 className="font-bold">Timeline</h3>
          <select value={selectedType} onChange={e => setSelectedType(e.target.value)} className="border rounded px-2 py-1">
            <option value="open">Open</option>
            <option value="click">Click</option>
            <option value="conversion">Conversion</option>
          </select>
        </div>
        <Line
          data={{
            labels: timeline.map((t) => t.hour || t.event_date),
            datasets: [
              {
                label: selectedType,
                data: timeline.map((t) => t.count),
                fill: false,
                borderColor: '#6366f1',
                tension: 0.1,
              },
            ],
          }}
        />
      </Card>
    </div>
  );
};
