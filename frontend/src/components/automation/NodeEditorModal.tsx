'use client';
import React from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { JourneyNode } from './JourneyBuilder';

interface NodeEditorModalProps {
  open: boolean;
  node: JourneyNode | null;
  onClose: () => void;
  onSave: (node: JourneyNode) => void;
}

export const NodeEditorModal: React.FC<NodeEditorModalProps> = ({ open, node, onClose, onSave }) => {
  const [label, setLabel] = React.useState(node?.label || '');
  const [type, setType] = React.useState<JourneyNode['type']>(node?.type || 'SEND_EMAIL');
  const [campaignId, setCampaignId] = React.useState<string>((node?.config?.campaignId as string) || '');
  const [delayMinutes, setDelayMinutes] = React.useState<string>(
    node?.config?.minutes ? String(node.config.minutes) : ''
  );

  React.useEffect(() => {
    setLabel(node?.label || '');
    setType(node?.type || 'SEND_EMAIL');
    setCampaignId((node?.config?.campaignId as string) || '');
    setDelayMinutes(node?.config?.minutes ? String(node.config.minutes) : '');
  }, [node]);

  if (!node) return null;

  const handleSave = () => {
    const nextConfig: Record<string, unknown> = { ...(node.config || {}) };
    if (type === 'SEND_EMAIL' && campaignId.trim()) {
      nextConfig.campaignId = campaignId.trim();
    } else if (type !== 'SEND_EMAIL') {
      delete nextConfig.campaignId;
    }
    if (type === 'DELAY' && delayMinutes.trim()) {
      const parsed = Number.parseInt(delayMinutes, 10);
      if (!Number.isNaN(parsed)) {
        nextConfig.minutes = parsed;
      }
    } else if (type !== 'DELAY') {
      delete nextConfig.minutes;
    }
    onSave({ ...node, label, type, config: nextConfig });
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Edit Node"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSave}>Save</Button>
        </>
      }
    >
      <div className="space-y-3">
        <Input value={label} onChange={e => setLabel(e.target.value)} label="Label" />
        <div>
          <label className="mb-1 block text-sm font-medium text-content-primary">Node Type</label>
          <select
            value={type}
            onChange={(e) => setType(e.target.value as JourneyNode['type'])}
            className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-all"
          >
            <option value="ENTRY_TRIGGER">Entry Trigger</option>
            <option value="SEND_EMAIL">Send Email</option>
            <option value="DELAY">Delay</option>
            <option value="CONDITION">Condition</option>
            <option value="BRANCH">Branch</option>
            <option value="SPLIT">Split</option>
            <option value="JOIN">Join</option>
            <option value="WEBHOOK">Webhook</option>
            <option value="UPDATE_FIELD">Update Field</option>
            <option value="ADD_TAG">Add Tag</option>
            <option value="REMOVE_TAG">Remove Tag</option>
            <option value="SUPPRESS_CONTACT">Suppress Contact</option>
            <option value="WAIT_UNTIL">Wait Until</option>
            <option value="PAUSE">Pause</option>
            <option value="EXIT_GOAL">Exit Goal</option>
            <option value="REENTRY_GATE">Re-entry Gate</option>
            <option value="EVENT_LISTENER">Event Listener</option>
            <option value="END">End</option>
          </select>
        </div>
        {type === 'SEND_EMAIL' && (
          <Input
            value={campaignId}
            onChange={(e) => setCampaignId(e.target.value)}
            label="Campaign ID"
            placeholder="campaign-123"
          />
        )}
        {type === 'DELAY' && (
          <Input
            value={delayMinutes}
            onChange={(e) => setDelayMinutes(e.target.value)}
            label="Delay Minutes"
            placeholder="60"
          />
        )}
      </div>
    </Modal>
  );
};
