import React, { useRef } from 'react';
import { Button } from '@/components/ui/Button';

interface AssetUploaderProps {
  onUpload: (file: File) => void;
  onBulkUpload?: (files: File[]) => void;
}

export const AssetUploader: React.FC<AssetUploaderProps> = ({ onUpload, onBulkUpload }) => {
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    if (files.length === 0) return;
    if (files.length === 1 || !onBulkUpload) {
      onUpload(files[0]);
    } else {
      onBulkUpload(files);
    }
  };

  return (
    <div className="space-y-3">
      <input
        ref={inputRef}
        type="file"
        multiple
        className="hidden"
        onChange={handleFileChange}
      />
      <div
        className="rounded-lg border border-dashed border-border-default p-4 text-center text-sm text-content-secondary"
        onDragOver={(event) => event.preventDefault()}
        onDrop={(event) => {
          event.preventDefault();
          const files = Array.from(event.dataTransfer.files ?? []);
          if (files.length === 0) return;
          if (files.length === 1 || !onBulkUpload) {
            onUpload(files[0]);
          } else {
            onBulkUpload(files);
          }
        }}
      >
        Drag-drop media files here
      </div>
      <div className="flex gap-2">
        <Button onClick={() => inputRef.current?.click()}>Upload Asset</Button>
        <Button variant="secondary" onClick={() => inputRef.current?.click()}>Bulk Upload</Button>
      </div>
    </div>
  );
};
