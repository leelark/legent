'use client';
import React, { useEffect, useState } from 'react';
import { listDomains, addDomain, validateDomain } from '@/lib/deliverability-api';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export const DomainManager: React.FC = () => {
  const [domains, setDomains] = useState<any[]>([]);
  const [newDomain, setNewDomain] = useState('');
  const [loading, setLoading] = useState(false);

  const refresh = () => listDomains().then(setDomains);
  useEffect(() => { refresh(); }, []);

  const handleAdd = async () => {
    setLoading(true);
    await addDomain(newDomain);
    setNewDomain('');
    refresh();
    setLoading(false);
  };

  const handleValidate = async (domain: string) => {
    setLoading(true);
    await validateDomain(domain);
    refresh();
    setLoading(false);
  };

  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Domain Management</h3>
      <div className="flex gap-2 mb-4">
        <input value={newDomain} onChange={e => setNewDomain(e.target.value)} placeholder="yourdomain.com" className="border rounded px-2 py-1" />
        <Button onClick={handleAdd} disabled={loading || !newDomain}>Add</Button>
      </div>
      <table className="w-full text-sm">
        <thead>
          <tr>
            <th>Domain</th>
            <th>Status</th>
            <th>SPF</th>
            <th>DKIM</th>
            <th>DMARC</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {domains.map((d) => (
            <tr key={d.id || d.domainName || d.domain}>
              <td>{d.domainName || d.domain}</td>
              <td>{d.status || (d.isActive ? 'VERIFIED' : 'PENDING')}</td>
              <td>{String(d.spfVerified ?? d.spfStatus ?? false)}</td>
              <td>{String(d.dkimVerified ?? d.dkimStatus ?? false)}</td>
              <td>{String(d.dmarcVerified ?? d.dmarcStatus ?? false)}</td>
              <td>
                {(d.status !== 'VERIFIED' && !d.isActive) && (
                  <Button size="sm" onClick={() => handleValidate(d.id)} disabled={loading || !d.id}>
                    Validate
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
};
