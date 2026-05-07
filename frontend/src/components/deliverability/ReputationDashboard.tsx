'use client';
import React, { useEffect, useState } from 'react';
import { getReputation } from '@/lib/deliverability-api';
import { Card } from '@/components/ui/Card';

export const ReputationDashboard: React.FC<{ domain: string }> = ({ domain }) => {
  const [score, setScore] = useState<any | null>(null);
  useEffect(() => {
    setScore(null);
    getReputation(domain).then(setScore).catch(() => setScore(null));
  }, [domain]);

  if (!score) return null;

  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Reputation: {domain}</h3>
      <div className="flex items-center gap-4">
        <span className="text-2xl font-bold">{score.score}</span>
        <span className="text-xs text-content-secondary">Last updated: {score.lastUpdated}</span>
      </div>
    </Card>
  );
};
