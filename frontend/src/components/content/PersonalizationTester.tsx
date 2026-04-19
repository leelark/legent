import React, { useState } from 'react';
import { Button } from '@/components/ui/Button';

interface PersonalizationTesterProps {
  onTest: (vars: Record<string, string>) => void;
}

export const PersonalizationTester: React.FC<PersonalizationTesterProps> = ({ onTest }) => {
  const [vars, setVars] = useState<Record<string, string>>({});
  const [key, setKey] = useState('');
  const [value, setValue] = useState('');

  const addVar = () => {
    if (key) {
      setVars({ ...vars, [key]: value });
      setKey('');
      setValue('');
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input
          className="border rounded p-1"
          placeholder="Token name"
          value={key}
          onChange={e => setKey(e.target.value)}
        />
        <input
          className="border rounded p-1"
          placeholder="Value"
          value={value}
          onChange={e => setValue(e.target.value)}
        />
        <Button size="sm" onClick={addVar}>Add</Button>
      </div>
      <div className="flex flex-wrap gap-2">
        {Object.entries(vars).map(([k, v]) => (
          <span key={k} className="bg-gray-100 rounded px-2 py-1 text-xs">{k}: {v}</span>
        ))}
      </div>
      <Button size="sm" onClick={() => onTest(vars)}>Test Personalization</Button>
    </div>
  );
};
