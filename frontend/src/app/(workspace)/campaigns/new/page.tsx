'use client';

import { useState } from 'react';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import {
  ArrowRight, ArrowLeft, CheckCircle, Megaphone, Users, PaperPlaneTilt, RocketLaunch, CircleNotch
} from '@phosphor-icons/react';
import Link from 'next/link';
import { post } from '@/lib/api-client';
import { useRouter } from 'next/navigation';

const STEPS = ['Properties', 'Audience', 'Delivery', 'Review'];

export default function CampaignWizardPage() {
  const router = useRouter();
  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [campaign, setCampaign] = useState({
    name: '',
    subject: '',
    preheader: '',
    contentId: '',
    audienceIds: [] as string[]
  });

  const handleCreateAndSend = async () => {
    setLoading(true);
    try {
        // 1. Create Campaign
        const created = await post<any>('/campaigns', {
            name: campaign.name,
            subject: campaign.subject,
            contentId: campaign.contentId || 'default-template'
        });

        // 2. Trigger Send
        await post<any>(`/campaigns/${created.id}/send`, {});

        router.push('/campaigns');
    } catch (error) {
        console.error("Failed to send campaign", error);
    } finally {
        setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-content-primary">New Campaign</h1>
        <p className="mt-1 text-sm text-content-secondary">Configure and schedule your email send</p>
      </div>

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
                  <label className="mb-1 block text-sm font-medium text-content-primary">Campaign Name</label>
                  <Input 
                    placeholder="e.g., Spring Sale 2026" 
                    fullWidth 
                    value={campaign.name}
                    onChange={(e) => setCampaign({...campaign, name: e.target.value})}
                  />
               </div>
               <div>
                  <label className="mb-1 block text-sm font-medium text-content-primary">Email Subject</label>
                  <Input 
                    placeholder="Don't miss our biggest sale!" 
                    fullWidth 
                    value={campaign.subject}
                    onChange={(e) => setCampaign({...campaign, subject: e.target.value})}
                  />
               </div>
               <div>
                  <label className="mb-1 block text-sm font-medium text-content-primary">Content Template ID</label>
                  <Input 
                    placeholder="UUID of the content" 
                    fullWidth 
                    value={campaign.contentId}
                    onChange={(e) => setCampaign({...campaign, contentId: e.target.value})}
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
                <Button variant="ghost" size="sm" icon={<Users size={16} />}>Add Inclusion</Button>
              </div>
              <div className="rounded border border-dashed border-border-default p-4 text-center text-sm text-content-muted">
                {campaign.audienceIds.length > 0 ? `${campaign.audienceIds.length} Selected` : "No audience selected."}
              </div>
            </div>
          </div>
        )}

        {currentStep === 2 && (
          <div className="space-y-6">
            <CardHeader title="Delivery Configuration" subtitle="When should this be sent?" />
            <div className="grid grid-cols-2 gap-4">
                <div className="rounded-xl border-2 border-brand-500 bg-brand-50 p-6 flex flex-col items-center cursor-pointer dark:bg-brand-900/10">
                    <PaperPlaneTilt size={32} weight="duotone" className="text-brand-500 mb-2" />
                    <span className="font-semibold text-content-primary">Send Immediately</span>
                    <span className="text-sm text-content-muted text-center mt-1">Dispatches as soon as audience resolves</span>
                </div>
                <div className="rounded-xl border-2 border-border-default hover:border-brand-300 p-6 flex flex-col items-center cursor-pointer transition-colors">
                    <RocketLaunch size={32} weight="duotone" className="text-content-muted mb-2" />
                    <span className="font-semibold text-content-primary">Schedule for Later</span>
                    <span className="text-sm text-content-muted text-center mt-1">Pick a precise date and time</span>
                </div>
            </div>
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
                   <span className="text-content-secondary">Delivery</span>
                   <span className="font-medium text-content-primary">Immediate</span>
               </div>
            </div>
          </div>
        )}
      </Card>

      {/* Navigation */}
      <div className="flex justify-between">
        <Button
          variant="secondary"
          onClick={() => setCurrentStep(Math.max(0, currentStep - 1))}
          disabled={currentStep === 0 || loading}
          icon={<ArrowLeft size={16} />}
        >
          Back
        </Button>
        {currentStep < 3 ? (
            <Button
              onClick={() => setCurrentStep(Math.min(STEPS.length - 1, currentStep + 1))}
              icon={<ArrowRight size={16} />}
            >
              Next
            </Button>
        ) : (
            <Button
              onClick={handleCreateAndSend}
              disabled={loading || !campaign.name}
              icon={loading ? <CircleNotch size={16} className="animate-spin" /> : <PaperPlaneTilt size={16} />}
            >
              {loading ? 'Processing...' : 'Confirm & Launch'}
            </Button>
        )}
      </div>
    </div>
  );
}
