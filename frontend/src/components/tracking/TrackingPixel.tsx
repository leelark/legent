// Usage: <TrackingPixel tenantId="..." campaignId="..." subscriberId="..." messageId="..." signature="..." workspaceId="..." />
import React from 'react';

type TrackingPixelProps = {
  tenantId: string;
  campaignId: string;
  subscriberId: string;
  messageId: string;
  signature: string;
  workspaceId?: string;
};

export const TrackingPixel: React.FC<TrackingPixelProps> = ({ tenantId, campaignId, subscriberId, messageId, signature, workspaceId }) => (
  // eslint-disable-next-line @next/next/no-img-element
  <img
    src={`/api/v1/tracking/o.gif?t=${encodeURIComponent(tenantId)}&c=${encodeURIComponent(campaignId)}&s=${encodeURIComponent(subscriberId)}&m=${encodeURIComponent(messageId)}&sig=${encodeURIComponent(signature)}${workspaceId ? `&w=${encodeURIComponent(workspaceId)}` : ''}`}
    alt=""
    width={1}
    height={1}
    style={{ display: 'none' }}
    loading="eager"
  />
);
