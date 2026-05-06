'use client';

import React, { useRef, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Clock, Download, Edit, GitBranch, GripVertical, Plus, Send, Upload } from 'lucide-react';
import { NodeEditorModal } from './NodeEditorModal';
import { getLatestWorkflowDefinition, saveWorkflowDefinition } from '@/lib/automation-api';

export interface JourneyNode {
  id: string;
  type:
    | 'ENTRY_TRIGGER'
    | 'SEND_EMAIL'
    | 'DELAY'
    | 'CONDITION'
    | 'BRANCH'
    | 'SPLIT'
    | 'JOIN'
    | 'WEBHOOK'
    | 'UPDATE_FIELD'
    | 'ADD_TAG'
    | 'REMOVE_TAG'
    | 'SUPPRESS_CONTACT'
    | 'WAIT_UNTIL'
    | 'PAUSE'
    | 'EXIT_GOAL'
    | 'REENTRY_GATE'
    | 'EVENT_LISTENER'
    | 'END';
  label: string;
  config?: Record<string, unknown>;
  next?: string;
  branches?: { condition: string; target: string }[];
}

interface WorkflowGraph {
  graphVersion: 2;
  initialNodeId: string;
  nodes: Record<string, {
    id: string;
    type: JourneyNode['type'];
    configuration: Record<string, unknown>;
    nextNodeId?: string;
    branches?: { condition: string; targetNodeId: string }[];
  }>;
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
      const graph = toGraph(nodes);
      await saveWorkflowDefinition(resolveWorkflowId(), graph);
    } catch {
      // Keep the builder responsive even if backend save fails.
    } finally {
      setLoading(false);
    }
  };

  const handleLoadFromBackend = async () => {
    setLoading(true);
    try {
      const workflowId = resolveWorkflowId();
      const latest = await getLatestWorkflowDefinition(workflowId);
      const parsed = toDefinition(latest);
      if (parsed) {
        onNodesChange(fromGraph(parsed));
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
                {(node.type === 'CONDITION' || node.type === 'BRANCH' || node.type === 'SPLIT') && <GitBranch className="h-4 w-4 text-purple-500" />}
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
            { id: `node-${nodes.length + 1}`, type: 'SEND_EMAIL', label: 'Send Email', config: {} } as JourneyNode,
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

function toGraph(nodes: JourneyNode[]): WorkflowGraph {
  if (nodes.length === 0) {
    return {
      graphVersion: 2,
      initialNodeId: 'node-1',
      nodes: {
        'node-1': {
          id: 'node-1',
          type: 'END',
          configuration: {},
        },
      },
    };
  }

  const graphNodes: WorkflowGraph['nodes'] = {};
  for (let i = 0; i < nodes.length; i += 1) {
    const node = nodes[i];
    const nextNode = node.next ?? nodes[i + 1]?.id;
    graphNodes[node.id] = {
      id: node.id,
      type: node.type,
      configuration: node.config ?? {},
      nextNodeId: nextNode,
      branches: (node.branches ?? []).map((branch) => ({
        condition: branch.condition,
        targetNodeId: branch.target,
      })),
    };
  }

  return {
    graphVersion: 2,
    initialNodeId: nodes[0]?.id ?? 'node-1',
    nodes: graphNodes,
  };
}

function fromGraph(graph: any): JourneyNode[] {
  if (!graph || !graph.nodes || typeof graph.nodes !== 'object') {
    return [];
  }
  const nodesMap = graph.nodes as Record<string, any>;
  const list: JourneyNode[] = Object.values(nodesMap).map((node: any) => ({
    id: node.id,
    type: node.type,
    label: node.label ?? defaultLabel(node.type),
    config: node.configuration ?? {},
    next: node.nextNodeId,
    branches: (node.branches ?? []).map((branch: any) => ({
      condition: branch.condition ?? '',
      target: branch.targetNodeId,
    })),
  }));

  const initialId = graph.initialNodeId;
  if (!initialId) {
    return list;
  }

  const ordered: JourneyNode[] = [];
  const visited = new Set<string>();
  let current = initialId;
  while (current && !visited.has(current)) {
    const node = list.find((item) => item.id === current);
    if (!node) {
      break;
    }
    visited.add(node.id);
    ordered.push(node);
    current = node.next;
  }
  for (const node of list) {
    if (!visited.has(node.id)) {
      ordered.push(node);
    }
  }
  return ordered;
}

function defaultLabel(type: JourneyNode['type']) {
  const labels: Record<JourneyNode['type'], string> = {
    ENTRY_TRIGGER: 'Entry Trigger',
    SEND_EMAIL: 'Send Email',
    DELAY: 'Delay',
    CONDITION: 'Condition',
    BRANCH: 'Branch',
    SPLIT: 'Split',
    JOIN: 'Join',
    WEBHOOK: 'Webhook',
    UPDATE_FIELD: 'Update Field',
    ADD_TAG: 'Add Tag',
    REMOVE_TAG: 'Remove Tag',
    SUPPRESS_CONTACT: 'Suppress Contact',
    WAIT_UNTIL: 'Wait Until',
    PAUSE: 'Pause',
    EXIT_GOAL: 'Exit Goal',
    REENTRY_GATE: 'Re-entry Gate',
    EVENT_LISTENER: 'Event Listener',
    END: 'End',
  };
  return labels[type] ?? type;
}

export default JourneyBuilder;

function toDefinition(latestVersion: any) {
  const rawDefinition = latestVersion?.definition ?? latestVersion?.graph ?? latestVersion;
  if (!rawDefinition) {
    return null;
  }
  return typeof rawDefinition === 'string' ? JSON.parse(rawDefinition) : rawDefinition;
}
