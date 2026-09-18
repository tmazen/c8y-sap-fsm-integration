# Cumulocity & SAP FSM Integration

This repository contains a comprehensive suite of microservices, extensions, and automated rules designed to connect **Cumulocity** with **SAP Field Service Management (FSM)**. The integration enables closed-loop field operations, predictive maintenance dispatching, real-time device control, and embedded IoT visualizations.

---

## Repository Structure

```text
.
├── fsm-busines-rules/
├── fsm-extension/
├── fsm-mobile-webhook-microservice/
└── fsm-syn-microservice/c8y-fsm-sync-microservice-java/
```

---

## Project Summaries

| Project Component | Technology Stack | Description |
| --- | --- | --- |
| **`fsm-syn-microservice/c8y-fsm-sync-microservice-java`** | Java 17, Spring Boot, Cumulocity Microservice SDK | Core scheduled bridge service. Syncs Cumulocity Service Requests with SAP FSM Service Calls (API v27) bi-directionally every 2 minutes. |
| **`fsm-busines-rules`** | SAP FSM Business Rules (JavaScript / Webhook) | Triggers real-time actions from SAP FSM to Cumulocity. Enables/disables device **Maintenance Mode** in Cumulocity upon Service Call updates. |
| **`fsm-extension`** | HTML5, AWS S3, iFrame Embedding | Embeds live Cumulocity Cockpit dashboards directly into the SAP FSM Web Application (Service Call Modal Outlet Tab). |
| **`fsm-mobile-webhook-microservice`** | Python 3, Docker, EC2 Proxy Bridge | Extends the SAP FSM Mobile App via Web Containers to display Cumulocity dashboards on mobile devices. |

---

## Component Deep Dive

### 1. `fsm-syn-microservice/c8y-fsm-sync-microservice-java`
* **Purpose:** Serves as the primary synchronization engine between Cumulocity Service Request Management and SAP FSM.
* **Key Features:**
  * **Scheduled Sync:** Polls for updates at configurable intervals (default: 2 minutes).
  * **Bidirectional Lifecycle:** Pushes new Cumulocity service requests to SAP FSM and pulls ticket status changes back into Cumulocity.
  * **Idempotency & Resilience:** Tracks external IDs to prevent duplicate service calls and executes retries on transient network errors.

---

### 2. `fsm-busines-rules`
* **Purpose:** Configures custom fields and webhook business rules within SAP FSM to control Cumulocity devices dynamically.
* **Key Features:**
  * **Custom Field Definition:** Adds `deviceMaintenanceMode` to the SAP FSM Service Call object.
  * **Automated Webhook:** Listens for changes to the maintenance field and dispatches a payload to the Java microservice endpoint `maintenanceMode/updateMaintenanceMode` to toggle maintenance state in Cumulocity.

---

### 3. `fsm-extension`
* **Purpose:** Enables field technicians using the SAP FSM Web Portal to inspect real-time device telemetry.
* **Key Features:**
  * **Cloned Cockpit Entrypoint:** Uses a custom `iframe.html` entrypoint to strip top headers and side navigators (`hideHeader=true&hideNavigator=true`).
  * **AWS S3 Static Hosting:** Hosts the HTTPS HTML wrapper on Amazon S3 for compliance with SAP FSM extension security policies.
  * **Target Outlet:** Assigned to the `Service Call Modal Outlet Tab` in SAP FSM.

---

### 4. `fsm-mobile-webhook-microservice`
* **Purpose:** Bridges SAP FSM Mobile Web Containers to Cumulocity Cockpit Dashboards.
* **Key Features:**
  * **Authentication & Protocol Proxy:** Overcomes SAP FSM Mobile Web Container limitations (`POST`-only without custom headers) by introducing an external EC2 Python bridge (`fsm-bridge.py`) that injects Cumulocity tenant credentials.
  * **Dashboard Redirection:** Reads the target mobile dashboard URL from the `sap.fsm.mobile` tenant option category.

---

## Prerequisites Across All Components

1. **Cumulocity IoT Tenant:**
   * Administrative privileges to deploy microservices and configure tenant options.
   * The core **Service Request Mgmt Service** microservice must be pre-deployed on the tenant.
   * **Basic Authentication** enabled for iFrame dashboard embedding.
2. **SAP FSM Account:**
   * Access to the SAP FSM Admin Portal and Shell (`https://de.fsm.cloud.sap`).
   * OAuth credentials (Client ID, Client Secret, Account, and Company).
3. **Infrastructure & Hosting:**
   * AWS Account (EC2 for mobile bridge, S3 for web extension hosting).
   * Docker & Java 17+ / Python 3 build environments.

---

## Getting Started

Refer to the individual `README.md` files located inside each project folder for component-specific build, packaging, configuration, and execution instructions.
