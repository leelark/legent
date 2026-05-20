'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Clock, Download, Edit, GitBranch, GripVertical, Plus, Send, Upload } from 'lucide-react';
import { NodeEditorModal } from './NodeEditorModal';
import {
  getWorkflowCapabilities,
  getLatestWorkflowDefinition,
  saveWorkflowDefinition,
  validateWorkflow,
  type WorkflowDefinitionVersion,
  type WorkflowGraphDefinition,
} from '@/lib/automation-api';
import {
  isJourneyNodeType,
  journeyNodeLabel,
  RUNTIME_SUPPORTED_JOURNEY_NODE_TYPES,
  type JourneyNode,
  type JourneyNodeType,
} from './journey-node-contract';

export type { JourneyNode } from './journey-node-contract';

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
  showDraftNodeTypes?: boolean;
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

function JourneyBuilder({ nodes, onNodesChange, workflowId, showDraftNodeTypes = true }: JourneyBuilderProps) {
  const [draggedIdx, setDraggedIdx] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingNode, setEditingNode] = useState<JourneyNode | null>(null);
  const [loading, setLoading] = useState(false);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [runtimeSupportedTypes, setRuntimeSupportedTypes] = useState<readonly JourneyNodeType[]>(
    RUNTIME_SUPPORTED_JOURNEY_NODE_TYPES
  );
  const dragOverIdx = useRef<number | null>(null);
  const runtimeSupportedTypeSet = useMemo(() => new Set(runtimeSupportedTypes), [runtimeSupportedTypes]);
  const isNodeRuntimeSupported = (type: JourneyNodeType) => runtimeSupportedTypeSet.has(type);

  useEffect(() => {
    if (!workflowId) {
      setRuntimeSupportedTypes(RUNTIME_SUPPORTED_JOURNEY_NODE_TYPES);
      return;
    }

    let cancelled = false;
    getWorkflowCapabilities(workflowId)
      .then((response) => {
        const supportedTypes = response.capabilities?.runtimeSupportedNodeTypes?.filter(isJourneyNodeType);
        if (!cancelled && supportedTypes && supportedTypes.length > 0) {
          setRuntimeSupportedTypes(supportedTypes);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRuntimeSupportedTypes(RUNTIME_SUPPORTED_JOURNEY_NODE_TYPES);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [workflowId]);

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
      if (!showDraftNodeTypes) {
        const unsupportedNodes = nodes.filter((node) => !isNodeRuntimeSupported(node.type));
        if (unsupportedNodes.length > 0) {
          setValidationErrors(unsupportedNodes.map((node) => `${node.label} uses ${node.type}, which requires Advanced mode before saving.`));
          return;
        }
      }
      const graph = buildWorkflowGraph(nodes);
      const validation = await validateWorkflow(resolveWorkflowId(), graph);
      setValidationErrors(validation.errors ?? []);
      await saveWorkflowDefinition(resolveWorkflowId(), graph);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Workflow definition could not be saved.';
      setValidationErrors([message]);
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
                {!isNodeRuntimeSupported(node.type) && (
                  <span className="rounded-full border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-200">
                    Draft only
                  </span>
                )}
              </span>
              <span className="flex gap-1">
                <Button size="icon" variant="ghost" onClick={() => handleEdit(node)} aria-label={`Edit journey step ${node.label}`}>
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
            <div className="mb-2 text-xs text-content-secondary">{node.type}</div>
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

      {validationErrors.length > 0 && (
        <div className="mt-4 w-full max-w-xl rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-800 dark:text-amber-100">
          <p className="font-semibold">Cannot be published until live runtime support is added.</p>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            {validationErrors.map((error) => (
              <li key={error}>{error}</li>
            ))}
          </ul>
        </div>
      )}

      <NodeEditorModal
        open={editorOpen}
        node={editingNode}
        runtimeSupportedTypes={runtimeSupportedTypes}
        showDraftNodeTypes={showDraftNodeTypes}
        onClose={() => setEditorOpen(false)}
        onSave={handleSave}
      />
    </div>
  );
}

export function buildWorkflowGraph(nodes: JourneyNode[]): WorkflowGraph {
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

function fromGraph(graph: WorkflowGraphDefinition): JourneyNode[] {
  if (!isRecord(graph.nodes)) {
    return [];
  }
  const list: JourneyNode[] = Object.values(graph.nodes).flatMap((node) => {
    const parsed = toJourneyNode(node);
    return parsed ? [parsed] : [];
  });

  const initialId = typeof graph.initialNodeId === 'string' ? graph.initialNodeId : undefined;
  if (!initialId) {
    return list;
  }

  const ordered: JourneyNode[] = [];
  const visited = new Set<string>();
  let current: string | undefined = initialId;
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

function toJourneyNode(node: unknown): JourneyNode | null {
  if (!isRecord(node)) {
    return null;
  }

  const { id, type, label, configuration, nextNodeId, branches } = node;
  if (typeof id !== 'string' || !isJourneyNodeType(type)) {
    return null;
  }

  return {
    id,
    type,
    label: typeof label === 'string' && label.length > 0 ? label : journeyNodeLabel(type),
    config: isRecord(configuration) ? configuration : {},
    next: typeof nextNodeId === 'string' ? nextNodeId : undefined,
    branches: parseBranches(branches),
  };
}

function parseBranches(branches: unknown): JourneyNode['branches'] {
  if (!Array.isArray(branches)) {
    return [];
  }

  return branches.flatMap((branch) => {
    if (!isRecord(branch)) {
      return [];
    }
    return [{
      condition: typeof branch.condition === 'string' ? branch.condition : '',
      target: typeof branch.targetNodeId === 'string' ? branch.targetNodeId : '',
    }];
  });
}

export default JourneyBuilder;

function toDefinition(latestVersion: WorkflowDefinitionVersion | null | undefined) {
  const rawDefinition = latestVersion?.definition ?? latestVersion?.graph ?? latestVersion;
  if (!rawDefinition) {
    return null;
  }
  if (typeof rawDefinition === 'string') {
    const parsed: unknown = JSON.parse(rawDefinition);
    return isWorkflowGraphDefinition(parsed) ? parsed : null;
  }
  return isWorkflowGraphDefinition(rawDefinition) ? rawDefinition : null;
}

function isWorkflowGraphDefinition(value: unknown): value is WorkflowGraphDefinition {
  return isRecord(value) && (value.nodes === undefined || isRecord(value.nodes));
}
