'use client';

import { useState, useEffect } from 'react';
import { Card, CardHeader, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Table, TableHeader, TableBody, TableRow, TableCell } from '@/components/ui/Table';
import { ShieldCheck, Globe, ArrowClockwise, Lightning, Bug } from '@phosphor-icons/react';
import { Button } from '@/components/ui/Button';
import { get } from '@/lib/api-client';
import {
  DeliveryMessage,
  DeliveryQueueStats,
  enqueueReplay,
  getFailureDiagnostics,
  getQueueStats,
  getWarmupStatus,
  listMessages,
  processReplay,
  retryMessage
} from '@/lib/delivery-api';

interface DomainStatus {
  id: string;
  domainName: string;
  status: string;
  spfVerified: boolean;
  dkimVerified: boolean;
  dmarcVerified: boolean;
}

export default function DeliverabilityPage() {
  const [domains, setDomains] = useState<DomainStatus[]>([]);
  const [stats, setStats] = useState<DeliveryQueueStats | null>(null);
  const [messages, setMessages] = useState<DeliveryMessage[]>([]);
  const [diagnostics, setDiagnostics] = useState<{ failedRecent: number; byFailureClass: Record<string, number>; replayQueueDepth: number } | null>(null);
  const [warmup, setWarmup] = useState<{ readiness: number; activeProviders: number; unhealthyProviders: number } | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionBusy, setActionBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadAll = async () => {
    setLoading(true);
    setError(null);
    try {
      const [domainRes, queueRes, messageRes, diagnosticsRes, warmupRes] = await Promise.all([
        get<any>('/deliverability/domains'),
        getQueueStats(),
        listMessages(20),
        getFailureDiagnostics(),
        getWarmupStatus()
      ]);
      const domainData = Array.isArray(domainRes) ? domainRes : domainRes?.content || domainRes?.data || [];
      setDomains(domainData);
      setStats(queueRes);
      setMessages(messageRes || []);
      setDiagnostics(diagnosticsRes);
      setWarmup(warmupRes);
    } catch (e: any) {
      console.error('Failed to load delivery studio data', e);
      setError(e?.normalized?.message || 'Failed to load delivery studio data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAll();
  }, []);

  const handleRetry = async (messageId: string) => {
    setActionBusy(`retry:${messageId}`);
    try {
      await retryMessage(messageId, 'MANUAL_RETRY');
      await loadAll();
    } finally {
      setActionBusy(null);
    }
  };

  const handleReplay = async (messageId: string) => {
    setActionBusy(`replay:${messageId}`);
    try {
      await enqueueReplay(messageId, 'MANUAL_REPLAY');
      await processReplay(50);
      await loadAll();
    } finally {
      setActionBusy(null);
    }
  };

  const avgAuthScore = domains.length > 0
    ? Math.round((domains.filter((d) => d.spfVerified && d.dkimVerified && d.dmarcVerified).length / domains.length) * 100)
    : 0;
  const verifiedCount = domains.filter((d) => d.status === 'VERIFIED').length;
  const issuesCount = domains.filter((d) => !d.spfVerified || !d.dkimVerified || !d.dmarcVerified).length;

  if (loading) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Delivery Studio</h1>
          <p className="mt-1 text-sm text-content-secondary">Loading delivery telemetry...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-brand-300">Inbox operations</p>
          <h1 className="mt-1 text-2xl font-semibold text-content-primary md:text-3xl">Delivery Studio</h1>
          <p className="mt-1 text-sm text-content-secondary">Queue monitor, replay console, warm-up readiness, and inbox health telemetry</p>
        </div>
        <Button variant="outline" onClick={loadAll} icon={<ArrowClockwise size={14} />}>
          Refresh
        </Button>
      </div>
      {error && <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card className="bg-gradient-to-br from-blue-500/10 to-cyan-500/5 border-blue-500/20">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-blue-600 dark:text-blue-400">Queue Pending</p>
                <h3 className="text-3xl font-bold text-content-primary">{stats?.pending ?? 0}</h3>
              </div>
              <Lightning size={42} weight="duotone" className="text-blue-500" />
            </div>
          </CardContent>
        </Card>
        <Card className="bg-gradient-to-br from-green-500/10 to-emerald-500/5 border-green-500/20">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-green-600 dark:text-green-400">Auth Readiness</p>
                <h3 className="text-3xl font-bold text-content-primary">{avgAuthScore}%</h3>
              </div>
              <ShieldCheck size={42} weight="duotone" className="text-green-500" />
            </div>
          </CardContent>
        </Card>
        <Card className="bg-gradient-to-br from-orange-500/10 to-red-500/5 border-orange-500/20">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-orange-600 dark:text-orange-400">Recent Failures</p>
                <h3 className="text-3xl font-bold text-content-primary">{diagnostics?.failedRecent ?? 0}</h3>
              </div>
              <Bug size={42} weight="duotone" className="text-orange-500" />
            </div>
          </CardContent>
        </Card>
        <Card className="bg-gradient-to-br from-brand-500/10 to-blue-500/5 border-brand-500/20">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-brand-600 dark:text-brand-400">Warm-up Readiness</p>
                <h3 className="text-3xl font-bold text-content-primary">{warmup?.readiness ?? 0}%</h3>
              </div>
              <Globe size={42} weight="duotone" className="text-brand-500" />
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader title="Delivery Queue & Replay Diagnostics" />
          <div className="px-6 pb-6 text-sm text-content-secondary">
            <div className="grid grid-cols-2 gap-3">
              <div>Processing: <span className="font-semibold text-content-primary">{stats?.processing ?? 0}</span></div>
              <div>Failed: <span className="font-semibold text-content-primary">{stats?.failed ?? 0}</span></div>
              <div>Replay Pending: <span className="font-semibold text-content-primary">{stats?.replayPending ?? 0}</span></div>
              <div>Replay Failed: <span className="font-semibold text-content-primary">{stats?.replayFailed ?? 0}</span></div>
              <div>Provider Alerts: <span className="font-semibold text-content-primary">{stats?.unhealthyProviders ?? 0}</span></div>
              <div>Replay Depth: <span className="font-semibold text-content-primary">{diagnostics?.replayQueueDepth ?? 0}</span></div>
            </div>
            {diagnostics && Object.keys(diagnostics.byFailureClass || {}).length > 0 && (
              <div className="mt-4 space-y-1">
                <p className="text-xs uppercase tracking-wide text-content-muted">Failure Classification</p>
                {Object.entries(diagnostics.byFailureClass).map(([type, count]) => (
                  <div key={type} className="flex items-center justify-between rounded border border-border-default px-3 py-2">
                    <span>{type}</span>
                    <span className="font-semibold text-content-primary">{count}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </Card>

        <Card>
          <CardHeader title="Domain Authentication Health" />
          <div className="px-6 pb-6 text-sm text-content-secondary space-y-2">
            <div>Verified Domains: <span className="font-semibold text-content-primary">{verifiedCount} / {domains.length}</span></div>
            <div>Auth Issues: <span className="font-semibold text-content-primary">{issuesCount}</span></div>
            <div>Active Providers: <span className="font-semibold text-content-primary">{warmup?.activeProviders ?? 0}</span></div>
            <div>Unhealthy Providers: <span className="font-semibold text-content-primary">{warmup?.unhealthyProviders ?? 0}</span></div>
          </div>
        </Card>
      </div>

      <Card>
        <CardHeader title="Recent Delivery Messages" />
        <Table>
          <TableHeader>
            <TableRow>
              <TableCell className="font-semibold">Message</TableCell>
              <TableCell className="font-semibold">Recipient</TableCell>
              <TableCell className="font-semibold">Status</TableCell>
              <TableCell className="font-semibold">Attempts</TableCell>
              <TableCell className="font-semibold">Failure Class</TableCell>
              <TableCell className="font-semibold text-right">Actions</TableCell>
            </TableRow>
          </TableHeader>
          <TableBody>
            {messages.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-content-muted py-8">No recent delivery events</TableCell>
              </TableRow>
            ) : messages.map((message) => {
              const canRetry = message.status === 'FAILED';
              const canReplay = message.status === 'FAILED' || message.status === 'SENT';
              return (
                <TableRow key={message.id}>
                  <TableCell className="font-mono text-xs text-content-secondary">{message.messageId}</TableCell>
                  <TableCell className="font-medium text-content-primary">{message.email}</TableCell>
                  <TableCell>
                    <Badge variant={message.status === 'SENT' ? 'success' : message.status === 'FAILED' ? 'danger' : 'info'}>{message.status}</Badge>
                  </TableCell>
                  <TableCell>{message.attemptCount ?? 0}</TableCell>
                  <TableCell>{message.failureClass || '-'}</TableCell>
                  <TableCell className="text-right space-x-2">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={!canRetry || actionBusy === `retry:${message.messageId}`}
                      onClick={() => handleRetry(message.messageId)}
                    >
                      Retry
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={!canReplay || actionBusy === `replay:${message.messageId}`}
                      onClick={() => handleReplay(message.messageId)}
                    >
                      Replay
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Card>

      <Card>
        <CardHeader title="Sending Domains" />
        <Table>
          <TableHeader>
            <TableRow>
              <TableCell className="font-semibold">Domain</TableCell>
              <TableCell className="font-semibold">Status</TableCell>
              <TableCell className="font-semibold text-center">SPF</TableCell>
              <TableCell className="font-semibold text-center">DKIM</TableCell>
              <TableCell className="font-semibold text-center">DMARC</TableCell>
            </TableRow>
          </TableHeader>
          <TableBody>
            {domains.map((domain) => (
              <TableRow key={domain.id}>
                <TableCell className="font-medium text-content-primary">{domain.domainName}</TableCell>
                <TableCell>
                  {domain.status === 'VERIFIED' ? <Badge variant="success">Verified</Badge> : <Badge variant="warning">Action Required</Badge>}
                </TableCell>
                <TableCell className="text-center"><StatusDot ok={domain.spfVerified} /></TableCell>
                <TableCell className="text-center"><StatusDot ok={domain.dkimVerified} /></TableCell>
                <TableCell className="text-center"><StatusDot ok={domain.dmarcVerified} /></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </div>
  );
}

function StatusDot({ ok }: { ok: boolean }) {
  return (
    <div className="flex justify-center">
      <div className={`h-2.5 w-2.5 rounded-full ${ok ? 'bg-green-500' : 'bg-red-500'}`} />
    </div>
  );
}
