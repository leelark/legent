import React from 'react';
import { Button } from '@/components/ui/Button';

export interface BlockType {
  type: string;
  label: string;
  icon?: React.ReactNode;
}

interface BlockPaletteProps {
  blockTypes: BlockType[];
  onAddBlock: (type: string) => void;
}

export const BlockPalette: React.FC<BlockPaletteProps> = ({ blockTypes, onAddBlock }) => (
  <div className="flex flex-wrap gap-2">
    {blockTypes.map(bt => (
      <Button key={bt.type} onClick={() => onAddBlock(bt.type)}>{bt.label}</Button>
    ))}
  </div>
);
