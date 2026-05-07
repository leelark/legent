"use client";
import React, { useCallback, useEffect, useState } from 'react';
import { getBranding, saveBranding, type Branding } from '@/lib/admin-api';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useToast } from '@/components/ui/Toast';
import { AdminPanel, AdminSkeletonRows } from '@/components/admin/AdminChrome';

const HEX_COLOR = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i;

function isValidHex(value?: string) {
  return !value || HEX_COLOR.test(value.trim());
}

export const BrandingPanel: React.FC = () => {
  const [branding, setBranding] = useState<Branding>({});
  const [edit, setEdit] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [logoError, setLogoError] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { addToast } = useToast();

  const primaryValid = isValidHex(branding.primaryColor);
  const secondaryValid = isValidHex(branding.secondaryColor);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setBranding(await getBranding());
    } catch (err: any) {
      setError(err?.normalized?.message || 'Failed to load branding.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleSave = async () => {
    if (!primaryValid || !secondaryValid) {
      setError('Brand colors must be valid hex values, for example #6B21A8.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const saved = await saveBranding({
        ...branding,
        primaryColor: branding.primaryColor?.trim(),
        secondaryColor: branding.secondaryColor?.trim(),
      });
      setBranding(saved);
      setEdit(false);
      addToast({ type: 'success', title: 'Branding saved', message: 'Admin branding is ready for runtime consumers.' });
    } catch (err: any) {
      const message = err?.normalized?.message || err?.message || 'Failed to save branding.';
      setError(message);
      addToast({ type: 'error', title: 'Branding save failed', message });
    } finally {
      setSaving(false);
    }
  };
  return (
    <AdminPanel
      title="Branding"
      subtitle="Persisted brand identity with live theme preview and hex validation."
      action={<Button size="sm" variant="secondary" onClick={() => setEdit((value) => !value)}>{edit ? 'Close Editor' : 'Edit Branding'}</Button>}
    >
      {loading ? (
        <AdminSkeletonRows rows={3} />
      ) : (
        <div className="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
          <div className="space-y-4">
            {error ? <div className="rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">{error}</div> : null}

            {edit ? (
              <div className="space-y-3">
                <Input label="Brand name" value={branding.name || ''} onChange={(e) => setBranding({ ...branding, name: e.target.value })} placeholder="Legent" />
                <Input label="Logo URL" value={branding.logoUrl || ''} onChange={(e) => { setLogoError(false); setBranding({ ...branding, logoUrl: e.target.value }); }} placeholder="https://..." />
                <div className="grid gap-3 sm:grid-cols-2">
                  <Input
                    label="Primary color"
                    value={branding.primaryColor || ''}
                    onChange={(e) => setBranding({ ...branding, primaryColor: e.target.value })}
                    placeholder="#6B21A8"
                    error={primaryValid ? undefined : 'Use a valid hex color'}
                  />
                  <Input
                    label="Secondary color"
                    value={branding.secondaryColor || ''}
                    onChange={(e) => setBranding({ ...branding, secondaryColor: e.target.value })}
                    placeholder="#DB2777"
                    error={secondaryValid ? undefined : 'Use a valid hex color'}
                  />
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button onClick={handleSave} loading={saving} disabled={!primaryValid || !secondaryValid}>Save Branding</Button>
                  <Button variant="secondary" onClick={load} disabled={saving}>Reset From Server</Button>
                </div>
              </div>
            ) : (
              <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
                <p className="text-xs uppercase tracking-wide text-content-muted">Current Identity</p>
                <div className="mt-4 flex items-center gap-4">
                  <div className="flex h-12 w-12 items-center justify-center overflow-hidden rounded-xl border border-border-default bg-surface-primary">
                    {branding.logoUrl && !logoError ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={branding.logoUrl} alt={`${branding.name || 'Brand'} logo`} className="h-full w-full object-contain p-1" onError={() => setLogoError(true)} />
                    ) : (
                      <span className="text-sm font-bold text-brand-600">{(branding.name || 'L').slice(0, 1).toUpperCase()}</span>
                    )}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-lg font-semibold text-content-primary">{branding.name || 'Legent'}</p>
                    <p className="text-xs text-content-muted">{branding.logoUrl ? 'Custom logo configured' : 'Using generated logo fallback'}</p>
                  </div>
                </div>
                <div className="mt-4 grid gap-2 sm:grid-cols-2">
                  {[['Primary', branding.primaryColor || '#6B21A8'], ['Secondary', branding.secondaryColor || '#DB2777']].map(([label, color]) => (
                    <div key={label} className="rounded-lg border border-border-default bg-surface-primary p-3">
                      <span className="block h-9 rounded-md border border-border-default" style={{ backgroundColor: color }} />
                      <p className="mt-2 text-xs font-semibold text-content-primary">{label}</p>
                      <p className="text-xs text-content-muted">{color}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div className="overflow-hidden rounded-2xl border border-border-default bg-surface-primary shadow-sm">
            <div className="p-5" style={{ background: `linear-gradient(135deg, ${branding.primaryColor || '#6B21A8'}, ${branding.secondaryColor || '#DB2777'})` }}>
              <p className="text-xs font-semibold uppercase tracking-wide text-white/80">Live Preview</p>
              <h3 className="mt-2 text-2xl font-semibold text-white">{branding.name || 'Legent'} Command Center</h3>
              <p className="mt-2 max-w-md text-sm text-white/80">This preview shows how saved branding reads against high-contrast admin surfaces.</p>
            </div>
            <div className="grid gap-3 p-5 sm:grid-cols-2">
              <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
                <p className="text-sm font-semibold text-content-primary">Primary action</p>
                <button className="mt-3 rounded-lg px-4 py-2 text-sm font-semibold text-white shadow-sm" style={{ backgroundColor: branding.primaryColor || '#6B21A8' }}>
                  Save changes
                </button>
              </div>
              <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
                <p className="text-sm font-semibold text-content-primary">Secondary accent</p>
                <div className="mt-3 h-2 rounded-full" style={{ backgroundColor: branding.secondaryColor || '#DB2777' }} />
                <p className="mt-3 text-xs text-content-secondary">Used for highlights, charts, and campaign emphasis.</p>
              </div>
            </div>
          </div>
        </div>
      )}
    </AdminPanel>
  );
};
