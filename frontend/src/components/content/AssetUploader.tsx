import React, { useRef } from 'react';
import { Button } from '@/components/ui/Button';

interface AssetUploaderProps {
  onUpload: (file: File) => void;
}

export const AssetUploader: React.FC<AssetUploaderProps> = ({ onUpload }) => {
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) onUpload(file);
  };

  return (
    <div>
      <input
        ref={inputRef}
        type="file"
        className="hidden"
        onChange={handleFileChange}
      />
      <Button onClick={() => inputRef.current?.click()}>Upload Asset</Button>
    </div>
  );
};
