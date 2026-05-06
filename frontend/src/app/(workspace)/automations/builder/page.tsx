"use client";
import JourneyBuilder, { JourneyNode } from '@/components/automation/JourneyBuilder';
import React, { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Button } from '@/components/ui/Button';
import { Settings, PlayCircle } from 'lucide-react';
import { AnalyticsDashboard } from '@/components/tracking/AnalyticsDashboard';
import { FunnelDashboard } from '@/components/tracking/FunnelDashboard';
import { SegmentDashboard } from '@/components/tracking/SegmentDashboard';

export default function AutomationBuilder() {
    const searchParams = useSearchParams();
    const workflowId = searchParams.get('id');
    const [nodes, setNodes] = useState<JourneyNode[]>([]);

    if (!workflowId) {
        return <div className="p-8 text-center text-red-500">Workflow ID is required in the URL (?id=...)</div>;
    }

    return (
        <div className="space-y-6 h-[calc(100vh-120px)] flex flex-col">
            <div className="flex justify-between items-center bg-white p-4 rounded-md shadow-sm border">
                <div>
                    <h2 className="text-2xl font-bold tracking-tight">Journey Builder</h2>
                    <p className="text-sm text-muted-foreground mt-1">Design and automate your customer journey</p>
                                    </div>
                <div className="flex space-x-2">
                        <Button variant="outline"><Settings className="w-4 h-4 mr-2"/> Settings</Button>
                        <Button className="bg-green-600 hover:bg-green-700"><PlayCircle className="w-4 h-4 mr-2"/> Activate Workflow</Button>
                </div>
            </div>

                        <div className="flex-1 bg-slate-50 border rounded-xl overflow-hidden relative shadow-inner flex flex-col items-center justify-center">
                                <div className="w-full flex flex-row gap-8">
                                    <div className="flex-1">
                                        <JourneyBuilder nodes={nodes} onNodesChange={setNodes} workflowId={workflowId} />
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
