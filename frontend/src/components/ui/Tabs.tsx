'use client';

import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
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
  const id = useId();
  const buttonRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const activeIndex = Math.max(0, tabs.findIndex((tab) => tab.key === active));

  const activateTab = (index: number) => {
    const tab = tabs[index];
    if (!tab) return;
    setActive(tab.key);
    requestAnimationFrame(() => buttonRefs.current[index]?.focus());
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      activateTab((index + 1) % tabs.length);
    }
    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      activateTab((index - 1 + tabs.length) % tabs.length);
    }
    if (event.key === 'Home') {
      event.preventDefault();
      activateTab(0);
    }
    if (event.key === 'End') {
      event.preventDefault();
      activateTab(tabs.length - 1);
    }
  };

  return (
    <div>
      <div className="overflow-x-auto border-b border-border-default px-2">
        <div className="flex min-w-max items-center gap-1" role="tablist" aria-orientation="horizontal">
          {tabs.map((tab, index) => (
            <button
              key={tab.key}
              ref={(element) => {
                buttonRefs.current[index] = element;
              }}
              id={`${id}-${tab.key}-tab`}
              role="tab"
              aria-selected={active === tab.key}
              aria-controls={`${id}-${tab.key}-panel`}
              tabIndex={active === tab.key ? 0 : -1}
              onClick={() => setActive(tab.key)}
              onKeyDown={(event) => handleKeyDown(event, index)}
              className={clsx(
                'mb-[-1px] flex items-center gap-2 whitespace-nowrap border-b-2 px-4 py-2.5 text-sm font-medium transition-all',
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
      </div>
      <div
        className="p-4"
        id={`${id}-${tabs[activeIndex]?.key || active}-panel`}
        role="tabpanel"
        aria-labelledby={`${id}-${tabs[activeIndex]?.key || active}-tab`}
      >
        {children(active)}
      </div>
    </div>
  );
}
