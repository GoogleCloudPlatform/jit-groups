The Just-in-Time Access application manages access to a set of eligible roles. This set of roles, and the logic
to determine these roles based on your organization's IAM policies is called a _catalog_.

The application currently supports two catalogs:

*   **PolicyAnalyzer**: This catalog uses the 
    [`analyzeIamPolicy` API :octicons-link-external-16:](https://cloud.google.com/asset-inventory/docs/reference/rest/v1/TopLevel/analyzeIamPolicy)
    to find eligible role bindings. 
    
    Using the `analyzeIamPolicy` requires a Security Command Center Premium subscription. If you're a 
    Security Command Center susbcriber, we recommend to use this catalog.
    
*   **AssetInventory**: Introduced in version 1.6, this catalog uses the 
    [`effectiveIamPolicies.batchGet` API](https://cloud.google.com/asset-inventory/docs/reference/rest/v1/effectiveIamPolicies/batchGet)
    to find eligible role bindings. This catalog doesn't require a Security Command Center subscription, but has the
    following limitations:
    
    *   The catalog ignores nested group memberships when evaluating IAM policies. 
    *   The project auto-completer lists all available projects instead of a personalized list of projects.
    
    Use this catalog if you don't have a Security Command Center subscription.
    

## PolicyAnalyzer catalog

If you've used JIT Access prior to version 1.6, you're already using the **PolicyAnalyzer** catalog.

To switch from the **AssetInventory** catalog to the **PolicyAnalyzer** catalog, do the following:

1.  [Redeploy the application](https://cloud.devsite.corp.google.com/architecture/manage-just-in-time-privileged-access-to-project#upgrade_just-in-time_access) and apply the following changes to your `app.yaml` configuration file:

        RESOURCE_CATALOG=PolicyAnalyzer

## AssetInventory catalog

If you haven't used JIT Access prior to version 1.6, you're already using the **AssetInventory** catalog.

To switch from the **PolicyAnalyzer** catalog to the **AssetInventory** catalog, do the following:

1.  Enable the [Directory API :octicons-link-external-16:](https://developers.google.com/admin-sdk/directory/v1/guides)

        gcloud services enable admin.googleapis.com
        
1.  Allow the application's service account to create tokens using its service account by granting it the 
    **Service Account Token Creator role** (`roles/iam.serviceAccountTokenCreator`):

        SERVICE_ACCOUNT=app-service-account
        gcloud iam service-accounts add-iam-policy-binding $SERVICE_ACCOUNT \
            --member "serviceAccount:$SERVICE_ACCOUNT" \
            --role "roles/iam.serviceAccountTokenCreator"
            
    Replace `app-service-account` with the email address of the 
    [application's service account :octicons-link-external-16:](https://cloud.google.com/architecture/manage-just-in-time-privileged-access-to-project#configure-your-google-cloud-project).

1.  Update the IAM policy of the project, folder, or organization that you manage using Just-in-Time Access:

    * Grant the application's service account the **Project IAM Admin role** (`roles/resourcemanager.projectIamAdmin`)
    * Remove the existing binding for the **Security Admin role** (`roles/iam.securityAdmin`) role

    For example:

        gcloud organizations add-iam-policy-binding organizations/org-id \
            --member "serviceAccount:$SERVICE_ACCOUNT" \
            --role "roles/resourcemanager.projectIamAdmin" \
            --condition None
        gcloud organizations remove-iam-policy-binding organizations/org-id \
            --member "serviceAccount:$SERVICE_ACCOUNT" \
            --role "roles/iam.securityAdmin" \
            --condition None

1.  [Redeploy the application](https://cloud.devsite.corp.google.com/architecture/manage-just-in-time-privileged-access-to-project#upgrade_just-in-time_access) and apply the following changes to your `app.yaml` configuration file:

        RESOURCE_CATALOG=AssetInventory
        RESOURCE_CUSTOMER_ID=customer-id

    Replace `customer-id` with the [customer ID of your Cloud Identity or Workspace account :octicons-link-external-16:](https://support.google.com/a/answer/10070793).

