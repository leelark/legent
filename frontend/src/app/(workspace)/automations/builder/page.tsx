"use client";
import JourneyBuilder, { JourneyNode } from '@/components/automation/JourneyBuilder';
import React, { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageChrome';
import { PlayCircle, Workflow } from 'lucide-react';
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
        return (
          <Card>
            <EmptyState
              type="error"
              title="Workflow ID required"
              description="Open the builder from a workflow row so the route includes an id query parameter."
            />
          </Card>
        );
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
        <div className="flex min-h-[calc(100vh-120px)] flex-col space-y-5">
            <PageHeader
              eyebrow="Journey builder"
              title="Workflow canvas"
              description="Design, publish, pause, resume, and manually trigger governed journey execution."
              action={
                <>
                  <Button icon={<PlayCircle className="h-4 w-4" />} onClick={handleActivate} disabled={activating} loading={activating}>
                    Activate Workflow
                  </Button>
                  <Button variant="secondary" onClick={handlePause} disabled={pausing} loading={pausing}>
                    Pause
                  </Button>
                  <Button variant="secondary" onClick={handleResume} disabled={resuming} loading={resuming}>
                    Resume
                  </Button>
                  <Button variant="secondary" onClick={handleManualTrigger} disabled={triggering} loading={triggering}>
                    Trigger
                  </Button>
                </>
              }
            />

            <Card className="flex min-h-[620px] flex-1 flex-col overflow-hidden !p-0">
              <div className="flex items-center justify-between border-b border-border-default bg-surface-secondary/70 px-5 py-3">
                <div className="flex items-center gap-3">
                  <span className="grid h-9 w-9 place-items-center rounded-xl border border-brand-500/20 bg-brand-500/10 text-brand-600 dark:text-brand-300">
                    <Workflow size={18} />
                  </span>
                  <div>
                    <p className="text-sm font-semibold text-content-primary">Canvas</p>
                    <p className="text-xs text-content-secondary">Workflow {workflowId}</p>
                  </div>
                </div>
                <span className="rounded-full border border-border-default bg-surface-elevated px-3 py-1 text-xs font-semibold text-content-secondary">
                  {nodes.length} nodes
                </span>
              </div>
              <div className="flex-1 bg-surface-secondary/35 p-4">
                <div className="h-full min-h-[540px] rounded-xl border border-border-default bg-surface-primary/70 p-4 shadow-inner">
                <JourneyBuilder nodes={nodes} onNodesChange={setNodes} workflowId={workflowId} />
                </div>
              </div>
            </Card>
        </div>
    );
}
