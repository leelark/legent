import React from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { ShieldCheck, ShieldAlert, Globe, Activity, Ban } from 'lucide-react';
import { Button } from "@/components/ui/Button";

export default function DeliverabilitySettings() {
  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Deliverability & Compliance</h2>
          <p className="text-muted-foreground mt-1">Manage sender identities, DNS records, and domain reputation</p>
        </div>
        <Button className="bg-indigo-600 hover:bg-indigo-700">Add Sender Domain</Button>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Average Reputation Score</CardTitle>
            <Activity className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">98 / 100</div>
            <p className="text-xs text-muted-foreground mt-1">
              Excellent sending health across 3 domains
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Suppression List Size</CardTitle>
            <Ban className="h-4 w-4 text-red-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">14,293</div>
            <p className="text-xs text-muted-foreground mt-1">
              Hard bounces, complaints, and unsubscribes
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Compliance Alerts</CardTitle>
            <ShieldAlert className="h-4 w-4 text-orange-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-slate-800">0</div>
            <p className="text-xs text-muted-foreground mt-1">
              No recent spam traps hit
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
              <TableRow>
                <TableCell className="font-medium flex items-center">
                    <Globe className="w-4 h-4 mr-2 text-slate-400" /> legent.local
                </TableCell>
                <TableCell><ShieldCheck className="w-4 h-4 text-green-500" /></TableCell>
                <TableCell><ShieldCheck className="w-4 h-4 text-green-500" /></TableCell>
                <TableCell><ShieldCheck className="w-4 h-4 text-green-500" /></TableCell>
                <TableCell>
                    <span className="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800">
                        100 (Excellent)
                    </span>
                </TableCell>
                <TableCell className="text-right">
                  <Button variant="outline" size="sm">Verify DNS</Button>
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell className="font-medium flex items-center">
                    <Globe className="w-4 h-4 mr-2 text-slate-400" /> promo.legent.local
                </TableCell>
                <TableCell><ShieldAlert className="w-4 h-4 text-red-500" /></TableCell>
                <TableCell><ShieldCheck className="w-4 h-4 text-green-500" /></TableCell>
                <TableCell><ShieldAlert className="w-4 h-4 text-red-500" /></TableCell>
                <TableCell>
                    <span className="inline-flex items-center rounded-full bg-orange-100 px-2.5 py-0.5 text-xs font-medium text-orange-800">
                        82 (Warning)
                    </span>
                </TableCell>
                <TableCell className="text-right">
                  <Button variant="outline" size="sm">Verify DNS</Button>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
