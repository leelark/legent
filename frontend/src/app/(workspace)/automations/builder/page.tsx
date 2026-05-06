"use client";
import JourneyBuilder, { JourneyNode } from '@/components/automation/JourneyBuilder';
import React, { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Button } from '@/components/ui/Button';
import { PlayCircle } from 'lucide-react';
import apiClient from '@/lib/api-client';

export default function AutomationBuilder() {
    const searchParams = useSearchParams();
    const workflowId = searchParams.get('id');
    const [nodes, setNodes] = useState<JourneyNode[]>([]);
    const [activating, setActivating] = useState(false);

    if (!workflowId) {
        return <div className="p-8 text-center text-red-500">Workflow ID is required in the URL (?id=...)</div>;
    }

    const handleActivate = async () => {
      setActivating(true);
      try {
        await apiClient.post(`/workflows/${workflowId}/publish`, {});
      } finally {
        setActivating(false);
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
