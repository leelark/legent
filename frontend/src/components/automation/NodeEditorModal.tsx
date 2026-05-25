'use client';
import React from 'react';
import { Modal } from '@/components/ui/Modal';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { listCampaigns, type Campaign } from '@/lib/campaign-studio-api';
import {
  JOURNEY_NODE_TYPES,
  RUNTIME_SUPPORTED_JOURNEY_NODE_TYPES,
  journeyNodeLabel,
  type JourneyNode,
  type JourneyNodeType,
} from './journey-node-contract';

const SELECT_CLASS = 'w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-all';

interface NodeEditorModalProps {
  open: boolean;
  node: JourneyNode | null;
  runtimeSupportedTypes?: readonly JourneyNodeType[];
  showDraftNodeTypes?: boolean;
  onClose: () => void;
  onSave: (node: JourneyNode) => void;
}

export const NodeEditorModal: React.FC<NodeEditorModalProps> = ({
  open,
  node,
  runtimeSupportedTypes = RUNTIME_SUPPORTED_JOURNEY_NODE_TYPES,
  showDraftNodeTypes = true,
  onClose,
  onSave,
}) => {
  const [label, setLabel] = React.useState(node?.label || '');
  const [type, setType] = React.useState<JourneyNode['type']>(node?.type || 'SEND_EMAIL');
  const [campaignId, setCampaignId] = React.useState<string>((node?.config?.campaignId as string) || '');
  const [delayMinutes, setDelayMinutes] = React.useState<string>(
    node?.config?.minutes ? String(node.config.minutes) : ''
  );
  const [waitUntilAt, setWaitUntilAt] = React.useState<string>(
    ((node?.config?.at || node?.config?.until) as string) || ''
  );
  const [campaigns, setCampaigns] = React.useState<Campaign[]>([]);
  const [campaignsLoading, setCampaignsLoading] = React.useState(false);
  const [campaignError, setCampaignError] = React.useState<string | null>(null);

  React.useEffect(() => {
    setLabel(node?.label || '');
    setType(node?.type || 'SEND_EMAIL');
    setCampaignId((node?.config?.campaignId as string) || '');
    setDelayMinutes(node?.config?.minutes ? String(node.config.minutes) : '');
    setWaitUntilAt(((node?.config?.at || node?.config?.until) as string) || '');
  }, [node]);

  React.useEffect(() => {
    if (!open || type !== 'SEND_EMAIL') {
      return;
    }
    let cancelled = false;
    setCampaignsLoading(true);
    setCampaignError(null);
    listCampaigns({ page: 0, size: 100 })
      .then((response) => {
        if (cancelled) {
          return;
        }
        const items = Array.isArray(response as unknown)
          ? (response as unknown as Campaign[])
          : response.content ?? [];
        setCampaigns(items);
      })
      .catch(() => {
        if (!cancelled) {
          setCampaignError('Campaign catalog could not be loaded.');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setCampaignsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [open, type]);

  if (!node) return null;

  const runtimeSupportedTypeSet = new Set(runtimeSupportedTypes);
  const runtimeSupported = runtimeSupportedTypeSet.has(type);
  const nodeTypeOptions = showDraftNodeTypes
    ? JOURNEY_NODE_TYPES
    : JOURNEY_NODE_TYPES.filter((option) => runtimeSupportedTypeSet.has(option) || option === type);
  const selectedCampaign = campaigns.find((campaign) => campaign.id === campaignId);
  const campaignOptions = campaignId && !selectedCampaign
    ? [{ id: campaignId, name: `Current campaign (${campaignId})`, status: 'DRAFT' as const }, ...campaigns]
    : campaigns;

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
    if (type === 'WAIT_UNTIL' && waitUntilAt.trim()) {
      nextConfig.at = waitUntilAt.trim();
      delete nextConfig.until;
    } else if (type !== 'WAIT_UNTIL') {
      delete nextConfig.at;
      delete nextConfig.until;
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
          <Button onClick={handleSave} disabled={!runtimeSupported}>Save</Button>
        </>
      }
    >
      <div className="space-y-3">
        <Input value={label} onChange={e => setLabel(e.target.value)} label="Label" />
        <div>
          <label htmlFor="journey-node-type" className="mb-1 block text-sm font-medium text-content-primary">Node Type</label>
          <select
            id="journey-node-type"
            value={type}
            onChange={(e) => setType(e.target.value as JourneyNode['type'])}
            className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-all"
          >
            {nodeTypeOptions.map((option) => {
              const optionRuntimeSupported = runtimeSupportedTypeSet.has(option);
              return (
                <option key={option} value={option} disabled={!optionRuntimeSupported && option !== type}>
                  {journeyNodeLabel(option)}{optionRuntimeSupported ? '' : ' (draft only)'}
                </option>
              );
            })}
          </select>
          {!runtimeSupported && (
            <p className="mt-2 text-xs text-amber-700 dark:text-amber-200">
              This node is stored as a draft-only design element until live runtime support is added.
            </p>
          )}
        </div>
        {type === 'SEND_EMAIL' && (
          <div className="space-y-1.5">
            <label htmlFor="journey-node-campaign" className="block text-sm font-medium text-content-primary">Campaign</label>
            <select
              id="journey-node-campaign"
              aria-label="Campaign"
              value={campaignId}
              onChange={(event) => setCampaignId(event.target.value)}
              className={SELECT_CLASS}
              disabled={campaignsLoading}
            >
              <option value="">{campaignsLoading ? 'Loading campaigns...' : 'Select campaign'}</option>
              {campaignOptions.map((campaign) => (
                <option key={campaign.id} value={campaign.id}>
                  {campaign.name} ({campaign.id})
                </option>
              ))}
            </select>
            {selectedCampaign && (
              <div className="flex flex-wrap items-center gap-2 text-xs text-content-secondary">
                <span>{selectedCampaign.subject || selectedCampaign.type || 'Campaign send'}</span>
                <Badge variant={selectedCampaign.status === 'APPROVED' || selectedCampaign.status === 'SCHEDULED' ? 'success' : 'info'}>
                  {selectedCampaign.status}
                </Badge>
              </div>
            )}
            {campaignError && <p className="text-xs text-danger">{campaignError}</p>}
          </div>
        )}
        {type === 'DELAY' && (
          <Input
            value={delayMinutes}
            onChange={(e) => setDelayMinutes(e.target.value)}
            label="Delay Minutes"
            placeholder="60"
          />
        )}
        {type === 'WAIT_UNTIL' && (
          <Input
            value={waitUntilAt}
            onChange={(e) => setWaitUntilAt(e.target.value)}
            label="Wait Until"
            placeholder="2026-05-24T12:00:00Z"
            hint="Use an ISO-8601 UTC instant within seven days."
          />
        )}
      </div>
    </Modal>
  );
};
