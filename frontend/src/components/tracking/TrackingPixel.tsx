// Usage: <TrackingPixel mid="..." />
import React from 'react';

export const TrackingPixel: React.FC<{ mid: string }> = ({ mid }) => (
  <img
    src={`/api/v1/track/open.gif?mid=${encodeURIComponent(mid)}`}
    alt=""
    width={1}
    height={1}
    style={{ display: 'none' }}
    loading="eager"
  />
);
