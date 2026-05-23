import { describe, expect, it } from 'vitest';
import { sendGovernancePolicyItems, type SendGovernancePolicy } from '../../src/lib/send-governance-policy-api';

describe('sendGovernancePolicyItems', () => {
  it('normalizes paged policy responses from the content service', () => {
    const policies: SendGovernancePolicy[] = [
      {
        id: 'policy-1',
        policyKey: 'commercial-default',
        name: 'Commercial Default',
        active: true,
        version: 3,
      },
    ];

    expect(sendGovernancePolicyItems({ content: policies, totalElements: 1 })).toEqual(policies);
  });

  it('preserves direct array responses for compatibility fixtures', () => {
    const policies: SendGovernancePolicy[] = [{ id: 'policy-2', name: 'Transactional', active: false }];

    expect(sendGovernancePolicyItems(policies)).toEqual(policies);
  });
});
