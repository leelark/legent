export function subscribeAnalytics(onData: (data: any) => void) {
  const ws = new WebSocket(`ws://${window.location.host}/ws/analytics`);
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      onData(data);
    } catch {}
  };
  return () => ws.close();
}
