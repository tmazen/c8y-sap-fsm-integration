# Cumulocity FSM Sync Microservice

A scheduled, bidirectional sync bridge connecting **Cumulocity Service Request Management Service** with **SAP Field Service Management (FSM)**. Built against **SAP FSM Service Call API version 27**, this microservice ensures seamless, idempotent synchronization between IoT-triggered service requests and external field service operations.


-------------------
## Architecture Overview

```
+------------------------------------------------------+
|                    Cumulocity FSM Sync Microservice  |
|                                                      |
|   +-----------------------+                          |
|   |       Scheduler       |                          |
|   |      (X minutes)      |                          |
|   +-----------+-----------+                          |
|               |                                      |
|               v                                      |
|   +-----------------------+                          |
|   |     Sync Service      |                          |
|   +---+---------------+---+                          |
|       |               |                              |
|       v               v                              |
|  +----+----+    +-----+-----+                        |
|  |   SR    |    |    FSM    |                        |
|  | Service |    |  Service  |                        |
|  | Client  |    |  Client   |                        |
|  +----+----+    +-----+-----+                        |
+-------|---------------|------------------------------+
        |               |
        v               v
+---------------+ +---------------+
| Service Req.  | |    SAP FSM    |
| Mgmt Service  | | External API  |
+---------------+ +---------------+
```
-------------------
## Key Features
- **Scheduled Sync (X-Minute Intervals):** Runs automated background batch execution every X minutes to check for state changes.
- **Bidirectional Synchronization:**
  - **Forward Sync:** Pushes new service requests (`new requests -> FSM`) created in Cumulocity over to SAP FSM.
  - **Reverse Sync:** Pulls status updates (`status updates <- FSM`) from SAP FSM back into Cumulocity to keep lifecycle states aligned.
- **Idempotent Operations:** Employs external ID tracking across systems to guarantee that retried operations or identical payloads do not generate duplicate tickets.
- **Resilient Error Handling:** Built-in retry mechanism with exponential backoff to handle transient network issues or SAP FSM/Cumulocity API rate limits smoothly.
- **Comprehensive Logging & Monitoring:** Structured logs detailing sync progress, payload transformations, retry counts, and execution metrics for operational visibility.
--------------
## Component Architecture

| **Component**           | **Functionality**                                                                                                           |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| **Scheduler**           | Internal timer component that triggers the synchronization routine every X minutes.                                         |
| **Sync Service**        | The central orchestrator handling data mapping, status state machines, idempotency validation, and retry execution.         |
| **SR Service Client** | Dedicated REST client interacting with the internal [**Service Request Mgmt Service**](https://github.com/Cumulocity-IoT/cumulocity-microservice-service-request-mgmt/) in Cumulocity. |
| **FSM Service Client**  | Dedicated REST client managing OAuth authentication, token management, and data exchange with the **SAP FSM External API (v27)**. |
---------
## Data Flow & Synchronization Lifecycle

1. **Trigger:** The **Scheduler** fires every X minutes, initiating a run in the **Sync Service**.

2. **Push New Requests:**
   - **Sync Service** calls **SR Service Client** to query un-synced or recently updated service requests from **Service Request Mgmt Service**.
   - Constructs the SAP FSM service call payload and calls **FSM Service Client** to create the ticket in **SAP FSM External API**.
   - Records the returned FSM External ID in Cumulocity for future idempotency tracking.

3. **Pull Status Updates:**
   - **Sync Service** queries **FSM Service Client** for state/status changes on active, linked FSM tickets.
   - If an FSM ticket state has changed, the update is dispatched via **SR Service Client** back to **Service Request Mgmt Service**.
-----------
## Prerequisites

- Access to the SAP FSM Platform.
- The **Service Request Mgmt Service Microservice** must be deployed on the same Cumulocity tenant.
--------
## Configuration

### Application YAML Setup

Set the sync interval in `application.yml`:

```yaml
sync:
  interval-minutes: 2
```

### Tenant Option Configuration

Follow these steps to configure the tenant option category for SAP FSM integration:

1. **Check Existing Categories:** Make sure there is no existing category in the DB named `sap.fsm`. Delete it if it exists.
2. **Deploy Microservice:** Deploy the microservice with `settingsCategory` set to `sap.fsm` in its `cumulocity.json` .
3. **Category Registration:** The platform will automatically register the new tenant option category `sap.fsm`.
4. **Create Tenant Options:** Using Cumulocity REST APIs, create the following tenant options under the `sap.fsm` category:

```json
{
  "api-url": "<SAP FSM API URL>", # ex: https://de.fsm.cloud.sap
  "header-account-id": "<header-account-id>",
  "header-company-id": "<header-company-id>",
  "header-client-id": "<header-client-id>",
  "header-client-version": "<header-client-version>",
  "client-id": "<Client ID>",
  "credentials.client-secret": "<credentials.client-secret>",
  "service-call-version": "27"
}
```
> **Note:** The `"service-call-version": "27"` property locks the API communication specifically to version 27 of the SAP FSM Service Call endpoint.

5. **Re-subscribe Microservice:** Unsubscribe and then re-subscribe the microservice to apply the tenant option configurations.

---
