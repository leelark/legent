"use client";
import JourneyBuilder, { JourneyNode } from '@/components/automation/JourneyBuilder';
import React, { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Button } from '@/components/ui/Button';
import { PlayCircle } from 'lucide-react';
import { pauseWorkflow, publishWorkflow, resumeWorkflow, triggerWorkflow } from '@/lib/automation-api';
import { useToast } from '@/components/ui/Toast';

export default function AutomationBuilder() {
    const searchParams = useSearchParams();
    const workflowId = searchParams.get('id');
    const [nodes, setNodes] = useState<JourneyNode[]>([]);
    const [activating, setActivating] = useState(false);
    const [pausing, setPausing] = useState(false);
    const [resuming, setResuming] = useState(false);
    const [triggering, setTriggering] = useState(false);
    const { addToast } = useToast();

    if (!workflowId) {
        return <div className="p-8 text-center text-red-500">Workflow ID is required in the URL (?id=...)</div>;
    }

    const handleActivate = async () => {
      setActivating(true);
      try {
        await publishWorkflow(workflowId);
        addToast({ type: 'success', title: 'Workflow published', message: 'Workflow is now active.' });
      } catch (error: any) {
        addToast({ type: 'error', title: 'Publish failed', message: error?.normalized?.message || error?.response?.data?.error?.message || 'Unable to publish workflow.' });
      } finally {
        setActivating(false);
      }
    };

    const handlePause = async () => {
      setPausing(true);
      try {
        await pauseWorkflow(workflowId);
        addToast({ type: 'success', title: 'Workflow paused', message: 'Execution paused.' });
      } catch (error: any) {
        addToast({ type: 'error', title: 'Pause failed', message: error?.normalized?.message || error?.response?.data?.error?.message || 'Unable to pause workflow.' });
      } finally {
        setPausing(false);
      }
    };

    const handleResume = async () => {
      setResuming(true);
      try {
        await resumeWorkflow(workflowId);
        addToast({ type: 'success', title: 'Workflow resumed', message: 'Execution resumed.' });
      } catch (error: any) {
        addToast({ type: 'error', title: 'Resume failed', message: error?.normalized?.message || error?.response?.data?.error?.message || 'Unable to resume workflow.' });
      } finally {
        setResuming(false);
      }
    };

    const handleManualTrigger = async () => {
      setTriggering(true);
      try {
        await triggerWorkflow(workflowId, {
          subscriberId: `manual-${Date.now()}`,
          triggerSource: 'MANUAL',
          idempotencyKey: `manual-${workflowId}-${Date.now()}`,
        });
        addToast({ type: 'success', title: 'Workflow triggered', message: 'Manual run started.' });
      } catch (error: any) {
        addToast({ type: 'error', title: 'Trigger failed', message: error?.normalized?.message || error?.response?.data?.error?.message || 'Unable to trigger workflow.' });
      } finally {
        setTriggering(false);
      }
    };

    return (
        <div className="space-y-6 h-[calc(100vh-120px)] flex flex-col">
            <div className="flex justify-between items-center bg-white p-4 rounded-md shadow-sm border">
                <div>
                    <h2 className="text-2xl font-bold tracking-tight">Journey Builder</h2>
                    <p className="text-sm text-muted-foreground mt-1">Design and automate your customer journey</p>
                                    </div>
                <div className="flex space-x-2">
                        <Button className="bg-green-600 hover:bg-green-700" onClick={handleActivate} disabled={activating}>
                          <PlayCircle className="w-4 h-4 mr-2"/> {activating ? 'Activating...' : 'Activate Workflow'}
                        </Button>
                        <Button variant="secondary" onClick={handlePause} disabled={pausing}>
                          {pausing ? 'Pausing...' : 'Pause'}
                        </Button>
                        <Button variant="secondary" onClick={handleResume} disabled={resuming}>
                          {resuming ? 'Resuming...' : 'Resume'}
                        </Button>
                        <Button onClick={handleManualTrigger} disabled={triggering}>
                          {triggering ? 'Triggering...' : 'Trigger'}
                        </Button>
                </div>
            </div>

            <div className="flex-1 bg-slate-50 border rounded-xl overflow-hidden relative shadow-inner flex flex-col items-center justify-center">
              <div className="w-full p-6">
                <JourneyBuilder nodes={nodes} onNodesChange={setNodes} workflowId={workflowId} />
              </div>
            </div>
        </div>
    );
}
