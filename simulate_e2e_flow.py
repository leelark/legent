import sys
import time
import requests
import uuid
from typing import Dict, Any

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
TENANT_ID = "test-tenant-123"
HEADERS = {
    'X-Tenant-Id': TENANT_ID,
    'Content-Type': 'application/json'
}

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
    res = requests.post(f"{BASE_URLS['audience']}/subscribers", json=sub_payload, headers=HEADERS)
    if not res.ok:
        print(f"[FAIL] Creating subscriber failed: {res.text}")
        return
    sub_id = res.json().get('data', {}).get('id')
    print(f"[OK] Subscriber created. ID: {sub_id}")
    
    # Wait for Kafka replication across CQRS domains if required
    time.sleep(1)

    # 2.B - Create Campaign (Campaign Service)
    camp_payload = {
        "name": "Welcome Series",
        "type": "STANDARD",
        "subject": "Welcome to Legent",
        "preheader": "Let's get started"
    }
    print(f"[*] Creating campaign...")
    res = requests.post(f"{BASE_URLS['campaign']}/campaigns", json=camp_payload, headers=HEADERS)
    if not res.ok:
        print(f"[FAIL] Creating campaign failed: {res.text}")
        return
    camp_id = res.json().get('data', {}).get('id')
    print(f"[OK] Campaign created. ID: {camp_id}")

    # 2.C - Trigger Campaign Send
    print(f"[*] Triggering send for campaign {camp_id}...")
    res = requests.post(f"{BASE_URLS['campaign']}/campaigns/{camp_id}/send", headers=HEADERS)
    if not res.ok:
        print(f"[FAIL] Triggering campaign send failed: {res.text}")
        return
    send_job_id = res.json().get('data', {}).get('id')
    print(f"[OK] Campaign send triggered. Job ID: {send_job_id}")
    
    print("\n--- Journey successfully initiated on the backend! ---")
    print("Monitor the logs of `campaign-service`, `delivery-service`, and `tracking-service` to observe Kafka events firing:")
    print("1 -> campaign.send.requested")
    print("2 -> email.send.requested")
    print("3 -> email.sent")
    print("Alternatively, query the `message_logs` table in postgres to verify delivery attempt records.")

if __name__ == "__main__":
    check_services_health()
    simulate_journey()
