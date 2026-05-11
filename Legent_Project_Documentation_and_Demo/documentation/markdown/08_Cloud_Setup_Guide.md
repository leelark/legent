# Cloud Setup Guide

This project already includes Docker and Kubernetes assets. This guide is Kubernetes-first and includes cloud command appendices for EKS, GKE, and AKS.

## 1. Build Images

Set values:

```powershell
$env:REGISTRY="registry.example.com/legent"
$env:IMAGE_TAG="1.0.2"
```

Build backend service images:

```powershell
docker build -t $env:REGISTRY/foundation-service:$env:IMAGE_TAG services/foundation-service
docker build -t $env:REGISTRY/identity-service:$env:IMAGE_TAG services/identity-service
docker build -t $env:REGISTRY/audience-service:$env:IMAGE_TAG services/audience-service
docker build -t $env:REGISTRY/content-service:$env:IMAGE_TAG services/content-service
docker build -t $env:REGISTRY/campaign-service:$env:IMAGE_TAG services/campaign-service
docker build -t $env:REGISTRY/delivery-service:$env:IMAGE_TAG services/delivery-service
docker build -t $env:REGISTRY/tracking-service:$env:IMAGE_TAG services/tracking-service
docker build -t $env:REGISTRY/automation-service:$env:IMAGE_TAG services/automation-service
docker build -t $env:REGISTRY/deliverability-service:$env:IMAGE_TAG services/deliverability-service
docker build -t $env:REGISTRY/platform-service:$env:IMAGE_TAG services/platform-service
docker build -t $env:REGISTRY/frontend:$env:IMAGE_TAG frontend
```

Push images:

```powershell
docker push $env:REGISTRY/foundation-service:$env:IMAGE_TAG
docker push $env:REGISTRY/identity-service:$env:IMAGE_TAG
docker push $env:REGISTRY/audience-service:$env:IMAGE_TAG
docker push $env:REGISTRY/content-service:$env:IMAGE_TAG
docker push $env:REGISTRY/campaign-service:$env:IMAGE_TAG
docker push $env:REGISTRY/delivery-service:$env:IMAGE_TAG
docker push $env:REGISTRY/tracking-service:$env:IMAGE_TAG
docker push $env:REGISTRY/automation-service:$env:IMAGE_TAG
docker push $env:REGISTRY/deliverability-service:$env:IMAGE_TAG
docker push $env:REGISTRY/platform-service:$env:IMAGE_TAG
docker push $env:REGISTRY/frontend:$env:IMAGE_TAG
```

## 2. Provision Managed Dependencies

Recommended production services:

- Managed PostgreSQL with one database per service.
- Managed Redis.
- Managed Kafka or compatible streaming service.
- OpenSearch.
- S3-compatible object storage replacing MinIO.
- ClickHouse or managed analytics store.
- SMTP provider or SES/SendGrid/Mailgun credentials.

## 3. Kubernetes Secrets And Config

```powershell
kubectl create namespace legent
kubectl -n legent create secret generic legent-secrets `
  --from-literal=DB_USER=legent `
  --from-literal=DB_PASSWORD="replace-me" `
  --from-literal=LEGENT_SECURITY_JWT_SECRET="replace-me" `
  --from-literal=LEGENT_TRACKING_SIGNING_KEY="replace-me"
```

Apply base manifests:

```powershell
kubectl apply -f infrastructure/kubernetes/base/namespace.yml
kubectl apply -f infrastructure/kubernetes/base/secrets.yml
kubectl apply -f infrastructure/kubernetes/base/configmap.yml
kubectl apply -f infrastructure/kubernetes/base/services/
kubectl apply -f infrastructure/kubernetes/base/deployments/
kubectl apply -f infrastructure/kubernetes/base/hpa.yml
kubectl apply -f infrastructure/kubernetes/base/network-policy.yml
kubectl apply -f infrastructure/kubernetes/ingress/ingress.yml
kubectl apply -f infrastructure/kubernetes/observability/prometheus-alerts.yml
```

## 4. Health Checks

```powershell
kubectl -n legent get pods
kubectl -n legent get svc
kubectl -n legent logs deploy/foundation-service
kubectl -n legent port-forward svc/frontend 3000:3000
```

## 5. EKS Quick Appendix

```powershell
eksctl create cluster --name legent --region ap-south-1 --nodes 3
aws eks update-kubeconfig --name legent --region ap-south-1
```

## 6. GKE Quick Appendix

```powershell
gcloud container clusters create legent --zone asia-south1-a --num-nodes 3
gcloud container clusters get-credentials legent --zone asia-south1-a
```

## 7. AKS Quick Appendix

```powershell
az group create --name rg-legent --location centralindia
az aks create --resource-group rg-legent --name legent --node-count 3 --generate-ssh-keys
az aks get-credentials --resource-group rg-legent --name legent
```

## 8. Rollback

```powershell
kubectl -n legent rollout history deploy/campaign-service
kubectl -n legent rollout undo deploy/campaign-service
kubectl -n legent rollout status deploy/campaign-service
```
