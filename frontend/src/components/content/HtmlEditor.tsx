import React from 'react';

interface HtmlEditorProps {
  value: string;
  onChange: (value: string) => void;
}

export const HtmlEditor: React.FC<HtmlEditorProps> = ({ value, onChange }) => {
  return (
    <textarea
      className="w-full min-h-[200px] border rounded p-2 font-mono"
      value={value}
      onChange={e => onChange(e.target.value)}
      placeholder="Paste or edit HTML here..."
    />
  );
};
