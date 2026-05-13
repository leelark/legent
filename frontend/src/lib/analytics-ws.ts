export function subscribeAnalytics(tenantId: string | null | undefined, workspaceId: string | null | undefined, onData: (data: any) => void) {
  if (!tenantId?.trim() || !workspaceId?.trim()) {
    console.warn('[analytics-ws] Tenant and workspace context are required before opening analytics WebSocket');
    return () => {};
  }

  const ws = new WebSocket(resolveAnalyticsWebSocketUrl());
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      onData(data);
    } catch {}
  };
  return () => ws.close();
}

function resolveAnalyticsWebSocketUrl() {
  const apiBaseUrl = (
    process.env.NEXT_PUBLIC_API_BASE_URL ||
    process.env.NEXT_PUBLIC_API_URL ||
    ''
  ).replace(/\/$/, '');
  const baseUrl = /^https?:\/\//.test(apiBaseUrl) ? apiBaseUrl : window.location.origin;
  const url = new URL('/ws/analytics', baseUrl);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return url.toString();
}
