// Usage: <ClickLink mid="..." url="https://...">Click here</ClickLink>
import React from 'react';

export const ClickLink: React.FC<{ mid: string; url: string; children: React.ReactNode }> = ({ mid, url, children }) => {
  try {
    const parsedUrl = new URL(url);
    if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
      return <span>{children}</span>;
    }
  } catch (e) {
    return <span>{children}</span>;
  }
  const trackingUrl = `/api/v1/track/click?mid=${encodeURIComponent(mid)}&url=${encodeURIComponent(url)}`;
  return <a href={trackingUrl} target="_blank" rel="noopener noreferrer">{children}</a>;
};
