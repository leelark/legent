import React, { useState, useRef } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Send, Clock, GitBranch, Plus, GripVertical, Edit, Download, Upload } from 'lucide-react';
import { NodeEditorModal } from './NodeEditorModal';
import apiClient from '@/lib/api-client';

export interface JourneyNode {
  id: string;
  type: 'TRIGGER' | 'SEND_EMAIL' | 'DELAY' | 'CONDITION' | 'END';
  label: string;
  config?: Record<string, any>;
  next?: string;
  branches?: { condition: string; target: string }[];
}

interface JourneyBuilderProps {
  nodes: JourneyNode[];
  onNodesChange: (nodes: JourneyNode[]) => void;
  workflowId?: string;
}

  // Drag-and-drop state
  const [draggedIdx, setDraggedIdx] = useState<number | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingNode, setEditingNode] = useState<JourneyNode | null>(null);
  const [loading, setLoading] = useState(false);
  const dragOverIdx = useRef<number | null>(null);

  const handleDragStart = (idx: number) => setDraggedIdx(idx);
  const handleDragEnter = (idx: number) => { dragOverIdx.current = idx; };
  const handleDragEnd = () => {
    if (draggedIdx !== null && dragOverIdx.current !== null && draggedIdx !== dragOverIdx.current) {
      const updated = [...nodes];
      const [removed] = updated.splice(draggedIdx, 1);
      updated.splice(dragOverIdx.current, 0, removed);
      onNodesChange(updated);
    }
    setDraggedIdx(null);
    dragOverIdx.current = null;
  };

  const handleEdit = (node: JourneyNode) => {
    setEditingNode(node);
    setEditorOpen(true);
  };

  const handleSave = (updatedNode: JourneyNode) => {
    const updated = nodes.map(n => n.id === updatedNode.id ? updatedNode : n);
    onNodesChange(updated);
    setEditorOpen(false);
    setEditingNode(null);
  };

  // Backend integration
  const handleSaveToBackend = async () => {
    setLoading(true);
    try {
      // For demo, use workflowId or 'demo-workflow'
      const workflowId = (typeof window !== 'undefined' && window.localStorage.getItem('current_workflow_id')) || 'demo-workflow';
      await apiClient.post('/workflow-definitions', {
        workflowId,
        version: 1,
        definition: JSON.stringify(nodes),
      });
      alert('Journey saved!');
    } catch (e) {
      alert('Failed to save journey.');
    } finally {
      setLoading(false);
    }
  };

  const handleLoadFromBackend = async () => {
    setLoading(true);
    try {
      const workflowId = (typeof window !== 'undefined' && window.localStorage.getItem('current_workflow_id')) || 'demo-workflow';
      const res = await apiClient.get(`/workflow-definitions/${workflowId}/latest`);
      if (res.data?.data?.definition) {
        onNodesChange(JSON.parse(res.data.data.definition));
      }
    } catch (e) {
      alert('Failed to load journey.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col items-center w-full">
      <div className="flex gap-2 mb-4">
        <Button size="sm" variant="outline" onClick={handleLoadFromBackend} disabled={loading}>
          <Download className="w-4 h-4 mr-1" /> Load
        </Button>
        <Button size="sm" variant="outline" onClick={handleSaveToBackend} disabled={loading}>
          <Upload className="w-4 h-4 mr-1" /> Save
        </Button>
      </div>
      {nodes.map((node, idx) => (
        <div
          key={node.id}
          draggable
          onDragStart={() => handleDragStart(idx)}
          onDragEnter={() => handleDragEnter(idx)}
          onDragEnd={handleDragEnd}
          className="w-72 mb-4 flex flex-col items-center cursor-move"
        >
          <Card className={`w-full flex flex-col items-center ${draggedIdx === idx ? 'opacity-60' : ''}`}> 
            <div className="flex items-center gap-2 mb-2 w-full justify-between">
              <span className="flex items-center gap-2">
                <GripVertical className="w-4 h-4 text-slate-400 mr-1" />
                {node.type === 'SEND_EMAIL' && <Send className="w-4 h-4 text-blue-500" />}
                {node.type === 'DELAY' && <Clock className="w-4 h-4 text-orange-500" />}
                {node.type === 'CONDITION' && <GitBranch className="w-4 h-4 text-purple-500" />}
                <span className="font-semibold">{node.label}</span>
              </span>
              <span className="flex gap-1">
                <Button size="icon" variant="ghost" onClick={() => handleEdit(node)}><Edit className="w-4 h-4" /></Button>
                <Button size="icon" variant="ghost" onClick={() => {
                  const updated = nodes.filter((_, i) => i !== idx);
                  onNodesChange(updated);
                }}>🗑️</Button>
              </span>
            </div>
            <div className="text-xs text-muted-foreground mb-2">{node.type}</div>
          </Card>
        </div>
      ))}
      <Button className="mt-4" onClick={() => {
        const newNode: JourneyNode = { id: `node-${nodes.length+1}`, type: 'SEND_EMAIL', label: 'Send Email' };
        onNodesChange([...nodes, newNode]);
      }}>
        <Plus className="w-4 h-4 mr-2" /> Add Step
      </Button>
      <NodeEditorModal open={editorOpen} node={editingNode} onClose={() => setEditorOpen(false)} onSave={handleSave} />
    </div>
  );
}

export default JourneyBuilder;
