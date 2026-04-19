"use client";
// ...existing code...
"use client";
import React, { useEffect, useState } from 'react';
import { getBranding, saveBranding } from '@/lib/admin-api';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export const BrandingPanel: React.FC = () => {
  const [branding, setBranding] = useState<any>({});
  const [edit, setEdit] = useState(false);
  useEffect(() => { getBranding().then(setBranding); }, []);
  const handleSave = async () => {
    await saveBranding(branding);
    setEdit(false);
    getBranding().then(setBranding);
  };
  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Branding</h3>
      {edit ? (
        <div className="flex flex-col gap-2 mb-2">
          <input value={branding.name || ''} onChange={e => setBranding({ ...branding, name: e.target.value })} placeholder="Name" className="border rounded px-2 py-1" />
          <input value={branding.logoUrl || ''} onChange={e => setBranding({ ...branding, logoUrl: e.target.value })} placeholder="Logo URL" className="border rounded px-2 py-1" />
          <input value={branding.primaryColor || ''} onChange={e => setBranding({ ...branding, primaryColor: e.target.value })} placeholder="Primary Color" className="border rounded px-2 py-1" />
          <input value={branding.secondaryColor || ''} onChange={e => setBranding({ ...branding, secondaryColor: e.target.value })} placeholder="Secondary Color" className="border rounded px-2 py-1" />
          <Button onClick={handleSave}>Save</Button>
        </div>
      ) : (
        <div className="flex items-center gap-4 mb-2">
          {branding.logoUrl && <img src={branding.logoUrl} alt="logo" className="h-8" />}
          <span className="font-bold text-lg">{branding.name}</span>
          <span className="rounded px-2" style={{ background: branding.primaryColor }}>{branding.primaryColor}</span>
          <span className="rounded px-2" style={{ background: branding.secondaryColor }}>{branding.secondaryColor}</span>
          <Button size="sm" onClick={() => setEdit(true)}>Edit</Button>
        </div>
      )}
    </Card>
  );
};
