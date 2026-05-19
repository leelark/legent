'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageChrome';
import { Skeleton } from '@/components/ui/Skeleton';
import { Tabs } from '@/components/ui/Tabs';
import { useToast } from '@/components/ui/Toast';
import { TemplateBuilder, ContentBlock } from '@/components/content/TemplateBuilder';
import { TemplateStudioCommandCenter } from '@/components/content/TemplateStudioCommandCenter';
import { VersionHistory } from '@/components/content/VersionHistory';
import { AssetUploader } from '@/components/content/AssetUploader';
import { PersonalizationTester } from '@/components/content/PersonalizationTester';
import { sanitizeEmailHtml } from '@/lib/sanitize-html';
import {
  Asset,
  BrandKit,
  ContentSnippet,
  DynamicContentRule,
  PersonalizationToken,
  Template,
  TemplateApproval,
  TestSendRecord,
  TemplateVersion,
  ValidationResponse,
  type VersionCompareResponse,
  approveTemplateApproval,
  cancelTemplateApproval,
  compareTemplateVersions,
  createBrandKit,
  createContentSnippet,
  createDynamicContentRule,
  createPersonalizationToken,
  createTemplateTestSendMatrix,
  createTemplateTestSend,
  exportTemplateHtml,
  getTemplate,
  getTemplateApprovals,
  listAssets,
  listBrandKits,
  listContentSnippets,
  listDynamicContentRules,
  listPersonalizationTokens,
  listTemplateTestSends,
  listTemplateVersions,
  publishTemplate,
  publishTemplateVersion,
  rejectTemplateApproval,
  renderTemplateEnterprise,
  rollbackTemplate,
  saveTemplateDraft,
  submitTemplateApproval,
  updateTemplate,
  uploadAsset,
  uploadAssetsBulk,
  validateTemplateEnterprise,
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
    borderColor: '#e2e8f0',
    borderWidth: 0,
    textAlign: 'left',
  },
  settings: {
    hideOnMobile: false,
    hideOnDesktop: false,
    visibilityRule: '',
  },
});

type ApiErrorLike = {
  normalized?: { message?: unknown };
  response?: { data?: { error?: { message?: unknown } } };
  message?: unknown;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function asArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) {
    return value as T[];
  }
  if (!isRecord(value)) {
    return [];
  }
  if (Array.isArray(value.content)) {
    return value.content as T[];
  }
  if (Array.isArray(value.data)) {
    return value.data as T[];
  }
  return [];
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!isRecord(error)) {
    return fallback;
  }
  const candidate = error as ApiErrorLike;
  const message =
    candidate.normalized?.message ??
    candidate.response?.data?.error?.message ??
    candidate.message;
  return typeof message === 'string' && message.trim() ? message : fallback;
}

const parseMetadata = (metadata?: string | null): Record<string, unknown> => {
  if (!metadata) return {};
  try {
    const parsed = JSON.parse(metadata);
    return isRecord(parsed) ? parsed : {};
  } catch {
    return {};
  }
};

const escapeHtmlAttribute = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

const numericBlockStyle = (styles: Record<string, unknown>, key: string, fallback: number) => {
  const value = Number(styles[key] ?? fallback);
  return Number.isFinite(value) ? Math.max(0, value) : fallback;
};

const colorBlockStyle = (styles: Record<string, unknown>, key: string, fallback: string) => {
  const value = styles[key];
  if (typeof value !== 'string') return fallback;
  return /^#[0-9A-Fa-f]{3,8}$/.test(value.trim()) ? value.trim() : fallback;
};

const alignBlockStyle = (styles: Record<string, unknown>) => {
  const value = styles.textAlign;
  return value === 'center' || value === 'right' ? value : 'left';
};

const blocksToHtml = (blocks: ContentBlock[]): string => {
  const rows = blocks.map((block, index) => {
    const styles = block.styles ?? {};
    const settings = block.settings ?? {};
    const padding = numericBlockStyle(styles, 'padding', 16);
    const radius = numericBlockStyle(styles, 'borderRadius', 8);
    const borderWidth = numericBlockStyle(styles, 'borderWidth', 0);
    const backgroundColor = colorBlockStyle(styles, 'backgroundColor', '#ffffff');
    const textColor = colorBlockStyle(styles, 'textColor', '#0f172a');
    const borderColor = colorBlockStyle(styles, 'borderColor', '#e2e8f0');
    const textAlign = alignBlockStyle(styles);
    const visibilityRule = typeof settings.visibilityRule === 'string' ? settings.visibilityRule.trim() : '';
    const classNames = [
      'legent-block',
      `legent-block-${block.id.replace(/[^A-Za-z0-9_-]/g, '-') || index}`,
      settings.hideOnMobile ? 'legent-hide-mobile' : '',
      settings.hideOnDesktop ? 'legent-hide-desktop' : '',
    ].filter(Boolean).join(' ');
    const cellStyles = [
      `padding:${padding}px`,
      `background:${backgroundColor}`,
      `color:${textColor}`,
      `border-radius:${radius}px`,
      `text-align:${textAlign}`,
      borderWidth > 0 ? `border:${borderWidth}px solid ${borderColor}` : '',
    ].filter(Boolean).join(';');
    return `
      <tr class="${classNames}"${visibilityRule ? ` data-legent-visibility-rule="${escapeHtmlAttribute(visibilityRule)}"` : ''}>
        <td style="${cellStyles};">
          ${sanitizeEmailHtml(block.content)}
        </td>
      </tr>
    `;
  });
  return `
    <style>
      @media screen and (max-width: 600px) {
        .legent-hide-mobile { display:none !important; max-height:0 !important; overflow:hidden !important; mso-hide:all !important; }
        .legent-hide-desktop { display:table-row !important; }
      }
      @media screen and (min-width: 601px) {
        .legent-hide-desktop { display:none !important; max-height:0 !important; overflow:hidden !important; mso-hide:all !important; }
      }
    </style>
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
  const [metadata, setMetadata] = useState<Record<string, unknown>>({});

  const [versions, setVersions] = useState<TemplateVersion[]>([]);
  const [approvals, setApprovals] = useState<TemplateApproval[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [snippets, setSnippets] = useState<ContentSnippet[]>([]);
  const [tokens, setTokens] = useState<PersonalizationToken[]>([]);
  const [dynamicRules, setDynamicRules] = useState<DynamicContentRule[]>([]);
  const [brandKits, setBrandKits] = useState<BrandKit[]>([]);
  const [testSendRecords, setTestSendRecords] = useState<TestSendRecord[]>([]);

  const [previewHtml, setPreviewHtml] = useState('');
  const [previewSubject, setPreviewSubject] = useState('');
  const [previewWarnings, setPreviewWarnings] = useState<string[]>([]);
  const [validation, setValidation] = useState<ValidationResponse | null>(null);
  const [previewMode, setPreviewMode] = useState('desktop');
  const [darkModePreview, setDarkModePreview] = useState(false);
  const [personalizationVars, setPersonalizationVars] = useState<Record<string, string>>({});

  const [compareLeft, setCompareLeft] = useState<number | null>(null);
  const [compareRight, setCompareRight] = useState<number | null>(null);
  const [compareResult, setCompareResult] = useState<VersionCompareResponse | null>(null);

  const [testEmail, setTestEmail] = useState('');
  const [testMatrixEmails, setTestMatrixEmails] = useState('qa-desktop@example.com\nqa-mobile@example.com');
  const [testMatrixResult, setTestMatrixResult] = useState<{ queued: number; failed: number; errors: string[] } | null>(null);
  const [recipientGroup, setRecipientGroup] = useState('QA');
  const [approvalComment, setApprovalComment] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [assetQuery, setAssetQuery] = useState('');
  const [snippetKey, setSnippetKey] = useState('footer.disclaimer');
  const [snippetName, setSnippetName] = useState('Footer Disclaimer');
  const [snippetContent, setSnippetContent] = useState('<p>Manage your preferences or unsubscribe.</p>');
  const [tokenKey, setTokenKey] = useState('firstName');
  const [tokenName, setTokenName] = useState('First name');
  const [tokenPath, setTokenPath] = useState('firstName');
  const [tokenDefault, setTokenDefault] = useState('there');
  const [dynamicSlot, setDynamicSlot] = useState('main');
  const [dynamicName, setDynamicName] = useState('Default dynamic block');
  const [dynamicField, setDynamicField] = useState('segment');
  const [dynamicValue, setDynamicValue] = useState('vip');
  const [dynamicHtml, setDynamicHtml] = useState('<p>Exclusive content for this audience.</p>');
  const [brandName, setBrandName] = useState('Default Brand Kit');
  const [brandPrimary, setBrandPrimary] = useState('#2563eb');
  const [brandFooter, setBrandFooter] = useState('<p style="font-size:12px;color:#64748b">You are receiving this email from Legent.</p>');
  const [isBusy, setIsBusy] = useState(false);
  const [loading, setLoading] = useState(true);

  const htmlFromBlocks = useMemo(() => blocksToHtml(blocks), [blocks]);

  const loadTemplateStudio = useCallback(async () => {
    if (!templateId) return;
    setLoading(true);
    try {
      const templateRes = await getTemplate(templateId);
      const [
        versionsResult,
        approvalsResult,
        assetsResult,
        snippetsResult,
        tokensResult,
        rulesResult,
        brandKitsResult,
        testSendsResult,
      ] = await Promise.allSettled([
        listTemplateVersions(templateId),
        getTemplateApprovals(templateId),
        listAssets({ page: 0, size: 40 }),
        listContentSnippets(0, 50),
        listPersonalizationTokens(0, 100),
        listDynamicContentRules(templateId),
        listBrandKits(0, 50),
        listTemplateTestSends(templateId),
      ]);

      const optional = <T,>(result: PromiseSettledResult<T>, fallback: T, label: string) => {
        if (result.status === 'fulfilled') {
          return result.value;
        }
        addToast({
          type: 'warning',
          title: `${label} unavailable`,
          message: getErrorMessage(result.reason, `Could not load ${label.toLowerCase()}.`),
        });
        return fallback;
      };

      const versionsRes = optional<TemplateVersion[]>(versionsResult, [], 'Versions');
      const approvalsRes = optional<TemplateApproval[]>(approvalsResult, [], 'Approvals');
      const assetsRes = optional<unknown>(assetsResult, { content: [] }, 'Assets');
      const snippetsRes = optional<unknown>(snippetsResult, { content: [] }, 'Snippets');
      const tokensRes = optional<unknown>(tokensResult, { content: [] }, 'Tokens');
      const rulesRes = optional<DynamicContentRule[]>(rulesResult, [], 'Dynamic rules');
      const brandKitsRes = optional<unknown>(brandKitsResult, { content: [] }, 'Brand kits');
      const testSendsRes = optional<TestSendRecord[]>(testSendsResult, [], 'Test sends');

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
      const assetItems = asArray<Asset>(assetsRes);
      setAssets(assetItems);
      setSnippets(asArray<ContentSnippet>(snippetsRes));
      setTokens(asArray<PersonalizationToken>(tokensRes));
      setDynamicRules(Array.isArray(rulesRes) ? rulesRes : []);
      setBrandKits(asArray<BrandKit>(brandKitsRes));
      setTestSendRecords(Array.isArray(testSendsRes) ? testSendsRes : []);

      if (versionsRes.length >= 2) {
        setCompareLeft(versionsRes[0].versionNumber);
        setCompareRight(versionsRes[1].versionNumber);
      } else {
        setCompareLeft(null);
        setCompareRight(null);
      }
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Failed to load template studio',
        message: getErrorMessage(error, 'Unable to load template details.'),
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
      await withBusy(async () => {
        await persistTemplate();
        const preview = await renderTemplateEnterprise(template.id, {
          variables: personalizationVars,
          publishedOnly: false,
        });
        setPreviewHtml(preview.htmlContent);
        setPreviewSubject(preview.subject);
        setPreviewWarnings([...(preview.warnings ?? []), ...(preview.compatibilityWarnings ?? [])]);
        const validationResult = await validateTemplateEnterprise(template.id, {
          variables: personalizationVars,
          publishedOnly: false,
        });
        setValidation(validationResult);
        addToast({ type: 'success', title: 'QA refreshed', message: 'Current draft was saved before render validation.' });
      });
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Preview failed',
        message: getErrorMessage(error, 'Unable to generate preview.'),
      });
    }
  };

  const handleCompareVersions = async () => {
    if (!template || compareLeft == null || compareRight == null) return;
    try {
      const compare = await compareTemplateVersions(template.id, compareLeft, compareRight);
      setCompareResult(compare);
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Compare failed',
        message: getErrorMessage(error, 'Unable to compare versions.'),
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
      await createTemplateTestSend(template.id, {
        email: testEmail.trim(),
        recipientGroup: recipientGroup.trim() || undefined,
        variables: personalizationVars,
      });
      setTestSendRecords(await listTemplateTestSends(template.id));
      addToast({ type: 'success', title: 'Test queued', message: `Test email queued for ${testEmail.trim()}.` });
    });
  };

  const handleTestMatrix = async () => {
    if (!template) return;
    const recipients = testMatrixEmails
      .split(/\r?\n|,/)
      .map((email) => email.trim())
      .filter(Boolean)
      .map((email) => ({
        email,
        recipientGroup: recipientGroup.trim() || 'QA',
        variables: personalizationVars,
      }));
    if (recipients.length === 0) {
      addToast({ type: 'warning', title: 'Recipients required', message: 'Add at least one matrix recipient.' });
      return;
    }
    await withBusy(async () => {
      await persistTemplate();
      const result = await createTemplateTestSendMatrix(template.id, {
        matrixName: `${template.name} QA Matrix`,
        recipients,
      });
      setTestMatrixResult({ queued: result.queued, failed: result.failed, errors: result.errors || [] });
      setTestSendRecords(await listTemplateTestSends(template.id));
      addToast({ type: result.failed > 0 ? 'warning' : 'success', title: 'Matrix complete', message: `${result.queued} queued, ${result.failed} failed.` });
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
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Export failed',
        message: getErrorMessage(error, 'Unable to export HTML.'),
      });
    }
  };

  const handleAssetSearch = async () => {
    try {
      const response = await listAssets({ page: 0, size: 40, q: assetQuery.trim() || undefined });
      const assetItems = asArray<Asset>(response);
      setAssets(assetItems);
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Asset search failed',
        message: getErrorMessage(error, 'Unable to search assets.'),
      });
    }
  };

  const handleAssetUpload = async (file: File) => {
    try {
      await uploadAsset(file);
      await handleAssetSearch();
      addToast({ type: 'success', title: 'Asset uploaded', message: `${file.name} uploaded.` });
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Upload failed',
        message: getErrorMessage(error, 'Unable to upload asset.'),
      });
    }
  };

  const handleAssetBulkUpload = async (files: File[]) => {
    try {
      await uploadAssetsBulk(files);
      await handleAssetSearch();
      addToast({ type: 'success', title: 'Bulk upload complete', message: `${files.length} assets uploaded.` });
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Bulk upload failed',
        message: getErrorMessage(error, 'Unable to upload assets.'),
      });
    }
  };

  const handleCreateSnippet = async () => {
    await withBusy(async () => {
      await createContentSnippet({
        snippetKey: snippetKey.trim(),
        name: snippetName.trim(),
        snippetType: 'HTML',
        content: snippetContent,
        isGlobal: true,
      });
      const response = await listContentSnippets(0, 50);
      setSnippets(asArray<ContentSnippet>(response));
      addToast({ type: 'success', title: 'Snippet saved', message: `{{snippet.${snippetKey.trim()}}} is available.` });
    });
  };

  const handleCreateToken = async () => {
    await withBusy(async () => {
      await createPersonalizationToken({
        tokenKey: tokenKey.trim(),
        displayName: tokenName.trim(),
        dataPath: tokenPath.trim(),
        defaultValue: tokenDefault,
        sampleValue: tokenDefault,
        required: false,
      });
      const response = await listPersonalizationTokens(0, 100);
      setTokens(asArray<PersonalizationToken>(response));
      addToast({ type: 'success', title: 'Token registered', message: `{{${tokenKey.trim()}}} can now render safely.` });
    });
  };

  const handleCreateDynamicRule = async () => {
    if (!template) return;
    await withBusy(async () => {
      const rule = await createDynamicContentRule(template.id, {
        slotKey: dynamicSlot.trim(),
        name: dynamicName.trim(),
        priority: dynamicRules.length + 1,
        conditionField: dynamicField.trim(),
        operator: dynamicField.trim() ? 'EQUALS' : 'ALWAYS',
        conditionValue: dynamicValue.trim(),
        htmlContent: dynamicHtml,
        textContent: toText(dynamicHtml),
        active: true,
      });
      setDynamicRules((current) => [...current, rule].sort((a, b) => a.priority - b.priority));
      addToast({ type: 'success', title: 'Dynamic rule saved', message: `{{dynamic.${rule.slotKey}}} is ready.` });
    });
  };

  const handleCreateBrandKit = async () => {
    await withBusy(async () => {
      const brand = await createBrandKit({
        name: brandName.trim(),
        primaryColor: brandPrimary.trim(),
        footerHtml: brandFooter,
        isDefault: brandKits.length === 0,
      });
      setBrandKits((current) => [brand, ...current.filter((item) => item.id !== brand.id)]);
      addToast({ type: 'success', title: 'Brand kit saved', message: `${brand.name} is available for rendering.` });
    });
  };

  const previewFrameWidth =
    previewMode === 'mobile' ? '390px' : previewMode === 'tablet' ? '768px' : '100%';
  const previewFrameClassName = darkModePreview
    ? 'mx-auto min-h-[360px] rounded-lg border border-slate-700 bg-slate-950 p-4 text-slate-100 shadow-inner'
    : 'mx-auto min-h-[360px] rounded-lg border border-border-default bg-white p-4 text-black shadow-inner';

  if (loading) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Content operations"
          title="Template Studio"
          description="Loading builder state, approvals, assets, and render data."
        />
        <Card>
          <div className="space-y-4">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-2/3" />
            <Skeleton className="h-64 w-full" />
          </div>
        </Card>
      </div>
    );
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
      <PageHeader
        eyebrow="Content operations"
        title="Template Studio"
        description="Build, preview, approve, and publish enterprise email content."
        action={(
        <div className="flex flex-wrap items-center gap-2">
          <Link href="/app/email/templates">
            <Button variant="secondary">Back</Button>
          </Link>
          <Button variant="secondary" onClick={handleExport}>Export HTML</Button>
          <Button variant="secondary" onClick={handleSaveDraft} loading={isBusy}>Save Draft</Button>
          <Button onClick={handlePublishLatest} loading={isBusy}>Publish</Button>
        </div>
        )}
      />

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

      <TemplateStudioCommandCenter
        template={template}
        name={name}
        subject={subject}
        blocks={blocks}
        previewWarnings={previewWarnings}
        validation={validation}
        versions={versions}
        approvals={approvals}
        assets={assets}
        snippets={snippets}
        tokens={tokens}
        dynamicRules={dynamicRules}
        brandKits={brandKits}
        testSendRecords={testSendRecords}
        isBusy={isBusy}
        onSaveDraft={handleSaveDraft}
        onRunPreview={handlePreview}
        onSubmitApproval={handleSubmitApproval}
        onPublish={handlePublishLatest}
      />

      <Card>
        <Tabs
          defaultTab="builder"
          tabs={[
            { key: 'builder', label: 'Builder' },
            { key: 'blocks', label: 'Blocks/Snippets' },
            { key: 'dynamic', label: 'Dynamic Rules' },
            { key: 'tokens', label: 'Tokens' },
            { key: 'preview', label: 'Preview & QA' },
            { key: 'versions', label: 'Versions' },
            { key: 'approvals', label: 'Approvals' },
            { key: 'assets', label: 'Assets' },
            { key: 'brand', label: 'Brand Kit' },
            { key: 'tests', label: 'Test Sends' },
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

            if (tab === 'blocks') {
              return (
                <div className="space-y-4">
                  <Card>
                    <CardHeader title="Reusable Snippets" subtitle="Insert with {{snippet.key}} in templates and dynamic rules." />
                    <div className="grid gap-3 p-4 md:grid-cols-3">
                      <Input label="Key" value={snippetKey} onChange={(event) => setSnippetKey(event.target.value)} />
                      <Input label="Name" value={snippetName} onChange={(event) => setSnippetName(event.target.value)} />
                      <Button className="mt-6" onClick={handleCreateSnippet} loading={isBusy}>Save Snippet</Button>
                    </div>
                    <div className="p-4 pt-0">
                      <textarea
                        value={snippetContent}
                        onChange={(event) => setSnippetContent(event.target.value)}
                        rows={4}
                        className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 font-mono text-xs"
                      />
                    </div>
                  </Card>
                  <div className="grid gap-3 md:grid-cols-2">
                    {snippets.map((snippet) => (
                      <div key={snippet.id} className="rounded-lg border border-border-default p-3">
                        <p className="font-medium text-content-primary">{snippet.name}</p>
                        <p className="mt-1 font-mono text-xs text-content-secondary">{`{{snippet.${snippet.snippetKey}}}`}</p>
                        <p className="mt-2 line-clamp-2 text-xs text-content-muted">{snippet.content}</p>
                      </div>
                    ))}
                  </div>
                </div>
              );
            }

            if (tab === 'dynamic') {
              return (
                <div className="space-y-4">
                  <Card>
                    <CardHeader title="Dynamic Content Rules" subtitle="Rules resolve slots like {{dynamic.main}} by priority." />
                    <div className="grid gap-3 p-4 md:grid-cols-5">
                      <Input label="Slot" value={dynamicSlot} onChange={(event) => setDynamicSlot(event.target.value)} />
                      <Input label="Name" value={dynamicName} onChange={(event) => setDynamicName(event.target.value)} />
                      <Input label="Field" value={dynamicField} onChange={(event) => setDynamicField(event.target.value)} />
                      <Input label="Value" value={dynamicValue} onChange={(event) => setDynamicValue(event.target.value)} />
                      <Button className="mt-6" onClick={handleCreateDynamicRule} loading={isBusy}>Save Rule</Button>
                    </div>
                    <div className="p-4 pt-0">
                      <textarea
                        value={dynamicHtml}
                        onChange={(event) => setDynamicHtml(event.target.value)}
                        rows={4}
                        className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 font-mono text-xs"
                      />
                    </div>
                  </Card>
                  <div className="divide-y divide-border-default rounded-lg border border-border-default">
                    {dynamicRules.length === 0 ? (
                      <div className="p-4 text-sm text-content-secondary">No dynamic rules configured.</div>
                    ) : dynamicRules.map((rule) => (
                      <div key={rule.id} className="flex flex-col gap-2 p-3 md:flex-row md:items-center md:justify-between">
                        <div>
                          <p className="font-medium text-content-primary">{rule.name}</p>
                          <p className="text-xs text-content-secondary">
                            {`{{dynamic.${rule.slotKey}}}`} - {rule.operator} {rule.conditionField} {rule.conditionValue}
                          </p>
                        </div>
                        <Badge variant={rule.active ? 'success' : 'default'}>{rule.active ? 'Active' : 'Disabled'}</Badge>
                      </div>
                    ))}
                  </div>
                </div>
              );
            }

            if (tab === 'tokens') {
              return (
                <div className="space-y-4">
                  <Card>
                    <CardHeader title="Personalization Token Registry" subtitle="Only registered tokens are allowed in production renders." />
                    <div className="grid gap-3 p-4 md:grid-cols-5">
                      <Input label="Token" value={tokenKey} onChange={(event) => setTokenKey(event.target.value)} />
                      <Input label="Name" value={tokenName} onChange={(event) => setTokenName(event.target.value)} />
                      <Input label="Data Path" value={tokenPath} onChange={(event) => setTokenPath(event.target.value)} />
                      <Input label="Default" value={tokenDefault} onChange={(event) => setTokenDefault(event.target.value)} />
                      <Button className="mt-6" onClick={handleCreateToken} loading={isBusy}>Register</Button>
                    </div>
                  </Card>
                  <div className="grid gap-3 md:grid-cols-3">
                    {tokens.map((token) => (
                      <div key={token.id} className="rounded-lg border border-border-default p-3">
                        <p className="font-medium text-content-primary">{token.displayName}</p>
                        <p className="mt-1 font-mono text-xs text-content-secondary">{`{{${token.tokenKey}}}`}</p>
                        <p className="mt-1 text-xs text-content-muted">{token.dataPath || token.tokenKey}</p>
                      </div>
                    ))}
                  </div>
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
                      Dark mode preview
                    </label>
                    <Button className="mt-6" onClick={handlePreview} loading={isBusy}>Save + Run QA</Button>
                  </div>

                  <Card>
                    <CardHeader title={previewSubject || subject || 'Preview Subject'} />
                    <div className="rounded-lg border border-border-default bg-surface-secondary p-4">
                      <div
                        className={previewFrameClassName}
                        style={{ maxWidth: previewFrameWidth }}
                        data-testid="template-preview-frame"
                      >
                        <div dangerouslySetInnerHTML={{ __html: sanitizeEmailHtml(previewHtml || htmlFromBlocks) }} />
                      </div>
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
                            <p>Links: {validation.linkCount ?? 0} - Broken: {validation.brokenLinkCount ?? 0}</p>
                            <p>Images: {validation.imageCount ?? 0} - Missing alt: {validation.imagesMissingAlt ?? 0}</p>
                            {(validation.brokenLinks ?? []).length > 0 && (
                              <p className="text-danger">Broken links: {(validation.brokenLinks ?? []).join(', ')}</p>
                            )}
                            {(validation.errors ?? []).length > 0 && (
                              <p className="text-danger">{(validation.errors ?? []).join(' | ')}</p>
                            )}
                            {(validation.warnings ?? []).length > 0 && (
                              <p className="text-warning">{(validation.warnings ?? []).join(' | ')}</p>
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
                                Requested by {approval.requestedBy || 'unknown'} - {approval.requestedAt ? new Date(approval.requestedAt).toLocaleString() : 'n/a'}
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

            if (tab === 'brand') {
              return (
                <div className="space-y-4">
                  <Card>
                    <CardHeader title="Brand Kit" subtitle="Reusable fonts, colors, footer, and legal blocks for renderer output." />
                    <div className="grid gap-3 p-4 md:grid-cols-4">
                      <Input label="Name" value={brandName} onChange={(event) => setBrandName(event.target.value)} />
                      <Input label="Primary Color" value={brandPrimary} onChange={(event) => setBrandPrimary(event.target.value)} />
                      <Button className="mt-6" onClick={handleCreateBrandKit} loading={isBusy}>Save Brand Kit</Button>
                    </div>
                    <div className="p-4 pt-0">
                      <label className="mb-1 block text-sm font-medium text-content-primary">Footer HTML</label>
                      <textarea
                        value={brandFooter}
                        onChange={(event) => setBrandFooter(event.target.value)}
                        rows={4}
                        className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 font-mono text-xs"
                      />
                    </div>
                  </Card>
                  <div className="grid gap-3 md:grid-cols-3">
                    {brandKits.map((brand) => (
                      <div key={brand.id} className="rounded-lg border border-border-default p-3">
                        <div className="flex items-center justify-between gap-2">
                          <p className="font-medium text-content-primary">{brand.name}</p>
                          {brand.isDefault && <Badge variant="success">Default</Badge>}
                        </div>
                        <div className="mt-3 h-3 rounded" style={{ backgroundColor: brand.primaryColor || '#64748b' }} />
                        <p className="mt-2 text-xs text-content-secondary">{brand.defaultFromEmail || 'No default sender'}</p>
                      </div>
                    ))}
                  </div>
                </div>
              );
            }

            if (tab === 'tests') {
              return (
                <div className="space-y-4">
                  <Card>
                    <CardHeader title="Personalization Variables" subtitle="Values are used by preview validation and test sends." />
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
                    <div className="grid gap-3 p-4 md:grid-cols-[1fr_220px_auto]">
                      <Input
                        label="Recipient Email"
                        value={testEmail}
                        onChange={(event) => setTestEmail(event.target.value)}
                        placeholder="qa-team@company.com"
                      />
                      <Input
                        label="Recipient Group"
                        value={recipientGroup}
                        onChange={(event) => setRecipientGroup(event.target.value)}
                      />
                      <Button className="mt-6" onClick={handleTestSend} loading={isBusy}>
                        Send Test Email
                      </Button>
                    </div>
                  </Card>
                  <Card>
                    <CardHeader title="Test Send Matrix" />
                    <div className="grid gap-3 p-4 md:grid-cols-[1fr_auto]">
                      <div>
                        <label className="mb-1 block text-sm font-medium text-content-primary">Recipients</label>
                        <textarea
                          value={testMatrixEmails}
                          onChange={(event) => setTestMatrixEmails(event.target.value)}
                          rows={4}
                          className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
                        />
                      </div>
                      <Button className="mt-6" onClick={handleTestMatrix} loading={isBusy}>
                        Run Matrix
                      </Button>
                    </div>
                    {testMatrixResult && (
                      <div className="border-t border-border-default p-4 text-sm">
                        <p className="font-medium text-content-primary">
                          Queued {testMatrixResult.queued} - Failed {testMatrixResult.failed}
                        </p>
                        {testMatrixResult.errors.length > 0 && (
                          <div className="mt-2 space-y-1 text-danger">
                            {testMatrixResult.errors.map((error) => <p key={error}>{error}</p>)}
                          </div>
                        )}
                      </div>
                    )}
                  </Card>
                  <div className="divide-y divide-border-default rounded-lg border border-border-default">
                    {testSendRecords.length === 0 ? (
                      <div className="p-4 text-sm text-content-secondary">No test sends recorded.</div>
                    ) : testSendRecords.map((record) => (
                      <div key={record.id} className="flex flex-col gap-1 p-3 md:flex-row md:items-center md:justify-between">
                        <div>
                          <p className="font-medium text-content-primary">{record.recipientEmail}</p>
                          <p className="text-xs text-content-secondary">{record.subject || 'No subject'} - {record.createdAt ? new Date(record.createdAt).toLocaleString() : 'n/a'}</p>
                          {record.errorMessage && <p className="text-xs text-danger">{record.errorMessage}</p>}
                        </div>
                        <Badge variant={record.status === 'QUEUED' ? 'success' : record.status === 'FAILED' ? 'danger' : 'default'}>{record.status}</Badge>
                      </div>
                    ))}
                  </div>
                </div>
              );
            }

            return (
              <div className="space-y-4">
                <div className="p-4 text-sm text-content-secondary">Select a studio tab.</div>
              </div>
            );
          }}
        </Tabs>
      </Card>
    </div>
  );
}
