# Steps to Open Cumulocity in iFrame & Use as an SAP FSM Extension

This guide details how to embed a Cumulocity Cockpit dashboard into an iFrame and host it as an extension inside SAP Field Service Management (FSM).

---

## Prerequisites & Known Constraints

> [!WARNING]
> **Authentication Requirement:** Ensure **Basic Authentication** is configured for the Cumulocity tenant. Due to current iFrame restrictions, using OAuth will fail to load Cumulocity within an iFrame.
> * Related Issue Ticket: [CST-2827](https://cumulocity.atlassian.net/browse/CST-2827)
> * Related Product Idea: [IM-5582](https://cumulocity.atlassian.net/browse/IM-5582)

---

## 1. Configure Cloned Cockpit Application

1. **Clone Cockpit Application:** Clone the standard Cockpit app to create a custom application named `Cockpit-1`.
2. **Extract Archive:** Download the zipped application archive and extract its contents.
3. **Create iFrame Entrypoint:** Inside the extracted directory, duplicate `index.html` and rename the copy to `iframe.html`.
4. **Package & Deploy:** Re-zip the application files and upload/deploy the archive to the `Cockpit-1` application slot in Cumulocity.

---

## 2. Modify & Build Custom Login Application

1. **Download Source:** Clone or download the Login Application repository from GitHub: [Cumulocity-IoT/login](https://github.com/Cumulocity-IoT/login).
2. **Duplicate Entrypoint:** Inside the `src/` directory, duplicate `index.html` and name the copy `iframe.html`.
3. **Build Application:** Run the build process via CLI (requires Node.js & Angular CLI Check this link for [installtion steps](https://cumulocity.com/codex/quick-start/installation-setup/overview):
   ```bash
   ng build
   ```
4. **Deploy Application:** Upload and deploy the generated build artifact to Cumulocity using the UI.

---

## 3. Set Default Branding & Redirects

1. Set the newly deployed **Cockpit-1** application as the default application for your environment.
2. In the **Administration** app, navigate to **Branding** and add the following root property to default branding:
   ```json
   "loginRedirectPath": "/apps/public/login/iframe.html"
   ```
3. Update target dashboard URLs to point through the `Cockpit-1` iFrame endpoint. Append `hideHeader=true` and `hideNavigator=true` query parameters to hide the navigation bars:
   ```text
   https://<tenant-domain>/apps/cockpit-1/iframe.html?<mark>**hideHeader=true&hideNavigator=true**</mark>#/device/<device-id>/dashboard/<dashboard-id>
   ```

---

## 4. Host iFrame Wrapper on AWS S3

### Create the HTML Wrapper (`index.html`)

Create a local HTML file to frame the embedded dashboard:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cumulocity Cockpit Integration</title>
    <style>
        body, html {
            margin: 0;
            padding: 0;
            height: 100%;
            overflow: hidden; /* Prevents double scrollbars */
        }
        iframe {
            width: 100%;
            height: 100%;
            border: none;
        }
    </style>
</head>
<body>
    <iframe 
        src="https://<tenant-domain>/apps/cockpit-1/iframe.html?hideHeader=true&hideNavigator=true#/device/<device-id>/dashboard/<dashboard-id>" 
        title="Cumulocity Cockpit"
        id="c8y-iframe"
        allow="fullscreen">
    </iframe>
</body>
</html>
```

### AWS S3 Hosting Setup

1. **Create S3 Bucket:**
   * Open AWS Console -> **S3** -> Click **Create bucket**.
   * Set a globally unique bucket name (e.g., `fsm-extension-ui-prod`) and select your region.
   * Uncheck **Block all public access** and acknowledge the warning.
2. **Upload Wrapper:** Upload the `index.html` file into the root of the bucket.
3. **Enable Static Website Hosting:**
   * Go to the **Properties** tab of the bucket.
   * Enable **Static website hosting** and select **Host a static website**.
   * Set the Index document to `index.html`.
4. **Configure Public Read Bucket Policy:**
   Add the following policy under the **Permissions** tab (replace `fsm-extension-ui-prod` with your bucket name):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "PublicReadOnlyForIndex",
         "Effect": "Allow",
         "Principal": "*",
         "Action": "s3:GetObject",
         "Resource": "arn:aws:s3:::fsm-extension-ui-prod/index.html"
       }
     ]
   }
   ```
5. **Security Constraints:** Keep `s3:ListBucket` permissions disabled, maintain public ACLs as disabled, and ensure only object-level read permissions exist.
6. **Retrieve Direct HTTPS URL:** SAP FSM requires strict HTTPS endpoints. Use the direct S3 Object HTTPS URL instead of the HTTP website endpoint:
   ```text
   https://<bucket-name>.s3.<region>.amazonaws.com/index.html
   ```

---

## 5. Configure SAP FSM Extension

1. Log into the SAP FSM Shell interface (`https://de.fsm.cloud.sap/shell/#/`).
2. Click **Foundational Services**.
3. Select **Installed Extensions** from the left pane.
4. Click **Add Extension** and paste your direct AWS S3 Object HTTPS URL into the **Access URL** field.
5. Under **Extension Assignments**, assign the extension to the **Service Call Modal Outlet Tab**.
