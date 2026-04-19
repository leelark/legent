import React, { useEffect, useState } from 'react';
import { getReputation } from '@/lib/deliverability-api';
import { Card } from '@/components/ui/Card';

export const ReputationDashboard: React.FC<{ domain: string }> = ({ domain }) => {
  const [scores, setScores] = useState<any[]>([]);
  useEffect(() => {
    getReputation(domain).then(setScores);
  }, [domain]);

  if (!scores.length) return null;
  const latest = scores[scores.length - 1];

  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Reputation: {domain}</h3>
      <div className="flex items-center gap-4">
        <span className="text-2xl font-bold">{latest.score}</span>
        <span className="text-xs text-muted-foreground">Last updated: {latest.lastUpdated}</span>
      </div>
    </Card>
  );
};
