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

export type AutomationActivityType = 'SQL_QUERY' | 'FILE_DROP' | 'IMPORT' | 'EXTRACT' | 'SCRIPT' | 'WEBHOOK';
export type AutomationActivityStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED';

export interface AutomationActivity {
  id: string;
  name: string;
  activityType: AutomationActivityType;
  status: AutomationActivityStatus;
  scheduleExpression?: string;
  inputConfig?: Record<string, unknown>;
  outputConfig?: Record<string, unknown>;
  verification?: Record<string, unknown>;
  lastRunAt?: string;
  nextRunAt?: string;
}

export interface AutomationActivityRun {
  id: string;
  activityId: string;
  status: 'VERIFIED' | 'SUCCEEDED' | 'FAILED';
  dryRun: boolean;
  triggerSource?: string;
  rowsRead?: number;
  rowsWritten?: number;
  errorMessage?: string;
  result?: Record<string, unknown>;
  startedAt?: string;
  completedAt?: string;
}

export const listWorkflows = async () => get<Workflow[]>('/workflows');
export const createWorkflow = async (payload: { name: string; description?: string; status?: string }) =>
  post<Workflow>('/workflows', payload);
export const getWorkflow = async (id: string) => get<Workflow>(`/workflows/${id}`);
export const updateWorkflow = async (id: string, payload: Record<string, unknown>) => put<Workflow>(`/workflows/${id}`, payload);

export const validateWorkflow = async (id: string, graph: Record<string, unknown>) =>
  post<Record<string, unknown>>(`/workflows/${id}/validate`, graph);
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

export const listWorkflowVersions = async (id: string) => get<any[]>(`/workflows/${id}/versions`);
export const getWorkflowVersion = async (id: string, version: number) => get<any>(`/workflows/${id}/versions/${version}`);
export const getLatestWorkflowDefinition = async (id: string) => get<any>(`/workflow-definitions/${id}/latest`);
export const compareWorkflowVersions = async (id: string, leftVersion: number, rightVersion: number) =>
  post<Record<string, unknown>>(`/workflows/${id}/compare`, { leftVersion, rightVersion });

export const triggerWorkflow = async (id: string, payload: Record<string, unknown>) =>
  post<Record<string, unknown>>(`/workflows/${id}/trigger`, payload);
export const simulateWorkflow = async (id: string, payload: Record<string, unknown>) =>
  post<Record<string, unknown>>(`/workflows/${id}/simulate`, payload);
export const dryRunWorkflow = async (id: string, payload: Record<string, unknown>) =>
  post<Record<string, unknown>>(`/workflows/${id}/dry-run`, payload);

export const listWorkflowRuns = async (id: string) => get<any[]>(`/workflows/${id}/runs`);
export const getWorkflowRun = async (runId: string) => get<any>(`/workflows/runs/${runId}`);
export const getWorkflowRunSteps = async (runId: string) => get<any[]>(`/workflows/runs/${runId}/steps`);
export const getWorkflowRunTrace = async (runId: string) => get<Record<string, unknown>>(`/workflows/runs/${runId}/trace`);

export const listWorkflowSchedules = async (id: string) => get<WorkflowSchedule[]>(`/workflows/${id}/schedules`);
export const createWorkflowSchedule = async (id: string, payload: Record<string, unknown>) =>
  post<WorkflowSchedule>(`/workflows/${id}/schedules`, payload);
export const updateWorkflowSchedule = async (id: string, scheduleId: string, payload: Record<string, unknown>) =>
  put<WorkflowSchedule>(`/workflows/${id}/schedules/${scheduleId}`, payload);
export const deleteWorkflowSchedule = async (id: string, scheduleId: string) =>
  del<{ deleted: boolean }>(`/workflows/${id}/schedules/${scheduleId}`);

export const listAutomationActivities = async () => get<AutomationActivity[]>('/automation-studio/activities');
export const createAutomationActivity = async (payload: {
  name: string;
  activityType: AutomationActivityType;
  status?: AutomationActivityStatus;
  scheduleExpression?: string;
  inputConfig?: Record<string, unknown>;
  outputConfig?: Record<string, unknown>;
}) => post<AutomationActivity>('/automation-studio/activities', payload);
export const verifyAutomationActivity = async (id: string) =>
  post<{ valid: boolean; errors: string[]; warnings: string[]; normalizedConfig: Record<string, unknown> }>(`/automation-studio/activities/${id}/verify`, {});
export const runAutomationActivity = async (id: string, payload: { dryRun?: boolean; triggerSource?: string; overrides?: Record<string, unknown> }) =>
  post<AutomationActivityRun>(`/automation-studio/activities/${id}/runs`, payload);
export const listAutomationActivityRuns = async (id: string) =>
  get<AutomationActivityRun[]>(`/automation-studio/activities/${id}/runs`);
export const getWorkflowCapabilities = async (id: string) => get<Record<string, unknown>>(`/workflows/${id}/capabilities`);
export const getWorkflowAnalytics = async (id: string) => get<Record<string, unknown>>(`/workflows/${id}/analytics`);
