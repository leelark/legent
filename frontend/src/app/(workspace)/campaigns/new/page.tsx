'use client';

import { useState } from 'react';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import {
  ArrowRight, ArrowLeft, CheckCircle, Megaphone, Users, PaperPlaneTilt, RocketLaunch
} from '@phosphor-icons/react';
import Link from 'next/link';

const STEPS = ['Properties', 'Audience', 'Delivery', 'Review'];

export default function CampaignWizardPage() {
  const [currentStep, setCurrentStep] = useState(0);

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
                  <Input placeholder="e.g., Spring Sale 2026" fullWidth />
               </div>
               <div>
                  <label className="mb-1 block text-sm font-medium text-content-primary">Email Subject</label>
                  <Input placeholder="Don't miss our biggest sale!" fullWidth />
               </div>
               <div>
                  <label className="mb-1 block text-sm font-medium text-content-primary">Preheader Text</label>
                  <Input placeholder="Up to 50% off everything inside..." fullWidth />
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
                No audience selected. Click "Add Inclusion" to proceed.
              </div>
            </div>

            <div className="rounded-xl border border-border-default p-6 space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="font-semibold text-content-primary">Excluded Segments & Lists</h3>
                  <p className="text-sm text-content-muted">Subscribers who should NOT receive this email</p>
                </div>
                <Button variant="ghost" size="sm" icon={<Users size={16} />}>Add Exclusion</Button>
              </div>
              <div className="rounded border border-dashed border-border-default p-4 text-center text-sm text-content-muted">
                No exclusions configured.
              </div>
            </div>
            
            <div className="bg-surface-secondary/50 p-4 rounded-xl text-center">
                <span className="text-sm font-medium text-content-primary">Estimated Target: </span>
                <span className="text-lg font-bold text-brand-600">--</span>
                <Button variant="ghost" size="sm" className="ml-2">Calculate</Button>
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
                   <span className="font-medium text-content-primary">Spring Sale 2026</span>
               </div>
               <div className="flex justify-between py-2">
                   <span className="text-content-secondary">Audience Targets</span>
                   <span className="font-medium text-content-primary">0 Lists, 0 Segments</span>
               </div>
               <div className="flex justify-between py-2">
                   <span className="text-content-secondary">Delivery</span>
                   <span className="font-medium text-content-primary">Immediate</span>
               </div>
            </div>
            
            <div className="bg-red-50 dark:bg-red-900/10 p-4 rounded-xl text-center">
                <p className="text-sm text-danger font-medium">Please select at least one inclusion audience before sending.</p>
            </div>
          </div>
        )}
      </Card>

      {/* Navigation */}
      <div className="flex justify-between">
        <Button
          variant="secondary"
          onClick={() => setCurrentStep(Math.max(0, currentStep - 1))}
          disabled={currentStep === 0}
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
              disabled={true} // Enabled when review passes validation
              icon={<PaperPlaneTilt size={16} />}
            >
              Confirm Send
            </Button>
        )}
      </div>
    </div>
  );
}
