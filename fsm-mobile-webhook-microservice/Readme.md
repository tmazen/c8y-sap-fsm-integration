# How to Open Cumulocity Dashboard in SAP FSM Mobile App

This guide details how to extend the SAP FSM Mobile App using Web Containers to render an embedded Cumulocity Dashboard.

---

## Overview & Technical Workaround

The SAP FSM Mobile App can be extended to view information from external platforms by configuring **Web Containers** in the SAP FSM Admin Application. However, standard integration faces the following technical limitations:

* **HTTP Method Constraint:** SAP FSM Web Containers can only send `POST` requests to the configured target URL.
* **Header Restriction:** Web Containers cannot append custom HTTP Headers (such as an `Authorization` header) to outbound requests.
* **Cumulocity Requirement:** Cumulocity endpoints reject any incoming `POST` requests that do not present a valid `Authorization` header.

### Solution Architecture

To overcome these constraints, the integration utilizes two core components:

1. **[Proxy Script (`fsm-bridge.py`)](./aws-script):** Hosted on an external server (e.g., AWS EC2). It accepts unauthenticated `POST` requests from the SAP FSM Mobile App, injects the necessary tenant authorization headers, and forwards the requests to the custom microservice.
2. **[Python Microservice (`c8y-fsm-mob-ms-py`)](./c8y-fsm-mob-ms-py/app):** A custom microservice deployed in the Cumulocity tenant that receives `POST` requests from the bridge script and redirects the request to the target Cumulocity Dashboard stored in Tenant Options.

---

## Step-by-Step Setup Guide

### 1. Create Dashboard & Tenant Option in Cumulocity

1. Create a mobile-friendly dashboard in Cumulocity (recommended layout: one widget per row).
2. Configure the following Tenant Option in Cumulocity:

```json
{
  "category": "sap.fsm.mobile",
  "key": "c8y-dashboard-url",
  "value": "<Dashboard URL>"
}
```

---

## 2. Deploy Microservice & Prepare Service Credentials

1. Deploy the [`c8y-fsm-mob-ms-py`](./c8y-fsm-mob-ms-py/app) Python microservice to your Cumulocity tenant.
2. Create a dedicated user in the Cumulocity tenant with **least security privileges** (only the roles required to access the target dashboard).
3. Generate the Base64 Authorization Header for this user (e.g., `Basic <base64-credentials>`).
4. Update [`fsm-bridge.py`](./aws-script/fsm-bridge.py) by setting the `encoded_auth` variable value to the generated `Authorization` header string.

---

## 3. Host Proxy Bridge on AWS EC2

1. **Launch EC2 Instance:** Create a new AWS EC2 virtual machine.
2. **Create Working Directory:**
   ```bash
   mkdir fsm-mob-bridge && cd fsm-mob-bridge
   ```
3. **Install Dependencies:** Install Python 3, Pip 3, and [ngrok](https://ngrok.com/) (create a free account at ngrok.com if needed).
4. **Configure ngrok:**
   ```bash
   ngrok config add-authtoken <Auth Token>
   ```
5. **Upload Bridge Scripts:** Upload the following files to `fsm-mob-bridge`:
   * [`fsm-bridge.py`](./aws-script/fsm-bridge.py) (Proxy script)
   * [`start_bridge.sh`](./aws-script/start_bridge.sh) (Startup runner script)
6. **Execution:** Run `start_bridge.sh`. This script:
   * Starts `fsm-bridge.py` and ngrok in the background.
   * Outputs logs to `bridge.log` in the current directory.
   * Prints the generated public ngrok URL to be used inside SAP FSM.
7. **Security Group Configuration:** Verify that the AWS EC2 Security Group permits inbound traffic on port `443` (and HTTP/HTTPS as required by your ngrok setup).

---

## 4. Configure Web Container in SAP FSM

1. Log into the SAP FSM Admin Application (ex : https://de.fsm.cloud.sap/admin).
2. Select your target company.
3. Navigate to **Web Container** (located at the bottom of the left navigation pane).
4. Click **Create** and configure the container settings:
   * **Container Name:** Enter a unique identifier.
   * **Title:** Enter the display title (e.g., `Device Overview`) as it should appear in the mobile app.
   * **Target URL:** Enter the public HTTPS URL printed by `start_bridge.sh`.
   * **Object Types:** Select `Service Call`.

---

## 5. Verify in SAP FSM Mobile App

1. Open the SAP FSM Mobile App on your device.
2. Perform a manual **Sync App** operation from the top header menu.
3. Open **Service Calls** from the left pane and select any active Service Call.
4. Tap the **three dots menu (...)** in the upper right corner.
5. Select **Device Overview** to launch the embedded Cumulocity dashboard within the Web Container.
