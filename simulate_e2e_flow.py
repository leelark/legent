import sys
import time
import requests
import uuid
from typing import Dict, Any, Optional, Tuple

# Adjust these ports if your local Spring Boot applications bind differently
PORTS = {
    'foundation': 8081,
    'audience': 8082,
    'campaign': 8083,
    'delivery': 8084,
    'tracking': 8085,
    'automation': 8086,
    'deliverability': 8087,
    'platform': 8088,
    'identity': 8089
}

BASE_URLS = {svc: f"http://localhost:{port}/api/v1" for svc, port in PORTS.items()}
GATEWAY_BASE_URL = "http://localhost:8080/api/v1"


def bootstrap_authenticated_session() -> Optional[Tuple[requests.Session, Dict[str, str]]]:
    session = requests.Session()
    email = f"smoke.{uuid.uuid4().hex[:8]}@example.com"
    signup_payload = {
        "email": email,
        "password": "Passw0rd!123",
        "firstName": "Smoke",
        "lastName": "Tester",
        "companyName": f"SmokeCo-{uuid.uuid4().hex[:6]}"
    }

    print(f"[*] Signing up test user: {email}")
    res = session.post(f"{BASE_URLS['identity']}/auth/signup", json=signup_payload, timeout=10)
    if not res.ok:
        print(f"[FAIL] Signup failed: {res.text}")
        return None

    res = session.get(f"{BASE_URLS['identity']}/auth/session", timeout=10)
    if not res.ok:
        print(f"[FAIL] Session bootstrap failed: {res.text}")
        return None

    session_payload = res.json().get('data', {})
    tenant_id = session_payload.get('tenantId')
    workspace_id = session_payload.get('workspaceId')
    token = session.cookies.get("legent_token")
    if not tenant_id or not token:
        print("[FAIL] Missing tenant or auth token after signup/session")
        return None

    if not workspace_id:
        context_res = session.get(f"{BASE_URLS['identity']}/auth/contexts", timeout=10)
        if not context_res.ok:
            print(f"[FAIL] Failed to fetch auth contexts: {context_res.text}")
            return None
        contexts = context_res.json().get("data", [])
        default_context = next((ctx for ctx in contexts if ctx.get("default")), contexts[0] if contexts else None)
        if not default_context:
            print("[FAIL] No auth contexts available for workspace bootstrap")
            return None
        workspace_id = default_context.get("workspaceId")
        switch_payload = {
            "tenantId": default_context.get("tenantId") or tenant_id,
            "workspaceId": workspace_id,
            "environmentId": default_context.get("environmentId"),
        }
        switch_res = session.post(f"{BASE_URLS['identity']}/auth/context/switch", json=switch_payload, timeout=10)
        if not switch_res.ok:
            print(f"[FAIL] Context switch failed: {switch_res.text}")
            return None

    if not workspace_id:
        print("[FAIL] Missing workspace context after bootstrap")
        return None

    headers = {
        "X-Tenant-Id": tenant_id,
        "X-Workspace-Id": workspace_id,
        "X-Request-Id": f"smoke-{uuid.uuid4().hex}",
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    }
    print(f"[OK] Auth session ready. Tenant: {tenant_id}, Workspace: {workspace_id}")
    return session, headers

def check_services_health():
    print("--- 1. Checking Microservice Health ---")
    all_healthy = True
    for svc, port in PORTS.items():
        try:
            res = requests.get(f"http://localhost:{port}/actuator/health", timeout=2)
            if res.status_code == 200:
                print(f"[OK] {svc}-service is UP")
            elif svc == "identity" and res.status_code == 401:
                print(f"[OK] {svc}-service is UP (health endpoint secured: 401)")
            else:
                print(f"[WARN] {svc}-service returned {res.status_code}")
                all_healthy = False
        except requests.exceptions.RequestException:
            print(f"[ERROR] {svc}-service is UNREACHABLE on port {port}")
            all_healthy = False
            
    if not all_healthy:
        print("\n[!] Please ensure all backend microservices are running before proceeding with E2E Flow.\n")
        sys.exit(1)

def simulate_journey():
    print("\n--- 2. Starting End-to-End Campaign Journey ---")
    auth_bootstrap = bootstrap_authenticated_session()
    if not auth_bootstrap:
        return
    session, headers = auth_bootstrap
    
    # 2.A - Create Subscriber (Audience Service)
    subscriber_key = f"sub-{uuid.uuid4().hex[:8]}"
    sub_payload = {
        "subscriberKey": subscriber_key,
        "email": f"test.user.{uuid.uuid4().hex[:6]}@example.com",
        "firstName": "John",
        "lastName": "Doe",
        "source": "simulate_e2e_flow"
    }
    print(f"[*] Creating subscriber: {sub_payload['email']}")
    res = session.post(f"{BASE_URLS['audience']}/subscribers", json=sub_payload, headers=headers, timeout=10)
    if not res.ok:
        print(f"[FAIL] Creating subscriber failed: {res.text}")
        return
    sub_id = res.json().get('data', {}).get('id')
    print(f"[OK] Subscriber created. ID: {sub_id}")

    # 2.B - Create List + add subscriber membership
    list_payload = {
        "name": f"Smoke List {uuid.uuid4().hex[:6]}",
        "description": "Automated E2E list",
        "listType": "PUBLICATION"
    }
    print("[*] Creating list...")
    res = session.post(f"{BASE_URLS['audience']}/lists", json=list_payload, headers=headers, timeout=10)
    if not res.ok:
        print(f"[FAIL] Creating list failed: {res.text}")
        return
    list_id = res.json().get('data', {}).get('id')
    print(f"[OK] List created. ID: {list_id}")

    print(f"[*] Adding subscriber {sub_id} to list {list_id}...")
    res = session.post(
        f"{BASE_URLS['audience']}/lists/{list_id}/members",
        json={"subscriberIds": [sub_id]},
        headers=headers,
        timeout=10
    )
    if not res.ok:
        print(f"[FAIL] Adding list member failed: {res.text}")
        return
    print("[OK] List membership created")
    
    # Wait for Kafka replication across CQRS domains if required
    time.sleep(1)

    # 2.C - Create Campaign (Campaign Service)
    camp_payload = {
        "name": f"Welcome Series {uuid.uuid4().hex[:6]}",
        "type": "STANDARD",
        "subject": "Welcome to Legent",
        "preheader": "Let's get started",
        "audiences": [
            {
                "audienceType": "LIST",
                "audienceId": list_id,
                "action": "INCLUDE"
            }
        ]
    }
    print(f"[*] Creating campaign...")
    res = session.post(f"{BASE_URLS['campaign']}/campaigns", json=camp_payload, headers=headers, timeout=10)
    if not res.ok:
        print(f"[FAIL] Creating campaign failed: {res.text}")
        return
    camp_id = res.json().get('data', {}).get('id')
    print(f"[OK] Campaign created. ID: {camp_id}")

    # 2.D - Trigger Campaign Send
    print(f"[*] Triggering send for campaign {camp_id}...")
    res = session.post(f"{BASE_URLS['campaign']}/campaigns/{camp_id}/send", json={}, headers=headers, timeout=10)
    if not res.ok:
        print(f"[FAIL] Triggering campaign send failed: {res.text}")
        return
    send_job_id = res.json().get('data', {}).get('id')
    print(f"[OK] Campaign send triggered. Job ID: {send_job_id}")

    # 2.E - Poll job for non-zero audience resolution
    print("[*] Waiting for send job to resolve audience...")
    target_count = None
    for _ in range(15):
        time.sleep(1)
        res = session.get(f"{BASE_URLS['campaign']}/send-jobs/{send_job_id}", headers=headers, timeout=10)
        if not res.ok:
            continue
        data = res.json().get("data", {})
        target_count = data.get("totalTarget", 0)
        if target_count and target_count > 0:
            break

    if not target_count or target_count <= 0:
        print(f"[FAIL] Send job resolved with zero targets (job: {send_job_id})")
        return

    print(f"[OK] Send job resolved with target count: {target_count}")
    
    # 2.F - Automation graph contract + lifecycle smoke (workspace-strict, graph-v2)
    print("[*] Creating workflow and validating graph-v2 contract...")
    wf_payload = {
        "name": f"Smoke WF {uuid.uuid4().hex[:6]}",
        "description": "Smoke automation flow",
        "status": "DRAFT"
    }
    wf_res = session.post(f"{BASE_URLS['automation']}/workflows", json=wf_payload, headers=headers, timeout=10)
    if not wf_res.ok:
        print(f"[FAIL] Creating workflow failed: {wf_res.text}")
        return
    workflow_id = wf_res.json().get("data", {}).get("id")
    graph = {
        "graphVersion": 2,
        "initialNodeId": "node-1",
        "nodes": {
            "node-1": {"id": "node-1", "type": "ENTRY_TRIGGER", "configuration": {}, "nextNodeId": "node-2", "branches": []},
            "node-2": {"id": "node-2", "type": "DELAY", "configuration": {"delayMinutes": 0}, "nextNodeId": "node-3", "branches": []},
            "node-3": {"id": "node-3", "type": "END", "configuration": {}, "branches": []}
        },
        "edges": [],
        "entryPolicy": {"requireConsent": False, "checkSuppression": True, "frequencyCapPerDay": 10},
        "reentryPolicy": {"allowReentry": False, "cooldownMinutes": 1440}
    }

    for endpoint, payload in [
        (f"{BASE_URLS['automation']}/workflows/{workflow_id}/validate", graph),
        (f"{BASE_URLS['automation']}/workflows/{workflow_id}/definitions", graph),
        (f"{BASE_URLS['automation']}/workflows/{workflow_id}/publish", {}),
        (f"{BASE_URLS['automation']}/workflows/{workflow_id}/simulate", {"graph": graph, "context": {}}),
        (f"{BASE_URLS['automation']}/workflows/{workflow_id}/trigger", {"subscriberId": sub_id, "idempotencyKey": f"smoke-{uuid.uuid4().hex}"})
    ]:
        flow_res = session.post(endpoint, json=payload, headers=headers, timeout=10)
        if not flow_res.ok:
            print(f"[FAIL] Automation flow step failed ({endpoint}): {flow_res.text}")
            return

    # 2.G - Gateway contract smoke with strict context headers
    print("[*] Running gateway contract smoke checks...")
    gateway_checks = [
        ("GET", "/subscribers?page=0&size=5", None),
        ("GET", "/templates?page=0&size=5", None),
        ("GET", "/campaigns?page=0&size=5", None),
        ("GET", "/workflows", None),
        ("GET", "/delivery/queue/stats", None),
        ("GET", "/deliverability/domains", None),
    ]
    for method, path, payload in gateway_checks:
        url = f"{GATEWAY_BASE_URL}{path}"
        req = session.get if method == "GET" else session.post
        gw_res = req(url, headers=headers, json=payload, timeout=10)
        if not gw_res.ok:
            print(f"[FAIL] Gateway check failed [{method} {path}]: {gw_res.text}")
            return

    print("\n--- Journey successfully initiated on the backend! ---")
    print("Monitor the logs of `campaign-service`, `delivery-service`, and `tracking-service` to observe Kafka events firing:")
    print("1 -> campaign.send.requested")
    print("2 -> email.send.requested")
    print("3 -> email.sent")
    print("Alternatively, query the `message_logs` table in postgres to verify delivery attempt records.")

if __name__ == "__main__":
    check_services_health()
    simulate_journey()
