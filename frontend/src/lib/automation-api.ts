import { del, get, post, put } from '@/lib/api-client';

export type WorkflowStatus =
  | 'DRAFT'
  | 'ACTIVE'
  | 'PAUSED'
  | 'SCHEDULED'
  | 'STOPPED'
  | 'ARCHIVED'
  | 'FAILED'
  | 'ROLLED_BACK';

export interface Workflow {
  id: string;
  tenantId?: string;
  workspaceId?: string;
  teamId?: string;
  ownershipScope?: string;
  name: string;
  description?: string;
  status: WorkflowStatus;
  activeDefinitionVersion?: number;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkflowSchedule {
  id: string;
  workflowId: string;
  cronExpression?: string;
  timezone?: string;
  enabled?: boolean;
  nextRunAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkflowConditionEdge {
  condition?: string;
  targetNodeId?: string;
}

export interface WorkflowGraphNode {
  id?: string;
  type?: string;
  configuration?: Record<string, unknown>;
  nextNodeId?: string;
  branches?: WorkflowConditionEdge[];
}

export interface WorkflowGraphDefinition {
  graphVersion?: number;
  initialNodeId?: string;
  nodes?: Record<string, WorkflowGraphNode>;
  edges?: Record<string, unknown>[];
  entryPolicy?: Record<string, unknown>;
  reentryPolicy?: Record<string, unknown>;
}

export interface WorkflowCapabilities {
  entrySources?: number;
  waits?: number;
  decisions?: number;
  goals?: number;
  exits?: number;
  reentryGates?: number;
  nodeTypeCounts?: Record<string, number>;
  runtimeSupportedNodeTypes?: string[];
  runtimeUnsupportedNodes?: string[];
  entryPolicy?: Record<string, unknown> | null;
  reentryPolicy?: Record<string, unknown> | null;
}

export interface WorkflowValidationResponse {
  valid?: boolean;
  graphVersion?: number;
  nodeCount?: number;
  initialNodeId?: string;
  runtimeSupported?: boolean;
  errors?: string[];
  capabilities?: WorkflowCapabilities;
}

export interface WorkflowDefinitionVersion {
  workflowId: string;
  version: number;
  tenantId?: string;
  workspaceId?: string;
  graphVersion?: number;
  published?: boolean;
  definition?: string | WorkflowGraphDefinition | null;
  graph?: WorkflowGraphDefinition | null;
  createdAt?: string;
}

export interface WorkflowRun {
  id: string;
  tenantId?: string;
  workspaceId?: string;
  teamId?: string;
  ownershipScope?: string;
  environmentId?: string;
  requestId?: string;
  correlationId?: string;
  workflowId: string;
  version?: number;
  subscriberId?: string;
  status?: string;
  currentNodeId?: string;
  context?: string | Record<string, unknown> | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkflowRunStep {
  id: string;
  tenantId?: string;
  workspaceId?: string;
  instanceId?: string;
  nodeId?: string;
  status?: string;
  eventType?: string;
  correlationId?: string;
  details?: string | Record<string, unknown> | null;
  errorMessage?: string;
  executedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  deletedAt?: string;
  version?: number;
}

export type AutomationActivityType = 'SQL_QUERY' | 'FILE_DROP' | 'IMPORT' | 'EXTRACT' | 'SCRIPT' | 'WEBHOOK' | 'NOTIFICATION' | 'SEND_EMAIL';
export type AutomationActivityStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
export type AutomationFailurePolicy = 'STOP_ON_FAILURE' | 'SKIP_DEPENDENTS' | 'CONTINUE_INDEPENDENT';

export interface AutomationVerificationResponse {
  valid: boolean;
  errors: string[];
  warnings: string[];
  normalizedConfig: Record<string, unknown>;
}

export interface AutomationActivity {
  id: string;
  name: string;
  activityType: AutomationActivityType;
  status: AutomationActivityStatus;
  scheduleExpression?: string;
  dependencyActivityIds?: string[];
  failurePolicy?: AutomationFailurePolicy;
  inputConfig?: Record<string, unknown>;
  outputConfig?: Record<string, unknown>;
  verification?: AutomationVerificationResponse | Record<string, unknown>;
  lastRunAt?: string;
  nextRunAt?: string;
}

export interface AutomationActivityRun {
  id: string;
  activityId: string;
  status: 'VERIFIED' | 'SUCCEEDED' | 'FAILED' | 'LOCKED';
  dryRun: boolean;
  triggerSource?: string;
  rowsRead?: number;
  rowsWritten?: number;
  traceId?: string;
  errorCode?: string;
  errorMessage?: string;
  idempotencyKey?: string;
  retryAfterSeconds?: number;
  lockedUntil?: string;
  lockOwnerRunId?: string;
  operatorOverride?: boolean;
  overrideReason?: string;
  dependencyTrace?: Record<string, unknown>;
  result?: Record<string, unknown>;
  startedAt?: string;
  completedAt?: string;
}

export interface AutomationDataExtensionOption {
  id: string;
  name?: string;
  sendable?: boolean;
  recordCount?: number;
  governance?: {
    sourceType?: string;
    dataClassification?: string;
  };
}

export interface AutomationUserOption {
  id: string;
  email: string;
  firstName?: string | null;
  lastName?: string | null;
  role?: string;
  roles?: string[];
  isActive?: boolean;
}

export interface AutomationArtifactResponse {
  artifactId: string;
  activityId?: string;
  sourceKind?: string;
  status?: string;
  displayName?: string;
  contentType?: string;
  sizeBytes?: number;
  retentionPolicy?: string;
  expiresAt?: string;
  createdAt?: string;
}

type PagedResponse<T> = {
  content?: T[];
  data?: T[];
};

const pageItems = <T>(response: PagedResponse<T> | T[] | null | undefined) => {
  if (!response) {
    return [];
  }
  return Array.isArray(response) ? response : response.content ?? response.data ?? [];
};

export const listWorkflows = async () => get<Workflow[]>('/workflows');
export const createWorkflow = async (payload: { name: string; description?: string; status?: string }) =>
  post<Workflow>('/workflows', payload);
export const getWorkflow = async (id: string) => get<Workflow>(`/workflows/${id}`);
export const updateWorkflow = async (id: string, payload: Record<string, unknown>) => put<Workflow>(`/workflows/${id}`, payload);

export const validateWorkflow = async (id: string, graph: unknown) =>
  post<WorkflowValidationResponse>(`/workflows/${id}/validate`, graph);
export const saveWorkflowDefinition = async (
  id: string,
  graph: unknown,
  options?: { version?: number; published?: boolean }
) => {
  const qp = new URLSearchParams();
  if (options?.version != null) qp.set('version', String(options.version));
  if (options?.published != null) qp.set('published', String(options.published));
  const suffix = qp.toString() ? `?${qp.toString()}` : '';
  return post<Record<string, unknown>>(`/workflows/${id}/definitions${suffix}`, graph);
};

export const publishWorkflow = async (id: string, version?: number) =>
  post<Workflow>(`/workflows/${id}/publish`, version != null ? { version } : {});
export const pauseWorkflow = async (id: string) => post<Workflow>(`/workflows/${id}/pause`, {});
export const resumeWorkflow = async (id: string) => post<Workflow>(`/workflows/${id}/resume`, {});
export const stopWorkflow = async (id: string) => post<Workflow>(`/workflows/${id}/stop`, {});
export const archiveWorkflow = async (id: string) => post<Workflow>(`/workflows/${id}/archive`, {});
export const rollbackWorkflow = async (id: string, version: number) => post<Workflow>(`/workflows/${id}/rollback`, { version });
export const cloneWorkflow = async (id: string) => post<Workflow>(`/workflows/${id}/clone`, {});

export const listWorkflowVersions = async (id: string) => get<WorkflowDefinitionVersion[]>(`/workflows/${id}/versions`);
export const getWorkflowVersion = async (id: string, version: number) => get<WorkflowDefinitionVersion>(`/workflows/${id}/versions/${version}`);
export const getLatestWorkflowDefinition = async (id: string) => get<WorkflowDefinitionVersion>(`/workflow-definitions/${id}/latest`);
export const compareWorkflowVersions = async (id: string, leftVersion: number, rightVersion: number) =>
  post<Record<string, unknown>>(`/workflows/${id}/compare`, { leftVersion, rightVersion });

export const triggerWorkflow = async (id: string, payload: Record<string, unknown>) =>
  post<Record<string, unknown>>(`/workflows/${id}/trigger`, payload);
export const simulateWorkflow = async (id: string, payload: Record<string, unknown>) =>
  post<Record<string, unknown>>(`/workflows/${id}/simulate`, payload);
export const dryRunWorkflow = async (id: string, payload: Record<string, unknown>) =>
  post<Record<string, unknown>>(`/workflows/${id}/dry-run`, payload);

export const listWorkflowRuns = async (id: string) => get<WorkflowRun[]>(`/workflows/${id}/runs`);
export const getWorkflowRun = async (runId: string) => get<WorkflowRun>(`/workflows/runs/${runId}`);
export const getWorkflowRunSteps = async (runId: string) => get<WorkflowRunStep[]>(`/workflows/runs/${runId}/steps`);
export const getWorkflowRunTrace = async (runId: string) => get<Record<string, unknown>>(`/workflows/runs/${runId}/trace`);

export const listWorkflowSchedules = async (id: string) => get<WorkflowSchedule[]>(`/workflows/${id}/schedules`);
export const createWorkflowSchedule = async (id: string, payload: Record<string, unknown>) =>
  post<WorkflowSchedule>(`/workflows/${id}/schedules`, payload);
export const updateWorkflowSchedule = async (id: string, scheduleId: string, payload: Record<string, unknown>) =>
  put<WorkflowSchedule>(`/workflows/${id}/schedules/${scheduleId}`, payload);
export const deleteWorkflowSchedule = async (id: string, scheduleId: string) =>
  del<{ deleted: boolean }>(`/workflows/${id}/schedules/${scheduleId}`);

export const listAutomationActivities = async () => get<AutomationActivity[]>('/automation-studio/activities');
export const listAutomationDataExtensions = async () =>
  pageItems(await get<PagedResponse<AutomationDataExtensionOption> | AutomationDataExtensionOption[]>('/data-extensions?page=0&size=100'));
export const listAutomationUsers = async () => pageItems(await get<PagedResponse<AutomationUserOption> | AutomationUserOption[]>('/users'));
export const getAutomationArtifact = async (artifactId: string) =>
  get<AutomationArtifactResponse>(`/automation-studio/artifacts/${encodeURIComponent(artifactId)}`);
export const createAutomationActivity = async (payload: {
  name: string;
  activityType: AutomationActivityType;
  status?: AutomationActivityStatus;
  scheduleExpression?: string;
  dependencyActivityIds?: string[];
  failurePolicy?: AutomationFailurePolicy;
  inputConfig?: Record<string, unknown>;
  outputConfig?: Record<string, unknown>;
}) => post<AutomationActivity>('/automation-studio/activities', payload);
export const verifyAutomationActivity = async (id: string) =>
  post<AutomationVerificationResponse>(`/automation-studio/activities/${id}/verify`, {});
export const runAutomationActivity = async (id: string, payload: {
  dryRun?: boolean;
  confirmLiveRun?: boolean;
  idempotencyKey?: string;
  triggerSource?: string;
  operatorOverride?: boolean;
  overrideReason?: string;
  overrides?: Record<string, unknown>;
}) =>
  post<AutomationActivityRun>(`/automation-studio/activities/${id}/runs`, payload);
export const listAutomationActivityRuns = async (id: string, limit = 50) =>
  get<AutomationActivityRun[]>(`/automation-studio/activities/${id}/runs?limit=${limit}`);
export const getWorkflowCapabilities = async (id: string) => get<WorkflowValidationResponse>(`/workflows/${id}/capabilities`);
export const getWorkflowAnalytics = async (id: string) => get<Record<string, unknown>>(`/workflows/${id}/analytics`);
