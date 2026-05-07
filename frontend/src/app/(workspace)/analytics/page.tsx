'use client';

import { useEffect, useMemo, useState } from 'react';
import { Activity, CheckCircle, Flame, MousePointerClick, RefreshCw } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
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
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-brand-300">Live telemetry</p>
          <h1 className="mt-1 text-2xl font-semibold text-content-primary md:text-3xl">Analytics Overview</h1>
          <p className="mt-1 text-sm text-content-secondary">Aggregate delivery and engagement metrics from tracking services.</p>
        </div>
        <Button variant="secondary" icon={<RefreshCw size={16} />} onClick={() => void load()} loading={loading}>
          Refresh
        </Button>
      </div>

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
              <div className="py-16 text-center text-sm text-content-secondary">Loading data...</div>
            ) : eventCounts.length === 0 ? (
              <div className="py-16 text-center text-sm text-content-secondary">No event data available yet</div>
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
              <p className="py-12 text-center text-sm text-content-secondary">Loading campaigns...</p>
            ) : campaigns.length === 0 ? (
              <p className="py-12 text-center text-sm text-content-secondary">No campaigns tracked yet</p>
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
        <div className="rounded-lg border border-brand-500/20 bg-brand-500/10 p-2 text-brand-300">{icon}</div>
      </div>
    </Card>
  );
}
