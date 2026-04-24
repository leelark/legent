'use client';

import { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui";
import { Activity, MousePointerClick, CheckCircle, Flame } from 'lucide-react';
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

export default function AnalyticsDashboard() {
  const [eventCounts, setEventCounts] = useState<EventCount[]>([]);
  const [campaigns, setCampaigns] = useState<CampaignSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const counts = await get<EventCount[]>('/analytics/events/counts');
        setEventCounts(Array.isArray(counts) ? counts : []);
      } catch (e: any) {
        setEventCounts([]);
      }
      try {
        const camps = await get<CampaignSummary[]>('/analytics/campaigns');
        setCampaigns(Array.isArray(camps) ? camps : []);
      } catch (e: any) {
        setCampaigns([]);
      }
      setLoading(false);
    };
    load();
  }, []);

  const totalOpens = eventCounts.find(e => e.event_type === 'OPEN')?.count || 0;
  const totalClicks = eventCounts.find(e => e.event_type === 'CLICK')?.count || 0;
  const totalConversions = eventCounts.find(e => e.event_type === 'CONVERSION')?.count || 0;
  const activeCampaigns = campaigns.length;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Analytics Overview</h2>
          <p className="text-muted-foreground mt-1">Real-time aggregate delivery and engagement metrics</p>
        </div>
      </div>

      {/* Aggregate Metric Cards */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Opens</CardTitle>
            <Activity className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{totalOpens.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground">
              {loading ? 'Loading...' : 'From tracked events'}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Clicks</CardTitle>
            <MousePointerClick className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{totalClicks.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground">
              {loading ? 'Loading...' : 'From tracked events'}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Conversions</CardTitle>
            <CheckCircle className="h-4 w-4 text-purple-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{totalConversions.toLocaleString()}</div>
            <p className="text-xs text-muted-foreground">
              {loading ? 'Loading...' : 'From tracked events'}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Active Campaigns</CardTitle>
            <Flame className="h-4 w-4 text-orange-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{activeCampaigns}</div>
            <p className="text-xs text-muted-foreground">
              {loading ? 'Loading...' : 'tracked campaigns'}
            </p>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-7">
        <Card className="col-span-4">
          <CardHeader>
            <CardTitle>Engagement Overview</CardTitle>
            <CardDescription>
              A snapshot of Open and Click rates over recent campaigns.
            </CardDescription>
          </CardHeader>
          <CardContent className="pl-2 h-[350px] flex items-center justify-center border-t border-dashed m-4 rounded">
            {loading ? (
              <span className="text-muted-foreground">Loading data...</span>
            ) : eventCounts.length === 0 ? (
              <span className="text-muted-foreground">No event data available yet</span>
            ) : (
              <div className="w-full space-y-4 px-4">
                {eventCounts.map((ec) => (
                  <div key={ec.event_type} className="flex items-center justify-between">
                    <span className="text-sm font-medium">{ec.event_type}</span>
                    <span className="text-lg font-bold">{ec.count}</span>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="col-span-3">
          <CardHeader>
            <CardTitle>Top Performing Campaigns</CardTitle>
            <CardDescription>Recent campaign summaries</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-8">
              {loading ? (
                <p className="text-sm text-muted-foreground">Loading campaigns...</p>
              ) : campaigns.length === 0 ? (
                <p className="text-sm text-muted-foreground">No campaigns tracked yet</p>
              ) : (
                campaigns.slice(0, 5).map((c) => (
                  <div className="flex items-center" key={c.campaignId || c.id}>
                    <div className="ml-4 space-y-1">
                      <p className="text-sm font-medium leading-none">{c.campaignId || 'Campaign'}</p>
                      <p className="text-sm text-muted-foreground">Opens: {c.totalOpens || 0} | Clicks: {c.totalClicks || 0}</p>
                    </div>
                    <div className="ml-auto font-medium text-green-500">
                      {c.totalSends ? Math.round(((c.totalClicks || 0) / c.totalSends) * 100) + '% CTR' : '-'}
                    </div>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
