'use client';

import { useEffect, useMemo, useState } from 'react';
import { Activity, CheckCircle, Flame, MousePointerClick, RefreshCw } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageChrome';
import { Skeleton } from '@/components/ui/Skeleton';
import { get } from '@/lib/api-client';

interface EventCount {
  event_type: string;
  count: number;
}

interface CampaignSummary {
  id?: string;
  campaignId?: string;
  totalSends?: number;
  totalOpens?: number;
  totalClicks?: number;
  totalConversions?: number;
}

const eventLabels: Record<string, string> = {
  OPEN: 'Opens',
  CLICK: 'Clicks',
  CONVERSION: 'Conversions',
};

export default function AnalyticsDashboard() {
  const [eventCounts, setEventCounts] = useState<EventCount[]>([]);
  const [campaigns, setCampaigns] = useState<CampaignSummary[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    const [counts, camps] = await Promise.allSettled([
      get<EventCount[]>('/analytics/events/counts'),
      get<CampaignSummary[]>('/analytics/campaigns'),
    ]);
    setEventCounts(counts.status === 'fulfilled' && Array.isArray(counts.value) ? counts.value : []);
    setCampaigns(camps.status === 'fulfilled' && Array.isArray(camps.value) ? camps.value : []);
    setLoading(false);
  };

  useEffect(() => {
    void load();
  }, []);

  const totals = useMemo(() => {
    const find = (type: string) => eventCounts.find((event) => event.event_type === type)?.count || 0;
    return {
      opens: find('OPEN'),
      clicks: find('CLICK'),
      conversions: find('CONVERSION'),
      campaigns: campaigns.length,
    };
  }, [campaigns.length, eventCounts]);

  const maxCount = Math.max(1, ...eventCounts.map((event) => event.count));

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Live telemetry"
        title="Analytics Overview"
        description="Aggregate delivery and engagement metrics from tracking services."
        action={(
          <Button variant="secondary" icon={<RefreshCw size={16} />} onClick={() => void load()} loading={loading}>
            Refresh
          </Button>
        )}
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Total Opens" value={totals.opens} helper="Tracked open events" icon={<Activity size={18} />} />
        <MetricCard label="Total Clicks" value={totals.clicks} helper="Tracked click events" icon={<MousePointerClick size={18} />} />
        <MetricCard label="Conversions" value={totals.conversions} helper="Goal events captured" icon={<CheckCircle size={18} />} />
        <MetricCard label="Active Campaigns" value={totals.campaigns} helper="Campaign summaries" icon={<Flame size={18} />} />
      </div>

      <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
        <Card>
          <CardHeader>
            <CardTitle>Engagement Mix</CardTitle>
            <CardDescription>Event distribution by type.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {loading ? (
              <div className="space-y-4 py-6">
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} className="h-12 rounded-lg" />
                ))}
              </div>
            ) : eventCounts.length === 0 ? (
              <EmptyState type="empty" title="No event data" description="Engagement events will appear after campaigns generate activity." />
            ) : (
              eventCounts.map((event) => (
                <div key={event.event_type} className="space-y-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium">{eventLabels[event.event_type] || event.event_type}</span>
                    <span className="font-semibold">{event.count.toLocaleString()}</span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-surface-secondary">
                    <div className="h-full rounded-full bg-gradient-to-r from-brand-700 to-brand-300" style={{ width: `${Math.max(4, (event.count / maxCount) * 100)}%` }} />
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Top Campaigns</CardTitle>
            <CardDescription>Recent campaign summaries.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {loading ? (
              <div className="space-y-3 py-4">
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} className="h-16 rounded-lg" />
                ))}
              </div>
            ) : campaigns.length === 0 ? (
              <EmptyState type="empty" title="No campaigns tracked" description="Campaign summaries will appear once sends are recorded." />
            ) : (
              campaigns.slice(0, 6).map((campaign) => {
                const sends = campaign.totalSends || 0;
                const ctr = sends ? Math.round(((campaign.totalClicks || 0) / sends) * 100) : 0;
                return (
                  <div key={campaign.campaignId || campaign.id} className="rounded-lg border border-border-default bg-surface-secondary p-3">
                    <div className="flex items-center justify-between gap-3">
                      <p className="truncate text-sm font-semibold">{campaign.campaignId || campaign.id || 'Campaign'}</p>
                      <Badge variant={ctr >= 10 ? 'success' : 'default'}>{ctr}% CTR</Badge>
                    </div>
                    <p className="mt-1 text-xs text-content-secondary">Opens {campaign.totalOpens || 0} / Clicks {campaign.totalClicks || 0}</p>
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function MetricCard({ label, value, helper, icon }: { label: string; value: number; helper: string; icon: React.ReactNode }) {
  return (
    <Card>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-medium uppercase tracking-wider text-content-secondary">{label}</p>
          <p className="mt-2 text-3xl font-semibold text-content-primary">{value.toLocaleString()}</p>
          <p className="mt-1 text-xs text-content-muted">{helper}</p>
        </div>
        <div className="rounded-lg border border-brand-500/20 bg-brand-500/10 p-2 text-brand-600 dark:text-brand-300">{icon}</div>
      </div>
    </Card>
  );
}
