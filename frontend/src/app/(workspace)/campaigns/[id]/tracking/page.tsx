'use client';

import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';

export default function CampaignTrackingPage() {
  return (
    <div className="space-y-6">
      <div>
        <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-content-primary">Spring Sale 2026</h1>
            <Badge variant="warning">SENDING</Badge>
        </div>
        <p className="mt-1 text-sm text-content-secondary">Live send orchestration tracking</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <Card>
              <p className="text-sm text-content-secondary uppercase tracking-wider font-semibold">Total Target</p>
              <p className="text-2xl font-bold text-content-primary mt-1">450,000</p>
          </Card>
          <Card>
              <p className="text-sm text-content-secondary uppercase tracking-wider font-semibold">Sent</p>
              <p className="text-2xl font-bold text-brand-600 mt-1">125,400</p>
          </Card>
          <Card>
              <p className="text-sm text-content-secondary uppercase tracking-wider font-semibold">Failed</p>
              <p className="text-2xl font-bold text-danger mt-1">210</p>
          </Card>
          <Card>
              <p className="text-sm text-content-secondary uppercase tracking-wider font-semibold">Suppressed</p>
              <p className="text-2xl font-bold text-amber-500 mt-1">1,024</p>
          </Card>
      </div>
      
      <Card>
          <CardHeader title="Overall Progress" />
          <div className="py-4">
              <div className="flex justify-between text-sm mb-2 font-medium">
                  <span className="text-brand-600">27% Completed</span>
                  <span className="text-content-muted">Processing...</span>
              </div>
              <div className="h-3 w-full rounded-full bg-surface-secondary overflow-hidden">
                <div className="h-full rounded-full bg-gradient-to-r from-brand-500 to-brand-400 animate-pulse w-[27%]" />
              </div>
          </div>
      </Card>
      
      <Card>
          <CardHeader title="Batch Flow (Domain Grouping)" />
          <div className="space-y-4 py-4">
              {['gmail.com', 'yahoo.com', 'outlook.com', 'corporate.io'].map(domain => (
                  <div key={domain} className="flex items-center gap-4">
                      <span className="w-32 text-sm font-medium">{domain}</span>
                      <div className="flex-1 h-2 rounded-full bg-surface-secondary overflow-hidden">
                          <div className="h-full rounded-full bg-brand-500" style={{ width: `${Math.random() * 100}%` }} />
                      </div>
                      <span className="w-16 text-xs text-right text-content-muted">Processing</span>
                  </div>
              ))}
          </div>
      </Card>
    </div>
  );
}
