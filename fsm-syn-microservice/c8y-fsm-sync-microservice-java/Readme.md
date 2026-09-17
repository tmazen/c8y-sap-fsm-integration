# Cumulocity FSM Sync Microservice

A scheduled, bidirectional sync bridge connecting **Cumulocity Service Request Management Service** with **SAP Field Service Management (FSM)**. This microservice ensures seamless, idempotent synchronization between IoT-triggered service requests and external field service operations.

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
| **FSM Service Client**  | Dedicated REST client managing OAuth authentication, token management, and data exchange with the **SAP FSM External API**. |
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
