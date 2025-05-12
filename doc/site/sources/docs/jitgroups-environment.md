JIT Groups lets you configure one or more [environments](jitgroups-concepts.md#environment).
Environments correspond to segments of your Google Cloud organizational hierarchy, 
and you can use environments to delegate the management of
these resources to different teams or business units.

For each environment, JIT Access maintains:

+   A [policy document](policy-reference.md) that defines the groups for this environment. 
+   A Secret Manger secret that contains the policy document. 
+   A service account that's used to provision IAM bindings for resources in this environment. 

If you're planning to use a single environment, it's best to create the secret and service account in the
project that contains the JIT Groups application. If you're planning to use multiple environments, and delegate the
administration of these environments to different teams, then it's best to create a dedicated project for each
environment and create the secret and service account there.


## Register the environment

The steps to register an environment differ depending on whether you're using the project that contains the 
JIT Groups application or a separate project:

=== "Same project"

1.  Copy the example policy document to a file `environment.yaml`:

    ```sh
    cp target/sources/src/main/resources/oobe/policy.yaml environment.yaml
    ```

   1.  Open your [existing Terraform configuration](jitgroups-deploy.md) and add the following:

       ```hcl  hl_lines="4 9-22"
       module "application" {
           ...
           environments                = [ # List of environments, identified by service account
               "serviceAccount:${module.environment.service_account}"
           ]
           ...
       }

       module "environment" {
           source                      = "./target/terraform/jitgroups-environment"
           project_id                  = local.project_id
           application_service_account = module.application.service_account
    
           name                        = "NAME"
           policy                      = file("environment.yaml")
           
           # secret_location           = "SECRET_LOCATION"
       }

       output "environment"  {
           value                       = module.environment.service_account
       }

       output "url" {
           value                       = module.application.url
       }

       output "service_account" {
           value                       = module.application.service_account
       }
       ```


    
    Replace values of the following variables:
    
    +   `name`: the name of the environment.
    +   `secret_location` (optional): the region to [replicate Secret Manager secrets to :octicons-link-external-16:](https://cloud.google.com/secret-manager/docs/choosing-replication).
        By default, the secrets used for storing policy documents are replicated automatically.
    

    The application uses the environment name as unique identifier and 
    incorporates it into the name of Cloud Identity groups. Names must therefore comply with the following
    restrictions:
 
    +   Names are case-insensitive. You can't have two environments whose names only differ in casing.
    +   Names must be no longer than 16 characters.
    +   Names must only use the following characters: `A-Z`, `a-z`, `0-9`, `-`.

    The name can't be changed later.

    !!!note
        To add multiple environments, duplicate the highlighted section and assign unique
        names to the modules and outputs.

1.  Authorize `terraform`:

    ```sh
    gcloud auth application-default login
    ```
    
1.  Reinitialize Terraform and apply the configuration change:

    ```sh
    terraform init 
    terraform apply 
    ```

1.  Open a browser and navigate to the URL that you obtained after running `terraform apply`.
1.  Open the environment selector and select your environment.

### Grant access to resources

As a result of applying the Terraform configuration change, your project now contains an additional
service account, `jit.NAME@PROJECT.iam.gserviceaccount.com` where `NAME` is the name of the environment and
`PROJECT` is the project ID.

You must grant this service account permission to modify IAM policies of the resources that the environment
corresponds to. Because this step might require privileged access to these resources, 
it's not performed automatically by Terraform.

For example, if the environment corresponds to the `development` folder of your Google Cloud
organization, then you must grant the service account permission to modify the IAM policy of that folder.

=== "Project"

    If the environment corresponds to a single project, do the following:
    
    ```sh
    RESOURCE_ID=PROJECT_ID
    ENVIRONMENT_SERVICE_ACCOUNT=$(terraform output -raw environment)
    
    gcloud projects add-iam-policy-binding $RESOURCE_ID \
      --member "serviceAccount:$ENVIRONMENT_SERVICE_ACCOUNT" \
      --role "roles/resourcemanager.projectIamAdmin" \
      --format "value(etag)" \
      --condition None
    ```
    
    Replace  `PROJECT_ID` with the project ID that that you want to grant access to.

=== "Folder"

    If the environment corresponds to a folder, do the following:
    
    ```sh
    RESOURCE_ID=FOLDER_ID
    ENVIRONMENT_SERVICE_ACCOUNT=$(terraform output -raw environment)
    
    gcloud resource-manager folders add-iam-policy-binding $RESOURCE_ID \
      --member "serviceAccount:$ENVIRONMENT_SERVICE_ACCOUNT" \
      --role "roles/resourcemanager.projectIamAdmin" \
      --format "value(etag)" \
      --condition None
    ```
    
    Replace `FOLDER_ID` with the folder ID that that you want to grant access to.

=== "Organization"

    If the environment corresponds to the entire organization, do the following:
    
    ```sh
    RESOURCE_ID=ORG_ID
    ENVIRONMENT_SERVICE_ACCOUNT=$(terraform output -raw environment)
    
    gcloud resource-manager folders add-iam-policy-binding $RESOURCE_ID \
      --member "serviceAccount:$ENVIRONMENT_SERVICE_ACCOUNT" \
      --role "roles/resourcemanager.projectIamAdmin" \
      --format "value(etag)" \
      --condition None
    ```
    
    Replace `ORG_ID` with the organization ID that that you want to grant access to.

### Grant access to the VPC Service Controls perimeter

If you're using the environment to manage access to projects that are part of a 
VPC Service Controls perimeter, then you must create an ingress rule that permits
JIT Groups to access the perimeter:

1.  In the Cloud Console, go to [VPC Service Controls :octicons-link-external-16:](https://console.cloud.google.com/security/service-perimeter)
    and open the service perimeter.
1.  Click **Edit perimeter**.
1.  Select **Ingress Policy**.
1.  Click **Add rule** and configure the following settings:

    +   **Source**: **All sources**
    +   **Identity**: the email address of the environment's service account (`jit.NAME@PROJECT.iam.gserviceaccount.com`)
    +   **Project**: the project to manage access for, or **All projects**
    +   **Services**: **Google Cloud Resource Manager API**

1.  Click **Save**

## Customize the policy

To customize the policy document of your environment, do the following:

1.  In the web interface, click **View policy**.
1.  Use the YAML editor to modify the policy. For details about the syntax, see [policy documents](policy-reference.md).
1.  Click **Validate** to let JIT Groups check the YAML syntax, CEL expressions, and other configuration details.

!!!note

    JIT Groups doesn't have permission to modify the Secret Manager secret and therefore
    doesn't let you save or apply the policy changes in the web interface.

To save and apply your policy changes, do the following:

1.  Copy the updated YAML code to the `environment.yaml` file.

1.  Run Terraform to apply the configuration:

    ```sh
    terraform apply 
    ```

## What's next

[Set up continuous deployment :material-arrow-right:](jitgroups-continuous-deployment.md){ .md-button }
[Customize your policy document :material-arrow-right:](policy-reference.md){ .md-button }


