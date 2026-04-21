import React from 'react';
import { Modal } from '@/components/ui/Modal';
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
    <Modal
      open={open}
      onClose={onClose}
      title="Edit Node"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={() => onSave({ ...node, label })}>Save</Button>
        </>
      }
    >
      <div className="space-y-3">
        <Input value={label} onChange={e => setLabel(e.target.value)} label="Label" />
        {/* Add more config fields based on node.type here */}
      </div>
    </Modal>
  );
};
