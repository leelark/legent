"use client";
import { DomainManager } from '@/components/deliverability/DomainManager';
import { ReputationDashboard } from '@/components/deliverability/ReputationDashboard';
import { DmarcDashboard } from '@/components/deliverability/DmarcDashboard';
import JourneyBuilder, { JourneyNode } from '@/components/automation/JourneyBuilder';
import React, { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Settings, PlayCircle } from 'lucide-react';
import { AnalyticsDashboard } from '@/components/tracking/AnalyticsDashboard';
import { FunnelDashboard } from '@/components/tracking/FunnelDashboard';
import { SegmentDashboard } from '@/components/tracking/SegmentDashboard';

export default function AutomationBuilder() {
    const [nodes, setNodes] = useState<JourneyNode[]>([
        { id: 'node-1', type: 'TRIGGER', label: 'Entry Trigger' },
        { id: 'node-2', type: 'SEND_EMAIL', label: 'Send Welcome Email', config: { campaignId: 'welcome' }, next: 'node-3' },
        { id: 'node-3', type: 'DELAY', label: 'Wait 2 Days', config: { minutes: 2880 }, next: 'node-4' },
        { id: 'node-4', type: 'CONDITION', label: 'If Opened', branches: [ { condition: "opened == true", target: 'node-5' }, { condition: "opened == false", target: 'node-6' } ] },
        { id: 'node-5', type: 'SEND_EMAIL', label: 'Send Engaged Email', config: { campaignId: 'engaged' }, next: 'node-7' },
        { id: 'node-6', type: 'END', label: 'End' },
        { id: 'node-7', type: 'END', label: 'End' },
    ]);

    return (
        <div className="space-y-6 h-[calc(100vh-120px)] flex flex-col">
            <div className="flex justify-between items-center bg-white p-4 rounded-md shadow-sm border">
                <div>
                    <h2 className="text-2xl font-bold tracking-tight">Journey Builder</h2>
                    <p className="text-sm text-muted-foreground mt-1">Design and automate your customer journey</p>
                                        <DomainManager />
                                        <ReputationDashboard domain="yourdomain.com" />
                                        <DmarcDashboard domain="yourdomain.com" />
                                    </div>
                <div className="flex space-x-2">
                        <Button variant="outline"><Settings className="w-4 h-4 mr-2"/> Settings</Button>
                        <Button className="bg-green-600 hover:bg-green-700"><PlayCircle className="w-4 h-4 mr-2"/> Activate Workflow</Button>
                </div>
            </div>

                        <div className="flex-1 bg-slate-50 border rounded-xl overflow-hidden relative shadow-inner flex flex-col items-center justify-center">
                                <div className="w-full flex flex-row gap-8">
                                    <div className="flex-1">
                                        <JourneyBuilder nodes={nodes} onNodesChange={setNodes} workflowId={"demo-workflow"} />
                                    </div>
                                    <div className="w-[480px] min-w-[320px] max-w-[520px] flex flex-col gap-4">
                                        <AnalyticsDashboard />
                                        <FunnelDashboard campaignId="welcome" />
                                        <SegmentDashboard field="audienceId" value="default" />
                                    </div>
                                </div>
                        </div>
        </div>
    );
}
