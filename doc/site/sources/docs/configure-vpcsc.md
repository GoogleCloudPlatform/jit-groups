The Just-in-Time Access application uses the [Google Cloud Resource Manager :octicons-link-external-16:](https://cloud.google.com/resource-manager/reference/rest) 
API to grant access to projects. If a project is part of a 
[VPC service perimeter :octicons-link-external-16:](https://cloud.google.com/vpc-service-controls/docs/service-perimeters) 
that restricts access to the _Google Cloud Resource Manager API_, then the application might be unable to grant users access to that project.

To allow Just-in-Time Access to grant users access to projects in a service perimeter, create an ingress policy:

1.  In the Cloud Console, go to [VPC Service Controls :octicons-link-external-16:](https://console.cloud.google.com/security/service-perimeter) 
    and open the service perimeter.
1.  Click **Edit perimeter**.
1.  Select **Ingress Policy**.
1.  Click **Add rule** and configure the following settings:

    +   **Source**: **All sources**
    +   **Identity**: the email address of the service account used by the JIT Access application
    +   **Project**: the project to manage access for, or **All projects**
    +   **Services**: **Google Cloud Resource Manager API**

1.  Click **Save**   

This ingress policy permits the service account used by the JIT Access application to access the Google Cloud Resource Manager API, 
and lets the Just-in-Time Access application grant users access to projects in that service perimeter.