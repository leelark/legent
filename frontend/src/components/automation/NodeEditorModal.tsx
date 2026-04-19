import React from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { JourneyNode } from './JourneyBuilder';

interface NodeEditorModalProps {
  open: boolean;
  node: JourneyNode | null;
  onClose: () => void;
  onSave: (node: JourneyNode) => void;
}

export const NodeEditorModal: React.FC<NodeEditorModalProps> = ({ open, node, onClose, onSave }) => {
  const [label, setLabel] = React.useState(node?.label || '');
  React.useEffect(() => { setLabel(node?.label || ''); }, [node]);

  if (!node) return null;

  return (
    <Dialog open={open} onOpenChange={v => !v && onClose()}>
      <DialogTitle>Edit Node</DialogTitle>
      <DialogContent>
        <Input value={label} onChange={e => setLabel(e.target.value)} label="Label" />
        {/* Add more config fields based on node.type here */}
      </DialogContent>
      <DialogActions>
        <Button variant="outline" onClick={onClose}>Cancel</Button>
        <Button onClick={() => onSave({ ...node, label })}>Save</Button>
      </DialogActions>
    </Dialog>
  );
};
