// Usage: <TrackingPixel mid="..." />
import React from 'react';

export const TrackingPixel: React.FC<{ mid: string; tid: string }> = ({ mid, tid }) => (
  // eslint-disable-next-line @next/next/no-img-element
  <img
    src={`/api/v1/track/open.gif?mid=${encodeURIComponent(mid)}&t=${encodeURIComponent(tid)}`}
    alt=""
    width={1}
    height={1}
    style={{ display: 'none' }}
    loading="eager"
  />
);
