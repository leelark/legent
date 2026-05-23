'use client';

import { useState, useRef, useEffect } from 'react';
import Papa, { type ParseResult } from 'papaparse';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { PageHeader } from '@/components/ui/PageChrome';
import { useToast } from '@/components/ui/Toast';
import { Upload, ArrowRight, ArrowLeft, CheckCircle, WarningCircle } from '@phosphor-icons/react';
import { get, post } from '@/lib/api-client';
import { useRouter } from 'next/navigation';
import type { ImportJob } from '../../types';

const STEPS = ['Upload', 'Map Fields', 'Validate', 'Import'];
const activeStatuses = new Set(['PENDING', 'VALIDATING', 'PROCESSING']);
const terminalStatuses = new Set(['COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED']);
const TARGET_FIELDS = [
  { key: 'email', label: 'Email' },
  { key: 'subscriberKey', label: 'Subscriber Key' },
  { key: 'firstName', label: 'First Name' },
  { key: 'lastName', label: 'Last Name' },
  { key: 'phone', label: 'Phone' }
];

type CsvPreview = {
  headers: string[];
  rows: Record<string, string>[];
};

const headerAliases: Record<string, string[]> = {
  email: ['email', 'emailaddress', 'subscriberemail'],
  subscriberKey: ['subscriberkey', 'subscriberid', 'contactkey', 'customerkey'],
  firstName: ['firstname', 'first'],
  lastName: ['lastname', 'last', 'surname'],
  phone: ['phone', 'phonenumber', 'mobile', 'mobilephone'],
};

function normalizeHeader(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function autoMapHeaders(headers: string[]) {
  const normalized = new Map(headers.map((header) => [normalizeHeader(header), header]));
  const initialMap: Record<string, string> = {};
  TARGET_FIELDS.forEach((targetField) => {
    const aliases = headerAliases[targetField.key] ?? [targetField.key];
    const matched = aliases.map((alias) => normalized.get(alias)).find(Boolean);
    if (matched) initialMap[targetField.key] = matched;
  });
  return initialMap;
}

function readCsvPreview(selectedFile: File): Promise<CsvPreview> {
  return new Promise((resolve, reject) => {
    Papa.parse<string[]>(selectedFile, {
      header: false,
      preview: 6,
      skipEmptyLines: 'greedy',
      complete: (results: ParseResult<string[]>) => {
        const fatalError = results.errors.find((error) => error.type !== 'Delimiter');
        if (fatalError) {
          reject(new Error(fatalError.message));
          return;
        }
        const [rawHeaders = [], ...previewRows] = results.data;
        const headers = rawHeaders.map((header) => String(header ?? '').trim());
        if (headers.some((header) => !header)) {
          reject(new Error('CSV headers must not be blank.'));
          return;
        }
        const duplicateHeader = headers.find((header, index) =>
          headers.findIndex((candidate) => candidate.toLowerCase() === header.toLowerCase()) !== index
        );
        if (duplicateHeader) {
          reject(new Error(`Duplicate CSV header: ${duplicateHeader}`));
          return;
        }
        if (headers.length === 0) {
          reject(new Error('No CSV headers found.'));
          return;
        }
        resolve({
          headers,
          rows: previewRows.slice(0, 5).map((row) => Object.fromEntries(
            headers.map((header, index) => [header, String(row[index] ?? '')])
          )),
        });
      },
      error: (error) => reject(new Error(error.message)),
    });
  });
}

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

function isActiveStatus(status?: string) {
  return !status || activeStatuses.has(status);
}

function isTerminalStatus(status?: string) {
  return Boolean(status && terminalStatuses.has(status));
}

function ImportResult({ jobStatus }: { jobStatus: ImportJob }) {
  if (jobStatus.status === 'COMPLETED') {
    return (
      <>
        <CheckCircle size={48} weight="duotone" className="text-emerald-500 mb-4" />
        <p className="text-lg font-semibold text-content-primary">Import Completed</p>
        <p className="text-sm text-content-secondary mt-1">Successfully imported {jobStatus.successRows || 0} rows.</p>
      </>
    );
  }
  if (jobStatus.status === 'COMPLETED_WITH_ERRORS') {
    return (
      <>
        <WarningCircle size={48} weight="duotone" className="text-amber-500 mb-4" />
        <p className="text-lg font-semibold text-content-primary">Import Completed with Errors</p>
        <p className="text-sm text-content-secondary mt-1">
          {jobStatus.successRows || 0} successful, {jobStatus.errorRows || 0} failed.
        </p>
      </>
    );
  }
  if (jobStatus.status === 'FAILED') {
    return (
      <>
        <WarningCircle size={48} weight="duotone" className="text-red-500 mb-4" />
        <p className="text-lg font-semibold text-content-primary">Import Failed</p>
        <p className="text-sm text-content-secondary mt-1">
          {jobStatus.errorRows || 0} failed rows. No further polling will run.
        </p>
      </>
    );
  }
  if (jobStatus.status === 'CANCELLED') {
    return (
      <>
        <WarningCircle size={48} weight="duotone" className="text-content-muted mb-4" />
        <p className="text-lg font-semibold text-content-primary">Import Cancelled</p>
        <p className="text-sm text-content-secondary mt-1">
          Processed {jobStatus.processedRows || 0} of {jobStatus.totalRows || 0} rows before cancellation.
        </p>
      </>
    );
  }
  return (
    <>
      <WarningCircle size={48} weight="duotone" className="text-red-500 mb-4" />
      <p className="text-lg font-semibold text-content-primary">Import Finished with Issues</p>
      <p className="text-sm text-content-secondary mt-1">
        {jobStatus.successRows || 0} successful, {jobStatus.errorRows || 0} failed. Status: {jobStatus.status}
      </p>
    </>
  );
}

export default function ImportWizardPage() {
  const router = useRouter();
  const { addToast } = useToast();
  const [currentStep, setCurrentStep] = useState(0);
  const [file, setFile] = useState<File | null>(null);
  const [csvHeaders, setCsvHeaders] = useState<string[]>([]);
  const [csvPreviewRows, setCsvPreviewRows] = useState<Record<string, string>[]>([]);
  const [mapping, setMapping] = useState<Record<string, string>>({});
  const [jobId, setJobId] = useState<string | null>(null);
  const [jobStatus, setJobStatus] = useState<ImportJob | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Poll job status
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (currentStep === 3 && jobId && !isTerminalStatus(jobStatus?.status)) {
      interval = setInterval(async () => {
        try {
          const res = await get<ImportJob>(`/imports/${jobId}`);
          setJobStatus(res);
        } catch {
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

  const processFile = async (selectedFile: File) => {
    setFile(selectedFile);
    try {
      const preview = await readCsvPreview(selectedFile);
      setCsvHeaders(preview.headers);
      setCsvPreviewRows(preview.rows);
      setMapping(autoMapHeaders(preview.headers));
      setCurrentStep(1);
      addToast({
        type: 'success',
        title: 'CSV parsed',
        message: `${preview.headers.length} columns found from ${selectedFile.name}.`,
      });
    } catch (error) {
      setFile(null);
      setCsvHeaders([]);
      setCsvPreviewRows([]);
      setMapping({});
      addToast({
        type: 'error',
        title: 'CSV parse failed',
        message: error instanceof Error ? error.message : 'Unable to read CSV headers.',
      });
    }
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
      const res = await post<ImportJob>('/imports', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      if (!res?.id) {
        throw new Error('Import service did not return a job id.');
      }
      setJobId(res.id);
      setJobStatus(res);
      setCurrentStep(3);
      addToast({ type: 'success', title: 'Import started', message: file.name });
    } catch (error) {
      setCurrentStep(2);
      addToast({
        type: 'error',
        title: 'Import start failed',
        message: error instanceof Error ? error.message : 'Failed to start import.',
      });
    }
  };

  const handleNext = () => {
    if (currentStep === 1) { // mapping to Validate
      setCurrentStep(2);
    } else if (currentStep === 2) { // Validate to Import
      void handleUpload();
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
            {csvPreviewRows.length > 0 && (
              <div className="overflow-hidden rounded-lg border border-border-default">
                <div className="border-b border-border-default bg-surface-secondary px-3 py-2 text-xs font-semibold uppercase tracking-[0.12em] text-content-muted">
                  CSV preview
                </div>
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-border-default text-left text-xs">
                    <thead className="bg-surface-secondary/70 text-content-secondary">
                      <tr>
                        {csvHeaders.slice(0, 6).map((header) => (
                          <th key={header} className="max-w-[180px] truncate px-3 py-2 font-semibold">{header}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border-default">
                      {csvPreviewRows.map((row, index) => (
                        <tr key={`${file?.name ?? 'csv'}-${index}`}>
                          {csvHeaders.slice(0, 6).map((header) => (
                            <td key={header} className="max-w-[180px] truncate px-3 py-2 text-content-secondary">{row[header] || '-'}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
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
              
              {isActiveStatus(jobStatus?.status) ? (
                <>
                  <div className="h-2 w-full max-w-md rounded-full bg-surface-secondary overflow-hidden mb-4 relative">
                    <div 
                      className="absolute left-0 top-0 h-full rounded-full bg-gradient-to-r from-brand-500 to-brand-400 transition-all duration-500" 
                      style={{ width: `${jobStatus?.progressPercent || 2}%` }} 
                    />
                  </div>
                  <p className="text-sm text-content-secondary">
                    {jobStatus ? `${jobStatus.status}: processed ${jobStatus.processedRows || 0} of ${jobStatus.totalRows || 0} rows...` : 'Starting import...'}
                  </p>
                </>
              ) : jobStatus ? (
                <ImportResult jobStatus={jobStatus} />
              ) : (
                null
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
