'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Tabs } from '@/components/ui/Tabs';
import { PageHeader } from '@/components/ui/PageChrome';
import { Skeleton } from '@/components/ui/Skeleton';
import {
  Users, ListBullets, Database, Funnel,
  Upload, Plus
} from '@phosphor-icons/react/dist/ssr';
import { get } from '@/lib/api-client';

function StatCard({ label, value, icon: Icon, href, color }: {
  label: string; value: string | number; icon: any; href: string; color: string;
}) {
  return (
    <Link href={href}>
      <Card hover>
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs font-medium text-content-secondary uppercase tracking-wider">{label}</p>
            <p className="mt-2 text-2xl font-bold text-content-primary">{value}</p>
          </div>
          <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${color}`}>
            <Icon size={20} weight="duotone" className="text-white" />
          </div>
        </div>
      </Card>
    </Link>
  );
}

export default function AudienceDashboard() {
  const [stats, setStats] = useState({
    subscribers: 0,
    lists: 0,
    dataExtensions: 0,
    segments: 0
  });
  const [loading, setLoading] = useState(true);
  const [recentSubscribers, setRecentSubscribers] = useState<any[]>([]);
  const [recentImports, setRecentImports] = useState<any[]>([]);
  const [activeSegments, setActiveSegments] = useState<any[]>([]);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const [subRes, listRes, deRes, segRes, recentRes, importRes, activeSegmentRes] = await Promise.all([
          get<any>('/subscribers/count'),
          get<any>('/lists?size=1'),
          get<any>('/data-extensions?size=1'),
          get<any>('/segments?size=1'),
          get<any>('/subscribers?size=5&sortDir=desc'),
          get<any>('/imports?page=0&size=5'),
          get<any>('/segments?page=0&size=5')
        ]);

        setStats({
          subscribers: (typeof subRes === 'number' ? subRes : subRes?.count) || 0,
          lists: listRes?.totalElements || 0,
          dataExtensions: deRes?.totalElements || 0,
          segments: segRes?.totalElements || 0
        });
        setRecentSubscribers(recentRes?.content || []);
        setRecentImports(importRes?.content || importRes?.data || []);
        setActiveSegments(activeSegmentRes?.content || activeSegmentRes?.data || []);
      } catch (err) {
        console.error("Failed to fetch audience stats", err);
      } finally {
        setLoading(false);
      }
    };

    fetchStats();
  }, []);
  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Customer data layer"
        title="Audience"
        description="Manage subscribers, lists, data extensions, and segments."
        action={(
        <div className="flex flex-wrap gap-2">
          <Link href="/app/audience/imports/new">
            <Button variant="secondary" icon={<Upload size={16} />}>Import</Button>
          </Link>
          <Link href="/app/audience/subscribers?action=create">
            <Button icon={<Plus size={16} />}>Add Subscriber</Button>
          </Link>
        </div>
        )}
      />

      {/* Stats Grid */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total Subscribers" value={loading ? "-" : stats.subscribers} icon={Users} href="/app/audience/subscribers" color="bg-gradient-to-br from-brand-500 to-brand-700" />
        <StatCard label="Lists" value={loading ? "-" : stats.lists} icon={ListBullets} href="/app/audience/lists" color="bg-gradient-to-br from-emerald-500 to-emerald-700" />
        <StatCard label="Data Extensions" value={loading ? "-" : stats.dataExtensions} icon={Database} href="/app/audience/data-extensions" color="bg-gradient-to-br from-amber-500 to-amber-700" />
        <StatCard label="Segments" value={loading ? "-" : stats.segments} icon={Funnel} href="/app/audience/segments" color="bg-gradient-to-br from-violet-500 to-violet-700" />
      </div>

      {/* Quick Access Tabs */}
      <Card>
        <Tabs
          tabs={[
            { key: 'recent', label: 'Recent Subscribers', icon: <Users size={16} /> },
            { key: 'imports', label: 'Recent Imports', icon: <Upload size={16} /> },
            { key: 'segments', label: 'Active Segments', icon: <Funnel size={16} /> },
          ]}
        >
          {(tab) => (
            <div className="py-8 text-center">
              {loading ? (
                <div className="mx-auto max-w-xl space-y-3">
                  {Array.from({ length: 3 }).map((_, index) => (
                    <Skeleton key={index} className="h-10 rounded-lg" />
                  ))}
                </div>
              ) : (
                <>
                  {tab === 'recent' && (
                    <div className="space-y-2">
                      {recentSubscribers.length > 0 ? (
                        recentSubscribers.map(sub => (
                          <div key={sub.id} className="rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-left text-sm text-content-primary">{sub.email}</div>
                        ))
                      ) : (
                        <p className="text-sm text-content-muted">No recent subscribers.</p>
                      )}
                    </div>
                  )}
                  {tab === 'imports' && (
                    <div className="space-y-2">
                      {recentImports.length > 0 ? recentImports.map((job) => (
                        <div key={job.id} className="rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-left text-sm">
                          <span className="font-medium text-content-primary">{job.fileName || job.source || job.id}</span>
                          <span className="ml-2 text-content-secondary">{job.status || 'QUEUED'}</span>
                        </div>
                      )) : <p className="text-sm text-content-muted">No import jobs yet.</p>}
                    </div>
                  )}
                  {tab === 'segments' && (
                    <div className="space-y-2">
                      {activeSegments.length > 0 ? activeSegments.map((segment) => (
                        <div key={segment.id} className="rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-left text-sm">
                          <span className="font-medium text-content-primary">{segment.name}</span>
                          <span className="ml-2 text-content-secondary">{segment.status || 'ACTIVE'}</span>
                        </div>
                      )) : <p className="text-sm text-content-muted">No active segments yet.</p>}
                    </div>
                  )}
                </>
              )}
              <Link href={tab === 'recent' ? '/app/audience/subscribers' : tab === 'imports' ? '/app/audience/imports' : '/app/audience/segments'}>
                <Button variant="ghost" size="sm" className="mt-3">View All -&gt;</Button>
              </Link>
            </div>
          )}
        </Tabs>
      </Card>
    </div>
  );
}
