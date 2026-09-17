# How to Use SAP FSM Business Rules to Call Cumulocity

This guide outlines how to enable or disable maintenance mode in Cumulocity for a device associated with an SAP FSM Service Call using FSM Business Rules[cite: 7].

---

## 1. Add Custom Field

1. From the Admin portal, select your company[cite: 7].
2. In the left navigation menu, select **Custom Objects** -> **Custom Field Definitions**[cite: 7].
3. Click **Create** to create a new Custom Field[cite: 7].
4. Configure the properties as follows[cite: 7]:
   * **Name:** `deviceMaintenanceMode`[cite: 7]
   * **Description:** `Maintenance Mode`[cite: 7]
   * **Object Type:** `ServiceCall`[cite: 7]
   * **Type:** `Selection List`[cite: 7]
   * **Classification Level:** `PUBLIC`[cite: 7]

![Create Custom Field Properties](image_1.png)[cite: 7]

5. Click **Save**[cite: 7].

---

## 2. Add Custom Field to Service Call Screen

1. From the Admin portal, select your company[cite: 7].
2. In the left menu, select **Screen Configurations**[cite: 7].
3. Select **ServiceCallAppServiceCallTab** (*Service Call App - Service Call Detail Tab*)[cite: 7].
4. Click the edit icon (![Edit Icon](image_2.png))[cite: 7].
5. Locate the list of custom fields on the right side panel[cite: 7].
6. Drag and drop the **Maintenance Mode** custom field into the `details_group`[cite: 7].
7. Click the three dots next to the custom field and select **Settings**[cite: 7].
8. In the **Basic settings** tab, verify that the custom field is configured to be editable and visible[cite: 7].
9. Open the **Custom field settings** tab and configure the selection key values as follows[cite: 7]:
   * `Off` : `Off`[cite: 7]
   * `On` : `On`[cite: 7]

![Custom Field Settings](image_3.png)[cite: 7]

10. **Activate** the screen configuration[cite: 7].

---

## 3. Microservice Endpoint Setup

Build and deploy the microservice to handle the incoming maintenance mode webhook updates if it is not already deployed[cite: 7]:

* **Target Endpoint:** `maintenanceMode/updateMaintenanceMode`[cite: 7]
* **Service Name:** `c8y-fsm-sync-ms-java`[cite: 7]

---

## 4. Add FSM Business Rule

1. From the Admin portal, select your company[cite: 7].
2. In the left menu, select **Business Rules**[cite: 7].
3. Click **Create**[cite: 7].
4. Enter the rule details[cite: 7]:
   * **Code:** `Service_Call_Update_Device_Maintenance_Mode`[cite: 7]
   * **Name:** `Service Call - Update Device Maintenance Mode`[cite: 7]
   * **Description:** `Service Call - Update Device Maintenance Mode`[cite: 7]
   * **Type:** `Two - JavaScript support inside expressions`[cite: 7]

![Edit Business Rule](image_4.png)[cite: 7]

5. Configure the **Trigger on** section[cite: 7]:
   * **Event:** `Object Operation (Type TWO)`[cite: 7]
   * **Operation:** `On Object Update`[cite: 7]
   * **Object:** `ServiceCall`[cite: 7]
   * **Execution:** `Synchronous - during synchronization with client application`[cite: 7]
   * **Permissions:** `Current User - nothing more than user permissions`[cite: 7]
   * **Conditions:** `old.udf.deviceMaintenanceMode != new.udf.deviceMaintenanceMode`[cite: 7]

![Trigger Configuration](image_5.png)[cite: 7]

6. Configure **Execute Action #1**[cite: 7]:
   * **Action:** `Webhook`[cite: 7]
   * **Execution Count:** `1`[cite: 7]
   * **Method:** `POST`[cite: 7]
   * **URL:** `https://<your-tenant>.cumulocity.com/service/c8y-fsm-sync-ms-java/maintenanceMode/updateMaintenanceMode`[cite: 7]
   * **Content Type:** `application/json`[cite: 7]
   * **Body:**[cite: 7]
     ```json
     {
       "serviceRequestId": "${new.externalId}",
       "maintenanceMode": "${new.udf.deviceMaintenanceMode}"
     }
     ```

![Execute Action Configuration](image_6.png)[cite: 7]

7. Click **Update & Validate**[cite: 7].

---

## 5. Testing the Integration

1. Click **Update & Execute**[cite: 7].
2. Pass any Service Call ID that possesses a valid Cumulocity Service Request mapped as its `externalId`[cite: 7].