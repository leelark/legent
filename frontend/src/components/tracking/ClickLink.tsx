// Usage: <ClickLink tenantId="..." campaignId="..." subscriberId="..." messageId="..." signature="..." url="https://...">Click here</ClickLink>
import React from 'react';

type ClickLinkProps = {
  tenantId: string;
  campaignId: string;
  subscriberId: string;
  messageId: string;
  signature: string;
  url: string;
  workspaceId?: string;
  children: React.ReactNode;
};

export const ClickLink: React.FC<ClickLinkProps> = ({ tenantId, campaignId, subscriberId, messageId, signature, url, workspaceId, children }) => {
  try {
    const parsedUrl = new URL(url);
    if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
      return <span>{children}</span>;
    }
  } catch (e) {
    return <span>{children}</span>;
  }
  const trackingUrl = `/api/v1/tracking/c?url=${encodeURIComponent(url)}&t=${encodeURIComponent(tenantId)}&c=${encodeURIComponent(campaignId)}&s=${encodeURIComponent(subscriberId)}&m=${encodeURIComponent(messageId)}&sig=${encodeURIComponent(signature)}${workspaceId ? `&w=${encodeURIComponent(workspaceId)}` : ''}`;
  return <a href={trackingUrl} target="_blank" rel="noopener noreferrer">{children}</a>;
};
