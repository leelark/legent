export type LegentClientOptions = {
  apiBaseUrl: string;
  token?: string;
  tenantId?: string;
  workspaceId?: string;
  fetchImpl?: typeof fetch;
};

export type CopilotRecommendationRequest = {
  artifactType: string;
  artifactId?: string;
  objective: string;
  audienceSummary?: string;
  riskTolerance?: 'LOW' | 'MEDIUM' | 'HIGH';
  requireHumanApproval?: boolean;
  policyContext?: Record<string, unknown>;
  candidateContent?: Record<string, unknown>;
  constraints?: string[];
};

export type DecisionEvaluateRequest = {
  policyKey: string;
  eventType?: string;
  channel?: string;
  profileUpdates?: Record<string, unknown>;
  context?: Record<string, unknown>;
};

export type SloEvaluateRequest = {
  serviceName: string;
  successRatePercent?: number;
  p95LatencyMs?: number;
  saturationPercent?: number;
  queueDepth?: number;
  requests?: number;
  errors?: number;
};

export class LegentClient {
  private readonly apiBaseUrl: string;
  private readonly token?: string;
  private readonly tenantId?: string;
  private readonly workspaceId?: string;
  private readonly fetchImpl: typeof fetch;

  constructor(options: LegentClientOptions) {
    this.apiBaseUrl = options.apiBaseUrl.replace(/\/$/, '');
    this.token = options.token;
    this.tenantId = options.tenantId;
    this.workspaceId = options.workspaceId;
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  createCopilotRecommendation(payload: CopilotRecommendationRequest) {
    return this.post('/differentiation/copilot/recommendations', payload);
  }

  approveCopilotRecommendation(id: string, decisionNote?: string) {
    return this.post(`/differentiation/copilot/recommendations/${encodeURIComponent(id)}/decision`, {
      decision: 'APPROVED',
      decisionNote,
    });
  }

  rejectCopilotRecommendation(id: string, decisionNote?: string) {
    return this.post(`/differentiation/copilot/recommendations/${encodeURIComponent(id)}/decision`, {
      decision: 'REJECTED',
      decisionNote,
    });
  }

  evaluateDecisionPolicy(payload: DecisionEvaluateRequest) {
    return this.post('/differentiation/decisioning/evaluate', payload);
  }

  simulateOmnichannelFlow(payload: Record<string, unknown>) {
    return this.post('/differentiation/omnichannel/simulate', payload);
  }

  createDeveloperSandbox(payload: Record<string, unknown>) {
    return this.post('/differentiation/developer/sandboxes', payload);
  }

  replayWebhook(payload: Record<string, unknown>) {
    return this.post('/differentiation/developer/webhook-replays', payload);
  }

  evaluateSlo(payload: SloEvaluateRequest) {
    return this.post('/differentiation/ops/slo-policies/evaluate', payload);
  }

  private async post<T = unknown>(path: string, payload: unknown): Promise<T> {
    const response = await this.fetchImpl(`${this.apiBaseUrl}/api/v1${path}`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify(payload),
    });
    return this.read<T>(response);
  }

  private headers(): HeadersInit {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Request-Id': crypto.randomUUID(),
    };
    if (this.token) {
      headers.Authorization = `Bearer ${this.token}`;
    }
    if (this.tenantId) {
      headers['X-Tenant-Id'] = this.tenantId;
    }
    if (this.workspaceId) {
      headers['X-Workspace-Id'] = this.workspaceId;
    }
    return headers;
  }

  private async read<T>(response: Response): Promise<T> {
    const payload = await response.json().catch(() => undefined);
    if (!response.ok) {
      throw new LegentApiError(response.status, payload);
    }
    return (payload?.data ?? payload) as T;
  }
}

export class LegentApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly payload: unknown,
  ) {
    super(`Legent API request failed with status ${status}`);
  }
}
