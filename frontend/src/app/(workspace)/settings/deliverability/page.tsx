'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { ShieldCheck, ShieldAlert, Globe, Activity, Ban, Loader2 } from 'lucide-react';
import { Button } from "@/components/ui/Button";
import { addDomain, listDomains, validateDomain } from '@/lib/deliverability-api';
import { ReputationDashboard } from '@/components/deliverability/ReputationDashboard';
import { DmarcDashboard } from '@/components/deliverability/DmarcDashboard';

export default function DeliverabilitySettings() {
  const [domains, setDomains] = useState<any[]>([]);
  const [newDomain, setNewDomain] = useState('');
  const [loading, setLoading] = useState(false);
  const [selectedDomain, setSelectedDomain] = useState('');

  const refreshDomains = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listDomains();
      const records = Array.isArray(response) ? response : [];
      setDomains(records);
      if (!selectedDomain && records.length > 0) {
        const firstDomain = records[0]?.domainName || '';
        setSelectedDomain(firstDomain);
      }
    } finally {
      setLoading(false);
    }
  }, [selectedDomain]);

  useEffect(() => {
    refreshDomains();
  }, [refreshDomains]);

  const summary = useMemo(() => {
    if (domains.length === 0) {
      return {
        avgScore: 0,
        suppressions: 0,
        complianceAlerts: 0
      };
    }

    const verifiedCount = domains.filter((d) => d.status === 'VERIFIED' || d.isActive).length;
    const complianceAlerts = domains.filter((d) => !d.spfVerified || !d.dkimVerified || !d.dmarcVerified).length;
    const avgScore = Math.round((verifiedCount / domains.length) * 100);

    return {
      avgScore,
      suppressions: 0,
      complianceAlerts
    };
  }, [domains]);

  const handleAddDomain = async () => {
    if (!newDomain.trim()) {
      return;
    }
    setLoading(true);
    try {
      await addDomain(newDomain.trim());
      setNewDomain('');
      await refreshDomains();
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyDomain = async (domainId: string) => {
    setLoading(true);
    try {
      await validateDomain(domainId);
      await refreshDomains();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Deliverability & Compliance</h2>
          <p className="text-content-secondary mt-1">Manage sender identities, DNS records, and domain reputation</p>
        </div>
        <div className="flex items-center gap-2">
          <input
            value={newDomain}
            onChange={(e) => setNewDomain(e.target.value)}
            placeholder="yourdomain.com"
            className="w-56 rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm"
          />
          <Button className="bg-indigo-600 hover:bg-indigo-700" onClick={handleAddDomain} disabled={loading || !newDomain.trim()}>
            Add Sender Domain
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Average Reputation Score</CardTitle>
            <Activity className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">{summary.avgScore} / 100</div>
            <p className="text-xs text-content-secondary mt-1">
              Computed from domain verification status
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Suppression List Size</CardTitle>
            <Ban className="h-4 w-4 text-red-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{summary.suppressions}</div>
            <p className="text-xs text-content-secondary mt-1">
              Synced from suppression sources
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Compliance Alerts</CardTitle>
            <ShieldAlert className="h-4 w-4 text-orange-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-slate-800">{summary.complianceAlerts}</div>
            <p className="text-xs text-content-secondary mt-1">
              Domains with SPF/DKIM/DMARC verification gaps
            </p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Authenticated Sender Domains</CardTitle>
          <CardDescription>
            Domains authorized for outgoing campaigns. Green indicators verify that DNS resolves correctly.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Domain</TableHead>
                <TableHead>SPF</TableHead>
                <TableHead>DKIM</TableHead>
                <TableHead>DMARC</TableHead>
                <TableHead>Reputation</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center py-6 text-content-muted">
                    <span className="inline-flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Loading domains...</span>
                  </TableCell>
                </TableRow>
              ) : domains.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center py-6 text-content-muted">
                    No domains registered
                  </TableCell>
                </TableRow>
              ) : domains.map((domain) => {
                const domainName = domain.domainName || '';
                const domainStatus = domain.status || (domain.isActive ? 'VERIFIED' : 'PENDING');
                const score = (domain.spfVerified && domain.dkimVerified && domain.dmarcVerified) ? 100 : 75;
                return (
                  <TableRow key={domain.id || domainName}>
                    <TableCell className="font-medium flex items-center">
                      <Globe className="w-4 h-4 mr-2 text-slate-400" /> {domainName}
                    </TableCell>
                    <TableCell>{domain.spfVerified ? <ShieldCheck className="w-4 h-4 text-green-500" /> : <ShieldAlert className="w-4 h-4 text-red-500" />}</TableCell>
                    <TableCell>{domain.dkimVerified ? <ShieldCheck className="w-4 h-4 text-green-500" /> : <ShieldAlert className="w-4 h-4 text-red-500" />}</TableCell>
                    <TableCell>{domain.dmarcVerified ? <ShieldCheck className="w-4 h-4 text-green-500" /> : <ShieldAlert className="w-4 h-4 text-red-500" />}</TableCell>
                    <TableCell>
                      <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${score >= 95 ? 'bg-green-100 text-green-800' : 'bg-orange-100 text-orange-800'}`}>
                        {score} ({domainStatus})
                      </span>
                    </TableCell>
                    <TableCell className="text-right space-x-2">
                      <Button variant="outline" size="sm" onClick={() => setSelectedDomain(domainName)}>
                        View
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => handleVerifyDomain(domain.id)} disabled={loading || !domain.id}>
                        Verify DNS
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {selectedDomain && (
        <div className="grid gap-4 lg:grid-cols-2">
          <ReputationDashboard domain={selectedDomain} />
          <DmarcDashboard domain={selectedDomain} />
        </div>
      )}
    </div>
  );
}
