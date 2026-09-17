# Cumulocity FSM Sync Microservice

A scheduled, bidirectional sync bridge connecting **Cumulocity IoT Service Request Management Service** with **SAP Field Service Management (FSM)**. This microservice ensures seamless, idempotent synchronization between IoT-triggered service requests and external field service operations.

-------------------
## Architecture Overview

```text
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
-------------------
