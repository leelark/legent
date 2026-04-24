'use client';

import { useEffect, useState } from 'react';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import {
  ArrowRight, ArrowLeft, CheckCircle, Megaphone, Users, PaperPlaneTilt, RocketLaunch, CircleNotch
} from '@phosphor-icons/react';
import Link from 'next/link';
import { get, post } from '@/lib/api-client';
import { useRouter } from 'next/navigation';

const STEPS = ['Properties', 'Audience', 'Delivery', 'Review'];

interface AudienceItem {
  id: string;
  name: string;
  type: 'LIST' | 'SEGMENT';
}

export default function CampaignWizardPage() {
  const router = useRouter();
  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [campaign, setCampaign] = useState({
    name: '',
    subject: '',
    preheader: '',
    templateId: '',
    audiences: [] as { audienceType: 'LIST' | 'SEGMENT'; audienceId: string; action: 'INCLUDE' }[],
    scheduleType: 'NOW' as 'NOW' | 'LATER',
    scheduleTime: ''
  });
  const [availableAudiences, setAvailableAudiences] = useState<AudienceItem[]>([]);
  const [showAudiencePicker, setShowAudiencePicker] = useState(false);

  useEffect(() => {
    get<any>('/lists').then((res) => {
      const lists = (Array.isArray(res) ? res : res?.content || []).map((l: any) => ({ id: l.id, name: l.name || l.id, type: 'LIST' as const }));
      get<any>('/segments').then((segRes) => {
        const segments = (Array.isArray(segRes) ? segRes : segRes?.content || []).map((s: any) => ({ id: s.id, name: s.name || s.id, type: 'SEGMENT' as const }));
        setAvailableAudiences([...lists, ...segments]);
      }).catch(() => setAvailableAudiences(lists));
    }).catch(() => {});
  }, []);

  const handleCreateAndSend = async () => {
    setLoading(true);
    setError(null);
    try {
        const created = await post<any>('/campaigns', {
            name: campaign.name,
            subject: campaign.subject,
            preheader: campaign.preheader,
            templateId: campaign.templateId || undefined,
            type: 'STANDARD',
            audiences: campaign.audiences
        });

        if (!created || !created.id) {
          throw new Error('Campaign creation failed');
        }

        const scheduleAt = campaign.scheduleType === 'LATER' && campaign.scheduleTime ? new Date(campaign.scheduleTime).toISOString() : null;
        await post<any>(`/campaigns/${created.id}/send`, { scheduledAt: scheduleAt });

        router.push('/campaigns');
    } catch (error: any) {
        const msg = error?.response?.data?.error?.message || error?.message || 'Failed to send campaign';
        setError(msg);
        console.error("Failed to send campaign", error);
    } finally {
        setLoading(false);
    }
  };

  const handleSaveDraft = async () => {
    setLoading(true);
    setError(null);
    try {
        await post<any>('/campaigns', {
            name: campaign.name,
            subject: campaign.subject,
            preheader: campaign.preheader,
            templateId: campaign.templateId || undefined,
            type: 'STANDARD',
            audiences: campaign.audiences
        });
        router.push('/campaigns');
    } catch (error: any) {
        const msg = error?.response?.data?.error?.message || error?.message || 'Failed to save campaign';
        setError(msg);
        console.error("Failed to save campaign", error);
    } finally {
        setLoading(false);
    }
  };

  const toggleAudience = (item: AudienceItem) => {
    const exists = campaign.audiences.find(a => a.audienceId === item.id);
    if (exists) {
      setCampaign({ ...campaign, audiences: campaign.audiences.filter(a => a.audienceId !== item.id) });
    } else {
      setCampaign({ ...campaign, audiences: [...campaign.audiences, { audienceType: item.type, audienceId: item.id, action: 'INCLUDE' as const }] });
    }
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-content-primary">New Campaign</h1>
        <p className="mt-1 text-sm text-content-secondary">Configure and schedule your email send</p>
      </div>

      {error && (
        <div className="rounded-lg bg-danger/10 border border-danger/20 p-3 text-sm text-danger">
          {error}
        </div>
      )}

      <div className="flex items-center justify-center gap-2">
        {STEPS.map((step, i) => (
          <div key={step} className="flex items-center gap-2">
            <div className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold transition-all ${
              i < currentStep
                ? 'bg-brand-500 text-white'
                : i === currentStep
                  ? 'bg-brand-100 text-brand-700 border-2 border-brand-500 dark:bg-brand-900/30 dark:text-brand-400'
                  : 'bg-surface-secondary text-content-muted'
            }`}>
              {i < currentStep ? <CheckCircle size={16} weight="fill" /> : i + 1}
            </div>
            <span className={`text-sm font-medium ${i === currentStep ? 'text-content-primary' : 'text-content-muted'}`}>
              {step}
            </span>
            {i < STEPS.length - 1 && (
              <div className={`h-px w-8 sm:w-16 ${i < currentStep ? 'bg-brand-500' : 'bg-border-default'}`} />
            )}
          </div>
        ))}
      </div>

      <Card>
        {currentStep === 0 && (
          <div className="space-y-6">
            <CardHeader title="Properties" subtitle="Basic campaign details" />
            <div className="space-y-4">
               <div>
                  <label className="mb-1 block text-sm font-medium text-content-primary">Campaign Name *</label>
                  <Input 
                    placeholder="e.g., Spring Sale 2026" 
                    fullWidth 
                    value={campaign.name}
                    onChange={(e) => setCampaign({...campaign, name: e.target.value})}
                  />
               </div>
               <div>
                  <label className="mb-1 block text-sm font-medium text-content-primary">Email Subject *</label>
                  <Input 
                    placeholder="Don't miss our biggest sale!" 
                    fullWidth 
                    value={campaign.subject}
                    onChange={(e) => setCampaign({...campaign, subject: e.target.value})}
                  />
               </div>
               <div>
                  <label className="mb-1 block text-sm font-medium text-content-primary">Preheader</label>
                  <Input 
                    placeholder="Short preview text..." 
                    fullWidth 
                    value={campaign.preheader}
                    onChange={(e) => setCampaign({...campaign, preheader: e.target.value})}
                  />
               </div>
               <div>
                  <label className="mb-1 block text-sm font-medium text-content-primary">Template ID</label>
                  <Input 
                    placeholder="UUID of the template" 
                    fullWidth 
                    value={campaign.templateId}
                    onChange={(e) => setCampaign({...campaign, templateId: e.target.value})}
                  />
               </div>
            </div>
          </div>
        )}

        {currentStep === 1 && (
          <div className="space-y-6">
            <CardHeader title="Audience Selection" subtitle="Who should receive this email?" />
            <div className="rounded-xl border border-border-default p-6 space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="font-semibold text-content-primary">Included Segments & Lists</h3>
                  <p className="text-sm text-content-muted">Target audience for this send</p>
                </div>
                <Button variant="ghost" size="sm" icon={<Users size={16} />} onClick={() => setShowAudiencePicker(!showAudiencePicker)}>
                  {showAudiencePicker ? 'Done' : 'Add Inclusion'}
                </Button>
              </div>
              {showAudiencePicker && (
                <div className="max-h-60 overflow-auto rounded border border-border-default bg-surface-secondary p-2 space-y-1">
                  {availableAudiences.length === 0 && <p className="text-sm text-content-muted p-2">No audiences available</p>}
                  {availableAudiences.map((item) => (
                    <label key={item.id} className="flex items-center gap-2 p-2 hover:bg-surface-tertiary rounded cursor-pointer">
                      <input
                        type="checkbox"
                        checked={!!campaign.audiences.find(a => a.audienceId === item.id)}
                        onChange={() => toggleAudience(item)}
                      />
                      <span className="text-sm text-content-primary">{item.name}</span>
                      <span className="text-xs text-content-muted uppercase">{item.type}</span>
                    </label>
                  ))}
                </div>
              )}
              <div className="rounded border border-dashed border-border-default p-4 text-center text-sm text-content-muted">
                {campaign.audiences.length > 0 ? `${campaign.audiences.length} Selected` : "No audience selected."}
              </div>
              {campaign.audiences.length > 0 && (
                <div className="flex flex-wrap gap-2">
                  {campaign.audiences.map((a) => (
                    <span key={a.audienceId} className="inline-flex items-center gap-1 rounded bg-brand-50 px-2 py-1 text-xs text-brand-700">
                      {a.audienceType}: {a.audienceId}
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {currentStep === 2 && (
          <div className="space-y-6">
            <CardHeader title="Delivery Configuration" subtitle="When should this be sent?" />
            <div className="grid grid-cols-2 gap-4">
                <div 
                  className={`rounded-xl border-2 p-6 flex flex-col items-center cursor-pointer transition-colors ${campaign.scheduleType === 'NOW' ? 'border-brand-500 bg-brand-50 dark:bg-brand-900/10' : 'border-border-default hover:border-brand-300'}`}
                  onClick={() => setCampaign({...campaign, scheduleType: 'NOW'})}
                >
                    <PaperPlaneTilt size={32} weight="duotone" className={`mb-2 ${campaign.scheduleType === 'NOW' ? 'text-brand-500' : 'text-content-muted'}`} />
                    <span className="font-semibold text-content-primary">Send Immediately</span>
                    <span className="text-sm text-content-muted text-center mt-1">Dispatches as soon as audience resolves</span>
                </div>
                <div 
                  className={`rounded-xl border-2 p-6 flex flex-col items-center cursor-pointer transition-colors ${campaign.scheduleType === 'LATER' ? 'border-brand-500 bg-brand-50 dark:bg-brand-900/10' : 'border-border-default hover:border-brand-300'}`}
                  onClick={() => setCampaign({...campaign, scheduleType: 'LATER'})}
                >
                    <RocketLaunch size={32} weight="duotone" className={`mb-2 ${campaign.scheduleType === 'LATER' ? 'text-brand-500' : 'text-content-muted'}`} />
                    <span className="font-semibold text-content-primary">Schedule for Later</span>
                    <span className="text-sm text-content-muted text-center mt-1">Pick a precise date and time</span>
                </div>
            </div>
            {campaign.scheduleType === 'LATER' && (
              <div className="space-y-2">
                <label className="block text-sm font-medium text-content-primary">Schedule Time</label>
                <input
                  type="datetime-local"
                  value={campaign.scheduleTime}
                  onChange={(e) => setCampaign({...campaign, scheduleTime: e.target.value})}
                  className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-all"
                />
              </div>
            )}
          </div>
        )}

        {currentStep === 3 && (
          <div className="space-y-6">
            <CardHeader title="Review & Confirm" subtitle="Double check your settings before dispatch" />
            <div className="space-y-4 divide-y divide-border-default text-sm">
               <div className="flex justify-between py-2">
                   <span className="text-content-secondary">Campaign Name</span>
                   <span className="font-medium text-content-primary">{campaign.name || 'Untitled'}</span>
               </div>
               <div className="flex justify-between py-2">
                   <span className="text-content-secondary">Subject</span>
                   <span className="font-medium text-content-primary">{campaign.subject || 'No Subject'}</span>
               </div>
               <div className="flex justify-between py-2">
                   <span className="text-content-secondary">Audiences</span>
                   <span className="font-medium text-content-primary">{campaign.audiences.length} selected</span>
               </div>
               <div className="flex justify-between py-2">
                   <span className="text-content-secondary">Delivery</span>
                   <span className="font-medium text-content-primary">{campaign.scheduleType === 'NOW' ? 'Immediate' : campaign.scheduleTime ? `Scheduled: ${campaign.scheduleTime}` : 'Schedule not set'}</span>
               </div>
            </div>
          </div>
        )}
      </Card>

      {/* Navigation */}
      <div className="flex justify-between items-center">
        <Button
          variant="secondary"
          onClick={() => setCurrentStep(Math.max(0, currentStep - 1))}
          disabled={currentStep === 0 || loading}
          icon={<ArrowLeft size={16} />}
        >
          Back
        </Button>
        <div className="flex gap-3">
          {currentStep === 3 && (
            <Button
              variant="secondary"
              onClick={handleSaveDraft}
              disabled={loading || !campaign.name}
            >
              Save as Draft
            </Button>
          )}
          {currentStep < 3 ? (
              <Button
                onClick={() => setCurrentStep(Math.min(STEPS.length - 1, currentStep + 1))}
                disabled={loading}
                icon={<ArrowRight size={16} />}
              >
                Next
              </Button>
          ) : (
              <Button
                onClick={handleCreateAndSend}
                disabled={loading || !campaign.name || !campaign.subject || campaign.audiences.length === 0 || (campaign.scheduleType === 'LATER' && !campaign.scheduleTime)}
                icon={loading ? <CircleNotch size={16} className="animate-spin" /> : <PaperPlaneTilt size={16} />}
              >
                {loading ? 'Processing...' : 'Confirm & Launch'}
              </Button>
          )}
        </div>
      </div>
    </div>
  );
}
