export function subscribeAnalytics(tenantId: string, onData: (data: any) => void) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = window.location.host;
  const ws = new WebSocket(`${protocol}//${host}/ws/analytics?t=${encodeURIComponent(tenantId)}`);
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      onData(data);
    } catch {}
  };
  return () => ws.close();
}
