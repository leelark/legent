export type JourneyNodeType =
  | 'ENTRY_TRIGGER'
  | 'SEND_EMAIL'
  | 'DELAY'
  | 'CONDITION'
  | 'BRANCH'
  | 'SPLIT'
  | 'JOIN'
  | 'WEBHOOK'
  | 'UPDATE_FIELD'
  | 'ADD_TAG'
  | 'REMOVE_TAG'
  | 'SUPPRESS_CONTACT'
  | 'WAIT_UNTIL'
  | 'PAUSE'
  | 'EXIT_GOAL'
  | 'REENTRY_GATE'
  | 'EVENT_LISTENER'
  | 'END';

export interface JourneyNode {
  id: string;
  type: JourneyNodeType;
  label: string;
  config?: Record<string, unknown>;
  next?: string;
  branches?: { condition: string; target: string }[];
}

export const JOURNEY_NODE_TYPES: readonly JourneyNodeType[] = [
  'ENTRY_TRIGGER',
  'SEND_EMAIL',
  'DELAY',
  'CONDITION',
  'BRANCH',
  'SPLIT',
  'JOIN',
  'WEBHOOK',
  'UPDATE_FIELD',
  'ADD_TAG',
  'REMOVE_TAG',
  'SUPPRESS_CONTACT',
  'WAIT_UNTIL',
  'PAUSE',
  'EXIT_GOAL',
  'REENTRY_GATE',
  'EVENT_LISTENER',
  'END',
];

export const RUNTIME_SUPPORTED_JOURNEY_NODE_TYPES: readonly JourneyNodeType[] = [
  'ENTRY_TRIGGER',
  'SEND_EMAIL',
  'DELAY',
  'WAIT_UNTIL',
  'CONDITION',
  'END',
];

export const JOURNEY_NODE_LABELS: Record<JourneyNodeType, string> = {
  ENTRY_TRIGGER: 'Entry Trigger',
  SEND_EMAIL: 'Send Email',
  DELAY: 'Delay',
  CONDITION: 'Condition',
  BRANCH: 'Branch',
  SPLIT: 'Split',
  JOIN: 'Join',
  WEBHOOK: 'Webhook',
  UPDATE_FIELD: 'Update Field',
  ADD_TAG: 'Add Tag',
  REMOVE_TAG: 'Remove Tag',
  SUPPRESS_CONTACT: 'Suppress Contact',
  WAIT_UNTIL: 'Wait Until',
  PAUSE: 'Pause',
  EXIT_GOAL: 'Exit Goal',
  REENTRY_GATE: 'Re-entry Gate',
  EVENT_LISTENER: 'Event Listener',
  END: 'End',
};

const JOURNEY_NODE_TYPE_SET = new Set<string>(JOURNEY_NODE_TYPES);
const RUNTIME_SUPPORTED_JOURNEY_NODE_TYPE_SET = new Set<string>(RUNTIME_SUPPORTED_JOURNEY_NODE_TYPES);

export const isJourneyNodeType = (value: unknown): value is JourneyNodeType =>
  typeof value === 'string' && JOURNEY_NODE_TYPE_SET.has(value);

export const isRuntimeSupportedJourneyNodeType = (value: JourneyNodeType) =>
  RUNTIME_SUPPORTED_JOURNEY_NODE_TYPE_SET.has(value);

export const journeyNodeLabel = (type: JourneyNodeType) => JOURNEY_NODE_LABELS[type] ?? type;
