# Kubernetes Production Overlay

Date: 2026-05-11

## Render

```powershell
kubectl kustomize infrastructure/kubernetes/overlays/production > legent-production.yml
```

## Apply

```powershell
kubectl apply -k infrastructure/kubernetes/overlays/production
kubectl -n legent rollout status deployment/identity-service --timeout=180s
```

## Contents

- External Secrets Operator `ExternalSecret` for `legent-secrets`.
- Default deny network policy with explicit backend and observability ingress.
- ResourceQuota and LimitRange budgets.
- PodDisruptionBudgets.
- Rolling update patch with rollback history.
- OpenTelemetry collector.
- Alertmanager routing config.

## Prerequisites

- `ClusterSecretStore/legent-production-secrets` exists and maps to the approved external secret manager.
- Prometheus or managed metrics platform scrapes `/actuator/prometheus` from inside the cluster.
- Ingress controller supports the actuator deny snippet or equivalent policy.
- Alertmanager webhook URLs are stored in the external secret manager.
