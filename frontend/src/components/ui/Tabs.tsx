'use client';

import { useState, type ReactNode } from 'react';
import { clsx } from 'clsx';

interface Tab {
  key: string;
  label: string;
  icon?: ReactNode;
}

interface TabsProps {
  tabs: Tab[];
  children: (activeTab: string) => ReactNode;
  defaultTab?: string;
}

export function Tabs({ tabs, children, defaultTab }: TabsProps) {
  const [active, setActive] = useState(defaultTab || tabs[0]?.key || '');

  return (
    <div>
      <div className="flex items-center gap-1 border-b border-border-default px-2">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActive(tab.key)}
            className={clsx(
              'flex items-center gap-2 px-4 py-2.5 text-sm font-medium transition-all border-b-2 -mb-px',
              active === tab.key
                ? 'text-brand-600 border-brand-500 dark:text-brand-400 dark:border-brand-400'
                : 'text-content-secondary border-transparent hover:text-content-primary hover:border-border-muted'
            )}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>
      <div className="p-4">
        {children(active)}
      </div>
    </div>
  );
}
