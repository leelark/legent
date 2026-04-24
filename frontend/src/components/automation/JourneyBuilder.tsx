'use client';

import React, { useRef, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Clock, Download, Edit, GitBranch, GripVertical, Plus, Send, Upload } from 'lucide-react';
import { NodeEditorModal } from './NodeEditorModal';
import apiClient from '@/lib/api-client';

export interface JourneyNode {
  id: string;
  type: 'TRIGGER' | 'SEND_EMAIL' | 'DELAY' | 'CONDITION' | 'END';
  label: string;
  config?: Record<string, unknown>;
  next?: string;
  branches?: { condition: string; target: string }[];
}

interface JourneyBuilderProps {
  nodes: JourneyNode[];
  onNodesChange: (nodes: JourneyNode[]) => void;
  workflowId?: string;
}

function JourneyBuilder({ nodes, onNodesChange, workflowId }: JourneyBuilderProps) {
  const [draggedIdx, setDraggedIdx] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingNode, setEditingNode] = useState<JourneyNode | null>(null);
  const [loading, setLoading] = useState(false);
  const dragOverIdx = useRef<number | null>(null);

  const resolveWorkflowId = () => {
    if (!workflowId || workflowId.trim().length === 0) {
      throw new Error('Workflow ID is required');
    }
    return workflowId;
  };

  const handleDragStart = (idx: number) => setDraggedIdx(idx);
  const handleDragEnter = (idx: number) => {
    dragOverIdx.current = idx;
  };

  const handleDragEnd = () => {
    if (draggedIdx !== null && dragOverIdx.current !== null && draggedIdx !== dragOverIdx.current) {
      const updated = [...nodes];
      const [removed] = updated.splice(draggedIdx, 1);
      if (removed) {
        updated.splice(dragOverIdx.current, 0, removed);
        onNodesChange(updated);
      }
    }

    setDraggedIdx(null);
    dragOverIdx.current = null;
  };

  const handleEdit = (node: JourneyNode) => {
    setEditingNode(node);
    setEditorOpen(true);
  };

  const handleSave = (updatedNode: JourneyNode) => {
    onNodesChange(nodes.map((node) => (node.id === updatedNode.id ? updatedNode : node)));
    setEditorOpen(false);
    setEditingNode(null);
  };

  const handleSaveToBackend = async () => {
    setLoading(true);
    try {
      await apiClient.post('/workflow-definitions', {
        workflowId: resolveWorkflowId(),
        version: 1,
        definition: JSON.stringify(nodes),
      });
    } catch {
      // Keep the builder responsive even if backend save fails.
    } finally {
      setLoading(false);
    }
  };

  const handleLoadFromBackend = async () => {
    setLoading(true);
    try {
      const response = await apiClient.get(`/workflow-definitions/${resolveWorkflowId()}/latest`);
      const definition = response?.data?.data?.definition;
      if (definition) {
        onNodesChange(JSON.parse(definition));
      }
    } catch {
      // Keep the current local draft if backend load fails.
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex w-full flex-col items-center">
      <div className="mb-4 flex gap-2">
        <Button size="sm" variant="outline" onClick={handleLoadFromBackend} disabled={loading}>
          <Download className="mr-1 h-4 w-4" /> Load
        </Button>
        <Button size="sm" variant="outline" onClick={handleSaveToBackend} disabled={loading}>
          <Upload className="mr-1 h-4 w-4" /> Save
        </Button>
      </div>

      {nodes.map((node, idx) => (
        <div
          key={node.id}
          draggable
          onDragStart={() => handleDragStart(idx)}
          onDragEnter={() => handleDragEnter(idx)}
          onDragEnd={handleDragEnd}
          className="mb-4 flex w-72 cursor-move flex-col items-center"
        >
          <Card className={`flex w-full flex-col items-center ${draggedIdx === idx ? 'opacity-60' : ''}`}>
            <div className="mb-2 flex w-full items-center justify-between gap-2">
              <span className="flex items-center gap-2">
                <GripVertical className="mr-1 h-4 w-4 text-slate-400" />
                {node.type === 'SEND_EMAIL' && <Send className="h-4 w-4 text-blue-500" />}
                {node.type === 'DELAY' && <Clock className="h-4 w-4 text-orange-500" />}
                {node.type === 'CONDITION' && <GitBranch className="h-4 w-4 text-purple-500" />}
                <span className="font-semibold">{node.label}</span>
              </span>
              <span className="flex gap-1">
                <Button size="icon" variant="ghost" onClick={() => handleEdit(node)}>
                  <Edit className="h-4 w-4" />
                </Button>
                <Button
                  size="icon"
                  variant="ghost"
                  onClick={() => onNodesChange(nodes.filter((_, i) => i !== idx))}
                  aria-label="Delete journey step"
                >
                  x
                </Button>
              </span>
            </div>
            <div className="mb-2 text-xs text-muted-foreground">{node.type}</div>
          </Card>
        </div>
      ))}

      <Button
        className="mt-4"
        onClick={() =>
          onNodesChange([
            ...nodes,
            { id: `node-${nodes.length + 1}`, type: 'SEND_EMAIL', label: 'Send Email' } as JourneyNode,
          ])
        }
      >
        <Plus className="mr-2 h-4 w-4" /> Add Step
      </Button>

      <NodeEditorModal
        open={editorOpen}
        node={editingNode}
        onClose={() => setEditorOpen(false)}
        onSave={handleSave}
      />
    </div>
  );
}

export default JourneyBuilder;
