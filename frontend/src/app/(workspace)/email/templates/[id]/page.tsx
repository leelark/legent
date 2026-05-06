'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { Tabs } from '@/components/ui/Tabs';
import { useToast } from '@/components/ui/Toast';
import { TemplateBuilder, ContentBlock } from '@/components/content/TemplateBuilder';
import { VersionHistory } from '@/components/content/VersionHistory';
import { AssetUploader } from '@/components/content/AssetUploader';
import { PersonalizationTester } from '@/components/content/PersonalizationTester';
import {
  Asset,
  Template,
  TemplateApproval,
  TemplateVersion,
  ValidationResponse,
  approveTemplateApproval,
  cancelTemplateApproval,
  compareTemplateVersions,
  exportTemplateHtml,
  getTemplate,
  getTemplateApprovals,
  listAssets,
  listTemplateVersions,
  previewTemplate,
  publishTemplate,
  publishTemplateVersion,
  rejectTemplateApproval,
  rollbackTemplate,
  saveTemplateDraft,
  sendTemplateTestEmail,
  submitTemplateApproval,
  updateTemplate,
  uploadAsset,
  uploadAssetsBulk,
  validateTemplate,
} from '@/lib/template-studio-api';

const toText = (html: string) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();

const defaultBlock = (html: string): ContentBlock => ({
  id: `block-${Date.now()}`,
  name: 'HTML Block',
  blockType: 'HTML',
  content: html || '<p>Start building your template.</p>',
  styles: {
    backgroundColor: '#ffffff',
    textColor: '#0f172a',
    padding: 16,
    borderRadius: 8,
  },
  settings: {
    hideOnMobile: false,
    hideOnDesktop: false,
    visibilityRule: '',
  },
});

const parseMetadata = (metadata?: string | null): Record<string, any> => {
  if (!metadata) return {};
  try {
    const parsed = JSON.parse(metadata);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
};

const blocksToHtml = (blocks: ContentBlock[]): string => {
  const rows = blocks.map((block) => {
    const styles = block.styles ?? {};
    const padding = Number(styles.padding ?? 16);
    const radius = Number(styles.borderRadius ?? 8);
    const backgroundColor = String(styles.backgroundColor ?? '#ffffff');
    const textColor = String(styles.textColor ?? '#0f172a');
    return `
      <tr>
        <td style="padding:${padding}px;background:${backgroundColor};color:${textColor};border-radius:${radius}px;">
          ${block.content}
        </td>
      </tr>
    `;
  });
  return `
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
      <tbody>
        ${rows.join('\n')}
      </tbody>
    </table>
  `;
};

export default function TemplateStudioPage() {
  const params = useParams();
  const templateId = params?.id as string;
  const { addToast } = useToast();

  const [template, setTemplate] = useState<Template | null>(null);
  const [name, setName] = useState('');
  const [subject, setSubject] = useState('');
  const [blocks, setBlocks] = useState<ContentBlock[]>([]);
  const [metadata, setMetadata] = useState<Record<string, any>>({});

  const [versions, setVersions] = useState<TemplateVersion[]>([]);
  const [approvals, setApprovals] = useState<TemplateApproval[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);

  const [previewHtml, setPreviewHtml] = useState('');
  const [previewSubject, setPreviewSubject] = useState('');
  const [previewWarnings, setPreviewWarnings] = useState<string[]>([]);
  const [validation, setValidation] = useState<ValidationResponse | null>(null);
  const [previewMode, setPreviewMode] = useState('desktop');
  const [darkModePreview, setDarkModePreview] = useState(false);
  const [personalizationVars, setPersonalizationVars] = useState<Record<string, string>>({});

  const [compareLeft, setCompareLeft] = useState<number | null>(null);
  const [compareRight, setCompareRight] = useState<number | null>(null);
  const [compareResult, setCompareResult] = useState<any>(null);

  const [testEmail, setTestEmail] = useState('');
  const [approvalComment, setApprovalComment] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [assetQuery, setAssetQuery] = useState('');
  const [isBusy, setIsBusy] = useState(false);
  const [loading, setLoading] = useState(true);

  const htmlFromBlocks = useMemo(() => blocksToHtml(blocks), [blocks]);

  const loadTemplateStudio = useCallback(async () => {
    if (!templateId) return;
    setLoading(true);
    try {
      const [templateRes, versionsRes, approvalsRes, assetsRes] = await Promise.all([
        getTemplate(templateId),
        listTemplateVersions(templateId),
        getTemplateApprovals(templateId),
        listAssets({ page: 0, size: 40 }),
      ]);

      const parsedMetadata = parseMetadata(templateRes.metadata);
      const builderBlocks = Array.isArray(parsedMetadata.builderBlocks) ? parsedMetadata.builderBlocks as ContentBlock[] : [];
      const initialBlocks = builderBlocks.length > 0
        ? builderBlocks
        : [defaultBlock(templateRes.draftHtmlContent || templateRes.htmlContent || '')];

      setTemplate(templateRes);
      setName(templateRes.name);
      setSubject(templateRes.draftSubject || templateRes.subject || '');
      setMetadata(parsedMetadata);
      setBlocks(initialBlocks);
      setVersions(Array.isArray(versionsRes) ? versionsRes : []);
      setApprovals(Array.isArray(approvalsRes) ? approvalsRes : []);
      const assetItems = Array.isArray(assetsRes) ? assetsRes : (assetsRes?.content ?? assetsRes?.data ?? []);
      setAssets(assetItems);

      if (versionsRes.length >= 2) {
        setCompareLeft(versionsRes[0].versionNumber);
        setCompareRight(versionsRes[1].versionNumber);
      }
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Failed to load template studio',
        message: error?.response?.data?.error?.message || 'Unable to load template details.',
      });
    } finally {
      setLoading(false);
    }
  }, [addToast, templateId]);

  useEffect(() => {
    void loadTemplateStudio();
  }, [loadTemplateStudio]);

  const persistTemplate = async () => {
    if (!template) return;
    const mergedMetadata = JSON.stringify({
      ...metadata,
      builderBlocks: blocks,
      updatedByStudioAt: new Date().toISOString(),
    });

    await updateTemplate(template.id, {
      name,
      subject,
      htmlContent: htmlFromBlocks,
      textContent: toText(htmlFromBlocks),
      metadata: mergedMetadata,
    });
    await saveTemplateDraft(template.id, {
      subject,
      htmlContent: htmlFromBlocks,
      textContent: toText(htmlFromBlocks),
    });
  };

  const withBusy = async (action: () => Promise<void>) => {
    setIsBusy(true);
    try {
      await action();
    } finally {
      setIsBusy(false);
    }
  };

  const handleSaveDraft = async () => {
    if (!template) return;
    await withBusy(async () => {
      await persistTemplate();
      addToast({ type: 'success', title: 'Draft saved', message: 'Template draft and autosave snapshot updated.' });
      await loadTemplateStudio();
    });
  };

  const handlePublishLatest = async () => {
    if (!template) return;
    await withBusy(async () => {
      await persistTemplate();
      await publishTemplate(template.id);
      addToast({ type: 'success', title: 'Template published', message: 'Latest draft is now published.' });
      await loadTemplateStudio();
    });
  };

  const handlePreview = async () => {
    if (!template) return;
    try {
      const preview = await previewTemplate(template.id, {
        variables: personalizationVars,
        mode: previewMode,
        darkMode: darkModePreview,
      });
      setPreviewHtml(preview.htmlContent);
      setPreviewSubject(preview.subject);
      setPreviewWarnings(preview.warnings ?? []);
      const validationResult = await validateTemplate(htmlFromBlocks);
      setValidation(validationResult);
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Preview failed',
        message: error?.response?.data?.error?.message || 'Unable to generate preview.',
      });
    }
  };

  const handleCompareVersions = async () => {
    if (!template || compareLeft == null || compareRight == null) return;
    try {
      const compare = await compareTemplateVersions(template.id, compareLeft, compareRight);
      setCompareResult(compare);
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Compare failed',
        message: error?.response?.data?.error?.message || 'Unable to compare versions.',
      });
    }
  };

  const refreshApprovals = async () => {
    if (!template) return;
    const items = await getTemplateApprovals(template.id);
    setApprovals(items);
  };

  const handleSubmitApproval = async () => {
    if (!template) return;
    await withBusy(async () => {
      await persistTemplate();
      await submitTemplateApproval(template.id, approvalComment);
      setApprovalComment('');
      addToast({ type: 'success', title: 'Submitted for approval', message: 'Approval request created.' });
      await refreshApprovals();
      await loadTemplateStudio();
    });
  };

  const handleApprove = async (approvalId: string) => {
    await withBusy(async () => {
      await approveTemplateApproval(approvalId, 'Approved from Template Studio');
      await refreshApprovals();
      addToast({ type: 'success', title: 'Approved', message: 'Template approval marked approved.' });
    });
  };

  const handleReject = async (approvalId: string) => {
    if (!rejectReason.trim()) {
      addToast({ type: 'warning', title: 'Reason required', message: 'Provide rejection reason first.' });
      return;
    }
    await withBusy(async () => {
      await rejectTemplateApproval(approvalId, rejectReason.trim());
      setRejectReason('');
      await refreshApprovals();
      addToast({ type: 'success', title: 'Rejected', message: 'Approval rejected with reason.' });
    });
  };

  const handleCancelApproval = async (approvalId: string) => {
    await withBusy(async () => {
      await cancelTemplateApproval(approvalId);
      await refreshApprovals();
      addToast({ type: 'success', title: 'Cancelled', message: 'Approval request cancelled.' });
    });
  };

  const handleVersionPublish = async (version: TemplateVersion) => {
    if (!template) return;
    await withBusy(async () => {
      await publishTemplateVersion(template.id, version.versionNumber);
      addToast({ type: 'success', title: 'Version published', message: `Version v${version.versionNumber} published.` });
      await loadTemplateStudio();
    });
  };

  const handleVersionRollback = async (version: TemplateVersion) => {
    if (!template) return;
    await withBusy(async () => {
      await rollbackTemplate(template.id, version.versionNumber, {
        reason: `Rollback from Template Studio to v${version.versionNumber}`,
        publish: true,
      });
      addToast({ type: 'success', title: 'Rollback completed', message: `Rolled back to v${version.versionNumber}.` });
      await loadTemplateStudio();
    });
  };

  const handleTestSend = async () => {
    if (!template) return;
    if (!testEmail.trim()) {
      addToast({ type: 'warning', title: 'Email required', message: 'Enter a test recipient email.' });
      return;
    }
    await withBusy(async () => {
      await persistTemplate();
      await sendTemplateTestEmail(template.id, {
        email: testEmail.trim(),
        variables: personalizationVars,
      });
      addToast({ type: 'success', title: 'Test queued', message: `Test email queued for ${testEmail.trim()}.` });
    });
  };

  const handleExport = async () => {
    if (!template) return;
    try {
      const exported = await exportTemplateHtml(template.id);
      const blob = new Blob([exported.htmlContent], { type: 'text/html;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `${template.name.replace(/\s+/g, '-').toLowerCase()}.html`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Export failed',
        message: error?.response?.data?.error?.message || 'Unable to export HTML.',
      });
    }
  };

  const handleAssetSearch = async () => {
    try {
      const response = await listAssets({ page: 0, size: 40, q: assetQuery.trim() || undefined });
      const assetItems = Array.isArray(response) ? response : (response?.content ?? response?.data ?? []);
      setAssets(assetItems);
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Asset search failed',
        message: error?.response?.data?.error?.message || 'Unable to search assets.',
      });
    }
  };

  const handleAssetUpload = async (file: File) => {
    try {
      await uploadAsset(file);
      await handleAssetSearch();
      addToast({ type: 'success', title: 'Asset uploaded', message: `${file.name} uploaded.` });
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Upload failed',
        message: error?.response?.data?.error?.message || 'Unable to upload asset.',
      });
    }
  };

  const handleAssetBulkUpload = async (files: File[]) => {
    try {
      await uploadAssetsBulk(files);
      await handleAssetSearch();
      addToast({ type: 'success', title: 'Bulk upload complete', message: `${files.length} assets uploaded.` });
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Bulk upload failed',
        message: error?.response?.data?.error?.message || 'Unable to upload assets.',
      });
    }
  };

  if (loading) {
    return <div className="p-8 text-sm text-content-secondary">Loading Template Studio...</div>;
  }

  if (!template) {
    return (
      <EmptyState
        type="error"
        title="Template not found"
        description="This template does not exist or was deleted."
        action={<Link href="/app/email/templates"><Button variant="secondary">Back to Templates</Button></Link>}
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Template Studio</h1>
          <p className="mt-1 text-sm text-content-secondary">
            Build, preview, approve, and publish enterprise email content.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Link href="/app/email/templates">
            <Button variant="secondary">Back</Button>
          </Link>
          <Button variant="secondary" onClick={handleExport}>Export HTML</Button>
          <Button variant="secondary" onClick={handleSaveDraft} loading={isBusy}>Save Draft</Button>
          <Button onClick={handlePublishLatest} loading={isBusy}>Publish</Button>
        </div>
      </div>

      <Card>
        <div className="grid gap-4 p-6 md:grid-cols-2">
          <Input label="Template Name" value={name} onChange={(event) => setName(event.target.value)} />
          <Input label="Subject Line" value={subject} onChange={(event) => setSubject(event.target.value)} />
        </div>
        <div className="flex flex-wrap gap-2 px-6 pb-6">
          <Badge variant={template.status === 'PUBLISHED' ? 'success' : 'default'}>{template.status}</Badge>
          {template.category && <Badge variant="info">{template.category}</Badge>}
          {template.lastPublishedVersion != null && (
            <Badge variant="default">v{template.lastPublishedVersion}</Badge>
          )}
        </div>
      </Card>

      <Card>
        <Tabs
          defaultTab="builder"
          tabs={[
            { key: 'builder', label: 'Builder' },
            { key: 'preview', label: 'Preview & QA' },
            { key: 'versions', label: 'Versions' },
            { key: 'approvals', label: 'Approvals' },
            { key: 'assets', label: 'Assets' },
            { key: 'personalization', label: 'Personalization' },
          ]}
        >
          {(tab) => {
            if (tab === 'builder') {
              return (
                <div className="space-y-4">
                  <TemplateBuilder blocks={blocks} onBlocksChange={setBlocks} />
                  <Card>
                    <CardHeader title="Rendered HTML Snapshot" subtitle="Generated from current blocks." />
                    <div className="p-4">
                      <textarea
                        className="min-h-[220px] w-full rounded-lg border border-border-default bg-surface-secondary p-3 font-mono text-xs"
                        value={htmlFromBlocks}
                        onChange={(event) => setBlocks([defaultBlock(event.target.value)])}
                      />
                    </div>
                  </Card>
                </div>
              );
            }

            if (tab === 'preview') {
              return (
                <div className="space-y-4">
                  <div className="grid gap-4 md:grid-cols-3">
                    <div>
                      <label className="mb-1 block text-sm font-medium text-content-primary">Preview Mode</label>
                      <select
                        value={previewMode}
                        onChange={(event) => setPreviewMode(event.target.value)}
                        className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm"
                      >
                        <option value="desktop">Desktop</option>
                        <option value="tablet">Tablet</option>
                        <option value="mobile">Mobile</option>
                      </select>
                    </div>
                    <label className="flex items-center gap-2 pt-7 text-sm text-content-primary">
                      <input
                        type="checkbox"
                        checked={darkModePreview}
                        onChange={(event) => setDarkModePreview(event.target.checked)}
                      />
                      Dark mode preview hint
                    </label>
                    <Button className="mt-6" onClick={handlePreview}>Run Preview + Validation</Button>
                  </div>

                  <Card>
                    <CardHeader title={previewSubject || subject || 'Preview Subject'} />
                    <div className="rounded-lg border border-border-default bg-white p-4 text-black">
                      <div dangerouslySetInnerHTML={{ __html: previewHtml || htmlFromBlocks }} />
                    </div>
                  </Card>

                  {(previewWarnings.length > 0 || validation) && (
                    <Card>
                      <CardHeader title="Quality Checks" />
                      <div className="space-y-2 p-4 text-sm">
                        {previewWarnings.map((warning) => (
                          <p key={warning} className="text-warning">{warning}</p>
                        ))}
                        {validation && (
                          <>
                            <p>Links: {validation.linkCount} · Broken: {validation.brokenLinkCount}</p>
                            <p>Images: {validation.imageCount} · Missing alt: {validation.imagesMissingAlt}</p>
                            {validation.brokenLinks.length > 0 && (
                              <p className="text-danger">Broken links: {validation.brokenLinks.join(', ')}</p>
                            )}
                            {validation.warnings.length > 0 && (
                              <p className="text-warning">{validation.warnings.join(' | ')}</p>
                            )}
                          </>
                        )}
                      </div>
                    </Card>
                  )}
                </div>
              );
            }

            if (tab === 'versions') {
              return (
                <div className="space-y-4">
                  <VersionHistory
                    versions={versions}
                    onSelect={(version) => {
                      setCompareLeft(version.versionNumber);
                      if (compareRight == null || compareRight === version.versionNumber) {
                        setCompareRight(versions.find((item) => item.versionNumber !== version.versionNumber)?.versionNumber ?? null);
                      }
                    }}
                    onPublish={handleVersionPublish}
                    onRollback={handleVersionRollback}
                  />

                  <Card>
                    <CardHeader title="Compare Versions" />
                    <div className="grid gap-3 p-4 md:grid-cols-4">
                      <div>
                        <label className="mb-1 block text-sm font-medium">Left</label>
                        <select
                          value={compareLeft ?? ''}
                          onChange={(event) => setCompareLeft(Number(event.target.value))}
                          className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm"
                        >
                          {versions.map((version) => (
                            <option key={`left-${version.versionNumber}`} value={version.versionNumber}>
                              v{version.versionNumber}
                            </option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <label className="mb-1 block text-sm font-medium">Right</label>
                        <select
                          value={compareRight ?? ''}
                          onChange={(event) => setCompareRight(Number(event.target.value))}
                          className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm"
                        >
                          {versions.map((version) => (
                            <option key={`right-${version.versionNumber}`} value={version.versionNumber}>
                              v{version.versionNumber}
                            </option>
                          ))}
                        </select>
                      </div>
                      <Button className="mt-6" onClick={handleCompareVersions}>Compare</Button>
                    </div>
                    {compareResult && (
                      <div className="space-y-2 p-4 text-sm">
                        <p>Subject changed: {String(compareResult.subjectChanged)}</p>
                        <p>HTML changed: {String(compareResult.htmlChanged)}</p>
                        <p>Text changed: {String(compareResult.textChanged)}</p>
                        <p>HTML length: {compareResult.leftHtmlLength} vs {compareResult.rightHtmlLength}</p>
                      </div>
                    )}
                  </Card>
                </div>
              );
            }

            if (tab === 'approvals') {
              return (
                <div className="space-y-4">
                  <Card>
                    <CardHeader title="Submit Approval Request" />
                    <div className="grid gap-3 p-4 md:grid-cols-[1fr_auto]">
                      <Input
                        label="Comments"
                        value={approvalComment}
                        onChange={(event) => setApprovalComment(event.target.value)}
                        placeholder="Request review from campaign team..."
                      />
                      <Button className="mt-6" onClick={handleSubmitApproval} loading={isBusy}>
                        Submit for Approval
                      </Button>
                    </div>
                  </Card>

                  <Card>
                    <CardHeader title="Approval History" action={<Badge variant="info">{approvals.length}</Badge>} />
                    {approvals.length === 0 ? (
                      <div className="p-6 text-sm text-content-secondary">No approval requests yet.</div>
                    ) : (
                      <div className="divide-y divide-border-default">
                        {approvals.map((approval) => (
                          <div key={approval.id} className="flex flex-col gap-3 p-4 md:flex-row md:items-center md:justify-between">
                            <div>
                              <p className="font-medium text-content-primary">Version v{approval.versionNumber}</p>
                              <p className="text-xs text-content-secondary">
                                Requested by {approval.requestedBy || 'unknown'} · {approval.requestedAt ? new Date(approval.requestedAt).toLocaleString() : 'n/a'}
                              </p>
                              {approval.comments && <p className="mt-1 text-sm text-content-secondary">{approval.comments}</p>}
                              {approval.rejectionReason && <p className="mt-1 text-sm text-danger">Reason: {approval.rejectionReason}</p>}
                            </div>
                            <div className="flex items-center gap-2">
                              <Badge variant={approval.status === 'APPROVED' ? 'success' : approval.status === 'REJECTED' ? 'danger' : 'default'}>
                                {approval.status}
                              </Badge>
                              {approval.status === 'PENDING' && (
                                <>
                                  <Button size="sm" onClick={() => handleApprove(approval.id)}>Approve</Button>
                                  <Button size="sm" variant="danger" onClick={() => handleReject(approval.id)}>Reject</Button>
                                  <Button size="sm" variant="secondary" onClick={() => handleCancelApproval(approval.id)}>Cancel</Button>
                                </>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                    <div className="grid gap-3 p-4 md:grid-cols-[1fr_auto]">
                      <Input
                        label="Reject Reason"
                        value={rejectReason}
                        onChange={(event) => setRejectReason(event.target.value)}
                        placeholder="Provide reason before reject action"
                      />
                    </div>
                  </Card>
                </div>
              );
            }

            if (tab === 'assets') {
              return (
                <div className="space-y-4">
                  <Card>
                    <CardHeader title="Media Library" subtitle="Upload and reuse image, GIF, and logo assets." />
                    <div className="grid gap-3 p-4 md:grid-cols-[1fr_auto]">
                      <Input
                        label="Search assets"
                        value={assetQuery}
                        onChange={(event) => setAssetQuery(event.target.value)}
                        placeholder="logo, banner, gif..."
                      />
                      <Button className="mt-6" onClick={handleAssetSearch}>Search</Button>
                    </div>
                    <div className="p-4 pt-0">
                      <AssetUploader onUpload={handleAssetUpload} onBulkUpload={handleAssetBulkUpload} />
                    </div>
                  </Card>

                  <Card>
                    <CardHeader title="Available Assets" action={<Badge variant="info">{assets.length}</Badge>} />
                    {assets.length === 0 ? (
                      <div className="p-6 text-sm text-content-secondary">No assets found.</div>
                    ) : (
                      <div className="divide-y divide-border-default">
                        {assets.map((asset) => (
                          <div key={asset.id} className="flex items-center justify-between gap-2 p-3">
                            <div>
                              <p className="font-medium text-content-primary">{asset.name}</p>
                              <p className="text-xs text-content-secondary">{asset.contentType}</p>
                            </div>
                            <Button
                              size="sm"
                              variant="secondary"
                              onClick={() => {
                                setBlocks((current) => [
                                  ...current,
                                  {
                                    ...defaultBlock(`<img src="${asset.storagePath || ''}" alt="${asset.name}" />`),
                                    name: asset.name,
                                    blockType: 'IMAGE',
                                  },
                                ]);
                              }}
                            >
                              Insert
                            </Button>
                          </div>
                        ))}
                      </div>
                    )}
                  </Card>
                </div>
              );
            }

            return (
              <div className="space-y-4">
                <Card>
                  <CardHeader title="Personalization Variables" subtitle="Add merge tag values for preview and test sends." />
                  <div className="p-4">
                    <PersonalizationTester
                      onTest={(vars) => {
                        setPersonalizationVars(vars);
                        addToast({ type: 'success', title: 'Variables updated', message: 'Preview variables applied.' });
                      }}
                    />
                  </div>
                </Card>
                <Card>
                  <CardHeader title="Test Send" />
                  <div className="grid gap-3 p-4 md:grid-cols-[1fr_auto]">
                    <Input
                      label="Recipient Email"
                      value={testEmail}
                      onChange={(event) => setTestEmail(event.target.value)}
                      placeholder="qa-team@company.com"
                    />
                    <Button className="mt-6" onClick={handleTestSend} loading={isBusy}>
                      Send Test Email
                    </Button>
                  </div>
                </Card>
              </div>
            );
          }}
        </Tabs>
      </Card>
    </div>
  );
}
