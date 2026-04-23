'use client';

import { useState, useEffect } from 'react';
import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Table } from '@/components/ui/Table';
import { EmptyState } from '@/components/ui/EmptyState';
import { ChartPieSlice, Activity, TrendUp } from '@phosphor-icons/react';

export default function TrackingPage() {
  const [summaries, setSummaries] = useState<any[]>([]);
  const [eventCounts, setEventCounts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAnalytics = async () => {
      try {
        const [sumRes, countRes] = await Promise.all([
          fetch('/api/v1/analytics/campaigns').then(r => r.json()),
          fetch('/api/v1/analytics/events/counts').then(r => r.json())
        ]);
        setSummaries(sumRes.data || []);
        setEventCounts(countRes.data || []);
      } catch (err) {
        console.error("Failed to fetch analytics", err);
      } finally {
        setLoading(false);
      }
    };
    fetchAnalytics();
  }, []);

  const columns = [
    { key: 'campaignId', header: 'Campaign ID' },
    { key: 'totalSends', header: 'Sends' },
    { key: 'totalOpens', header: 'Opens' },
    { key: 'totalClicks', header: 'Clicks' },
    { 
      key: 'ctr', 
      header: 'CTR', 
      render: (row: any) => row.totalSends > 0 ? ((row.totalClicks / row.totalSends) * 100).toFixed(2) + '%' : '0%'
    }
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-content-primary">Tracking & Analytics</h1>
        <p className="mt-1 text-sm text-content-secondary">Monitor email performance and engagement</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {eventCounts.map((item: any) => (
          <Card key={item.event_type} className="p-4">
             <div className="flex items-center gap-3">
               <div className="p-2 bg-brand-50 rounded-lg text-brand-600">
                 <TrendUp size={20} />
               </div>
               <div>
                 <p className="text-xs font-medium text-content-secondary uppercase">{item.event_type}</p>
                 <p className="text-xl font-bold">{item.count}</p>
               </div>
             </div>
          </Card>
        ))}
      </div>

      <Card className="!p-0 overflow-hidden">
        <CardHeader title="Campaign Performance Summary" className="px-6 py-4 border-b" />
        {loading ? (
          <div className="p-12 text-center text-content-muted">Loading analytics...</div>
        ) : summaries.length > 0 ? (
          <Table
            columns={columns}
            data={summaries}
            rowKey={(row: any) => row.id}
          />
        ) : (
          <EmptyState type="empty" title="No analytics data" description="Send your first campaign to see results here." />
        )}
      </Card>
    </div>
  );
}
