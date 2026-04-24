'use client';

import { useState, useEffect } from 'react';
import { Card, CardHeader, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Table, TableHeader, TableBody, TableRow, TableCell } from '@/components/ui/Table';
import { ShieldCheck, ShieldWarning, Globe, WarningCircle } from '@phosphor-icons/react';
import { get } from '@/lib/api-client';

interface DomainStatus {
  id: string;
  domain: string;
  isVerified: boolean;
  spfStatus: string;
  dkimStatus: string;
  dmarcStatus: string;
  reputationScore: number;
}

export default function DeliverabilityPage() {
  const [domains, setDomains] = useState<DomainStatus[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchDomains = async () => {
      try {
        const response = await get<any>('/deliverability/domains');
        const data = Array.isArray(response) ? response : response?.content || response?.data || [];
        setDomains(data);
      } catch (e) {
        console.error('Failed to fetch domains', e);
      } finally {
        setLoading(false);
      }
    };
    fetchDomains();
  }, []);

  const avgReputation = domains.length > 0 ? Math.round(domains.reduce((sum, d) => sum + (d.reputationScore || 0), 0) / domains.length) : 0;
  const verifiedCount = domains.filter(d => d.isVerified).length;
  const issuesCount = domains.filter(d => !d.isVerified || d.spfStatus === 'FAIL' || d.dkimStatus === 'FAIL').length;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Deliverability</h1>
          <p className="mt-1 text-sm text-content-secondary">Monitor your domain reputation and authentication health</p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="bg-gradient-to-br from-green-500/10 to-emerald-500/5 border-green-500/20">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-green-600 dark:text-green-400">Avg Reputation</p>
                <h3 className="text-3xl font-bold text-content-primary">{avgReputation}/100</h3>
              </div>
              <ShieldCheck size={48} weight="duotone" className="text-green-500" />
            </div>
          </CardContent>
        </Card>

        <Card className="bg-gradient-to-br from-brand-500/10 to-blue-500/5 border-brand-500/20">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-brand-600 dark:text-brand-400">Authenticated Domains</p>
                <h3 className="text-3xl font-bold text-content-primary">{verifiedCount} / {domains.length}</h3>
              </div>
              <Globe size={48} weight="duotone" className="text-brand-500" />
            </div>
          </CardContent>
        </Card>

        <Card className="bg-gradient-to-br from-orange-500/10 to-red-500/5 border-orange-500/20">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-orange-600 dark:text-orange-400">Issues Detected</p>
                <h3 className="text-3xl font-bold text-content-primary">{issuesCount}</h3>
              </div>
              <WarningCircle size={48} weight="duotone" className="text-orange-500" />
            </div>
          </CardContent>
        </Card>
      </div>

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
              <TableCell className="font-semibold text-right">Reputation</TableCell>
            </TableRow>
          </TableHeader>
          <TableBody>
            {domains.map((d) => (
              <TableRow key={d.id}>
                <TableCell className="font-medium text-content-primary">{d.domain}</TableCell>
                <TableCell>
                  {d.isVerified ? (
                    <Badge variant="success">Verified</Badge>
                  ) : (
                    <Badge variant="warning">Action Required</Badge>
                  )}
                </TableCell>
                <TableCell className="text-center">
                   <StatusDot status={d.spfStatus} />
                </TableCell>
                <TableCell className="text-center">
                   <StatusDot status={d.dkimStatus} />
                </TableCell>
                <TableCell className="text-center">
                   <StatusDot status={d.dmarcStatus} />
                </TableCell>
                <TableCell className="text-right">
                   <span className={`font-bold ${d.reputationScore > 80 ? 'text-green-500' : d.reputationScore > 50 ? 'text-orange-500' : 'text-red-500'}`}>
                     {d.reputationScore}
                   </span>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </div>
  );
}

function StatusDot({ status }: { status: string }) {
  const colors = {
    PASS: 'bg-green-500',
    FAIL: 'bg-red-500',
    PENDING: 'bg-orange-500',
    NONE: 'bg-content-muted'
  };
  
  return (
    <div className="flex flex-col items-center gap-1">
      <div className={`h-2.5 w-2.5 rounded-full ${colors[status as keyof typeof colors] || 'bg-gray-300'}`} />
      <span className="text-[10px] font-bold text-content-muted">{status}</span>
    </div>
  );
}
