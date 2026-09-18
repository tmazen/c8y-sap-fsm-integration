# Build and Deployment Guide for Python Microservice (`c8y-fsm-mob-ms-py`)

This document provides step-by-step instructions for building, packaging, and deploying the Python microservice (`c8y-fsm-mob-ms-py`) to Cumulocity IoT.

---

## Build & Packaging Steps

Follow these commands to build the Docker image, export the tarball, and package the microservice ZIP file:

### 2. Build Docker Image
Build the microservice container image tagged with version `1.0.0`:

```bash
docker build -t c8y-fsm-mob-ms-py:1.0.0 .
```

### 2. Export Image Tarball
Save the compiled Docker image into a tarball archive:

```bash
docker save c8y-fsm-mob-ms-py:1.0.0 > image.tar
```

### 3. Package Microservice for Cumulocity
Combine the exported `image.tar` and the `cumulocity.json` manifest file into the final deployable ZIP archive:

```bash
zip c8y-fsm-mob-ms-py.zip image.tar cumulocity.json
```

---

## Deployment to Cumulocity

1. Log into your **Cumulocity Tenant**.
2. Open the **Administration** application.
3. In the left navigation menu, go to **Ecosystem** -> **Microservices**.
4. Click **Add Microservice** in the top right corner.
5. Choose the generated `c8y-fsm-mob-ms-py.zip` file.
6. Verify that the microservice status changes to `Up` after deployment finishes.
