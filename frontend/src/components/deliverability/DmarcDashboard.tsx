'use client';
import React, { useEffect, useState } from 'react';
import { getDmarcReports } from '@/lib/dmarc-api';
import { Card } from '@/components/ui/Card';

interface DmarcReport {
  id: number | string;
  reportType: string;
  receivedAt: string;
  parsedSummary: unknown;
}

export const DmarcDashboard: React.FC<{ domain: string }> = ({ domain }) => {
  const [reports, setReports] = useState<DmarcReport[]>([]);
  useEffect(() => {
    getDmarcReports(domain)
      .then((response) => setReports(response as DmarcReport[]))
      .catch(() => setReports([]));
  }, [domain]);

  if (!reports.length) return null;

  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">DMARC Reports: {domain}</h3>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr>
              <th>Type</th>
              <th>Received</th>
              <th>Summary</th>
            </tr>
          </thead>
          <tbody>
            {reports.map((r) => (
              <tr key={r.id}>
                <td>{r.reportType}</td>
                <td>{r.receivedAt}</td>
                <td><pre className="whitespace-pre-wrap max-w-xs">{JSON.stringify(r.parsedSummary, null, 2)}</pre></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
};
