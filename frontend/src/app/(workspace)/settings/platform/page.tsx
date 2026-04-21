import React from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Link, Webhook, Key, CheckCircle, XCircle, Search } from 'lucide-react';
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";

export default function PlatformSettings() {
  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Platform Integrations</h2>
          <p className="text-muted-foreground mt-1">Manage API Keys, Webhooks, and Global Branding Settings</p>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
                <div>
                    <CardTitle className="text-lg">Global Webhooks</CardTitle>
                    <CardDescription>Real-time HTTP push notifications</CardDescription>
                </div>
                <Button variant="outline" size="sm"><Webhook className="w-4 h-4 mr-2"/> Add Webhook</Button>
            </div>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Endpoint</TableHead>
                  <TableHead>Events</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow>
                  <TableCell className="font-medium text-xs text-muted-foreground break-all max-w-[200px]">
                      https://hooks.slack.com/services/T00...
                  </TableCell>
                  <TableCell className="text-xs">
                      <span className="bg-slate-100 text-slate-800 px-2 py-1 rounded">workflow.completed</span>
                  </TableCell>
                  <TableCell><CheckCircle className="w-4 h-4 text-green-500" /></TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium text-xs text-muted-foreground break-all max-w-[200px]">
                      https://api.mycrm.com/v1/legent/sync
                  </TableCell>
                  <TableCell className="text-xs">
                      <span className="bg-slate-100 text-slate-800 px-2 py-1 rounded">subscriber.created</span>
                  </TableCell>
                  <TableCell><XCircle className="w-4 h-4 text-red-500" /></TableCell> {/* Failing hook */}
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
             <div className="flex items-center justify-between">
                <div>
                    <CardTitle className="text-lg">API Keys</CardTitle>
                    <CardDescription>Secure tokens for external REST integration</CardDescription>
                </div>
                <Button variant="outline" size="sm"><Key className="w-4 h-4 mr-2"/> Generate Key</Button>
            </div>
          </CardHeader>
          <CardContent>
             <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Description</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead>Last Used</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow>
                  <TableCell className="font-medium">Zapier Sync Integration</TableCell>
                  <TableCell className="text-slate-500 text-sm">Oct 12, 2026</TableCell>
                  <TableCell className="text-slate-500 text-sm">2 mins ago</TableCell>
                </TableRow>
                 <TableRow>
                  <TableCell className="font-medium">Internal Dashboard App</TableCell>
                  <TableCell className="text-slate-500 text-sm">Aug 01, 2026</TableCell>
                  <TableCell className="text-slate-500 text-sm">Yesterday</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>

       <Card>
          <CardHeader>
            <CardTitle>Branding & Visuals</CardTitle>
            <CardDescription>
              Customize the look and feel of the Legent Studio for your tenant&apos;s users.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
              <div className="grid w-full max-w-sm items-center gap-1.5">
                <label htmlFor="theme" className="text-sm font-medium leading-none">Primary Theme Color (Hex)</label>
                <div className="flex items-center space-x-2">
                    <div className="w-8 h-8 rounded border bg-indigo-600 shadow-inner"></div>
                    <Input type="text" id="theme" placeholder="#4F46E5" defaultValue="#4F46E5" />
                </div>
              </div>
          </CardContent>
        </Card>
    </div>
  );
}
