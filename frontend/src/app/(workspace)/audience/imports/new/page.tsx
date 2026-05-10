'use client';

import { useState, useRef, useEffect } from 'react';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { PageHeader } from '@/components/ui/PageChrome';
import { Upload, ArrowRight, ArrowLeft, CheckCircle, WarningCircle, FileText } from '@phosphor-icons/react';
import { get, post } from '@/lib/api-client';
import { useRouter } from 'next/navigation';

const STEPS = ['Upload', 'Map Fields', 'Validate', 'Import'];
const TARGET_FIELDS = [
  { key: 'email', label: 'Email' },
  { key: 'subscriberKey', label: 'Subscriber Key' },
  { key: 'firstName', label: 'First Name' },
  { key: 'lastName', label: 'Last Name' },
  { key: 'phone', label: 'Phone' }
];

function StepIndicator({ currentStep }: { currentStep: number }) {
  return (
    <div className="flex items-center justify-center gap-2">
      {STEPS.map((step, i) => (
        <div key={step} className="flex items-center gap-2">
          <div className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold transition-all ${
            i < currentStep
              ? 'bg-brand-500 text-white'
              : i === currentStep
                ? 'bg-brand-100 text-brand-700 border-2 border-brand-500 dark:bg-brand-900/30 dark:text-brand-400'
                : 'bg-surface-secondary text-content-muted'
          }`}>
            {i < currentStep ? <CheckCircle size={16} weight="fill" /> : i + 1}
          </div>
          <span className={`text-sm font-medium ${i === currentStep ? 'text-content-primary' : 'text-content-muted'}`}>
            {step}
          </span>
          {i < STEPS.length - 1 && (
            <div className={`h-px w-8 ${i < currentStep ? 'bg-brand-500' : 'bg-border-default'}`} />
          )}
        </div>
      ))}
    </div>
  );
}

export default function ImportWizardPage() {
  const router = useRouter();
  const [currentStep, setCurrentStep] = useState(0);
  const [file, setFile] = useState<File | null>(null);
  const [csvHeaders, setCsvHeaders] = useState<string[]>([]);
  const [mapping, setMapping] = useState<Record<string, string>>({});
  const [jobId, setJobId] = useState<string | null>(null);
  const [jobStatus, setJobStatus] = useState<any>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Poll job status
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (currentStep === 3 && jobId && (!jobStatus || (jobStatus.status !== 'COMPLETED' && jobStatus.status !== 'COMPLETED_WITH_ERRORS' && jobStatus.status !== 'FAILED'))) {
      interval = setInterval(async () => {
        try {
          const res = await get<any>(`/imports/${jobId}`);
          setJobStatus(res);
        } catch (e) {
          console.error('Failed to fetch job status');
        }
      }, 2000);
    }
    return () => clearInterval(interval);
  }, [currentStep, jobId, jobStatus]);

  const handleFileDrop = (e: React.DragEvent) => {
    e.preventDefault();
    if (e.dataTransfer.files?.length) {
      processFile(e.dataTransfer.files[0]);
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files?.length) {
      processFile(e.target.files[0]);
    }
  };

  const processFile = (selectedFile: File) => {
    setFile(selectedFile);
    // Read headers
    const reader = new FileReader();
    reader.onload = (e) => {
      const text = e.target?.result as string;
      const firstLine = text.split('\n')[0];
      if (firstLine) {
        const headers = firstLine.split(',').map(h => h.trim());
        setCsvHeaders(headers);
        // Auto-map where lowercase matches
        const initialMap: Record<string, string> = {};
        TARGET_FIELDS.forEach(tf => {
          const matched = headers.find(h => h.toLowerCase() === tf.key.toLowerCase());
          if (matched) initialMap[tf.key] = matched;
        });
        setMapping(initialMap);
      }
      setCurrentStep(1);
    };
    reader.readAsText(selectedFile.slice(0, 1024));
  };


  const handleUpload = async () => {
    if (!file) return;
    try {
      const formData = new FormData();
      formData.append('file', file);
      // Construct the ImportDto.StartRequest JSON part
      const requestBlob = new Blob([
        JSON.stringify({
          fileName: file.name,
          fileSize: file.size,
          targetType: 'SUBSCRIBER',
          fieldMapping: mapping
        })
      ], { type: 'application/json' });
      formData.append('request', requestBlob);
      const res = await post<any>('/imports', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      if (res?.id) {
        setJobId(res.id);
        setJobStatus(res);
        setCurrentStep(3);
      } else {
        alert('Failed to start import');
      }
    } catch (e) {
      alert('Failed to start import');
    }
  };

  const handleNext = () => {
    if (currentStep === 1) { // mapping to Validate
      setCurrentStep(2);
    } else if (currentStep === 2) { // Validate to Import
      setCurrentStep(3);
      handleUpload();
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        eyebrow="Import wizard"
        title="Import Subscribers"
        description="Upload CSV data, map subscriber fields, validate required columns, and start controlled audience intake."
        action={currentStep === 3 && (jobStatus?.status === 'COMPLETED' || jobStatus?.status === 'COMPLETED_WITH_ERRORS') ? (
            <Button onClick={() => router.push('/app/audience/subscribers')}>View Subscribers</Button>
        ) : null}
      />

      <StepIndicator currentStep={currentStep} />

      <Card>
        {currentStep === 0 && (
          <div className="space-y-6">
            <CardHeader title="Upload File" subtitle="Select a CSV file to import" />
            <div 
              onDragOver={(e) => e.preventDefault()}
              onDrop={handleFileDrop}
              onClick={() => fileInputRef.current?.click()}
              className="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-border-default p-12 hover:border-brand-300 transition-colors cursor-pointer"
            >
              <input type="file" accept=".csv" className="hidden" ref={fileInputRef} onChange={handleFileSelect} />
              <div className="rounded-2xl bg-surface-secondary p-4 mb-4">
                <Upload size={32} weight="duotone" className="text-content-muted" />
              </div>
              <p className="text-sm font-medium text-content-primary">Drop your CSV file here</p>
              <p className="mt-1 text-xs text-content-muted">or click to browse</p>
            </div>
          </div>
        )}

        {currentStep === 1 && (
          <div className="space-y-6">
            <CardHeader title="Map Fields" subtitle="Match your CSV columns to subscriber fields" />
            <div className="space-y-3">
              {TARGET_FIELDS.map((field) => (
                <div key={field.key} className="flex items-center gap-4">
                  <span className="w-32 text-sm font-medium text-content-primary capitalize">
                    {field.label} {field.key === 'email' ? '*' : ''}
                  </span>
                  <span className="text-content-muted">-&gt;</span>
                  <select 
                    value={mapping[field.key] || ''}
                    onChange={(e) => setMapping({ ...mapping, [field.key]: e.target.value })}
                    className="flex-1 rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
                  >
                    <option value="">-- Select CSV column --</option>
                    {csvHeaders.map(h => <option key={h} value={h}>{h}</option>)}
                  </select>
                </div>
              ))}
            </div>
          </div>
        )}

        {currentStep === 2 && (
          <div className="space-y-6">
            <CardHeader title="Validation" subtitle="Checking your mapping configuration" />
            <div className="flex flex-col items-center py-8">
              {!mapping['email'] ? (
                 <>
                   <WarningCircle size={48} weight="duotone" className="text-red-500 mb-4" />
                   <p className="text-lg font-semibold text-content-primary">Missing Required Fields</p>
                   <p className="text-sm text-content-secondary mt-1">You must map Email.</p>
                 </>
              ) : (
                 <>
                   <CheckCircle size={48} weight="duotone" className="text-emerald-500 mb-4" />
                   <p className="text-lg font-semibold text-content-primary">Mapping Valid</p>
                   <p className="text-sm text-content-secondary mt-1">Ready to import {file?.name}</p>
                 </>
              )}
            </div>
          </div>
        )}

        {currentStep === 3 && (
          <div className="space-y-6">
            <CardHeader title="Importing" subtitle="Processing your data" />
            <div className="flex flex-col items-center py-8">
              
              {!jobStatus || jobStatus.status === 'PROCESSING' || jobStatus.status === 'PENDING' ? (
                <>
                  <div className="h-2 w-full max-w-md rounded-full bg-surface-secondary overflow-hidden mb-4 relative">
                    <div 
                      className="absolute left-0 top-0 h-full rounded-full bg-gradient-to-r from-brand-500 to-brand-400 transition-all duration-500" 
                      style={{ width: `${jobStatus?.progressPercent || 2}%` }} 
                    />
                  </div>
                  <p className="text-sm text-content-secondary">
                    {jobStatus ? `Processed ${jobStatus.processedRows} of ${jobStatus.totalRows} rows...` : 'Starting import...'}
                  </p>
                </>
              ) : jobStatus.status === 'COMPLETED' ? (
                 <>
                   <CheckCircle size={48} weight="duotone" className="text-emerald-500 mb-4" />
                   <p className="text-lg font-semibold text-content-primary">Import Completed</p>
                   <p className="text-sm text-content-secondary mt-1">Successfully imported {jobStatus.successRows} rows.</p>
                 </>
              ) : (
                 <>
                   <WarningCircle size={48} weight="duotone" className="text-red-500 mb-4" />
                   <p className="text-lg font-semibold text-content-primary">Import Finished with Issues</p>
                   <p className="text-sm text-content-secondary mt-1">
                     {jobStatus.successRows} successful, {jobStatus.errorRows} failed. Status: {jobStatus.status}
                   </p>
                 </>
              )}
            </div>
          </div>
        )}
      </Card>

      {/* Navigation */}
      {currentStep < 3 && (
        <div className="flex justify-between">
          <Button
            variant="secondary"
            onClick={() => setCurrentStep(Math.max(0, currentStep - 1))}
            disabled={currentStep === 0}
            icon={<ArrowLeft size={16} />}
          >
            Back
          </Button>
          <Button
            onClick={handleNext}
            disabled={currentStep === 2 && !mapping['email']}
            icon={<ArrowRight size={16} />}
          >
            {currentStep === 2 ? 'Start Import' : 'Next'}
          </Button>
        </div>
      )}
    </div>
  );
}
