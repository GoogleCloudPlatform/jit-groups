# Resource conditions

JIT Access manages access by modifying the IAM policy of projects. When a user activates a role using
JIT Access, then, by default, that role applies to all resources in the project.

Resource conditions let you constrain the set of resources within a project that a role should apply to.
For example, you can use a resource condition to restrict a user's access to certain types of Compute Engine resources,
or to resources within a certain zone.

To use a resource condition, you add an extra clause to the IAM condition of an eligible role binding:

*   `has({}.jitAccessConstraint) && [resource-condition]`

*   `has({}.multiPartyApprovalConstraint) && [resource-condition]`

Where `[resource-condition]` is a valid [IAM condition :octicons-link-external-16:](https://cloud.google.com/iam/docs/conditions-overview).


!!! note

    You can change the order of clauses. For example `has({}.jitAccessConstraint) && [resource condition]`
    and `[resource condition] && has({}.jitAccessConstraint)` are equivalent.


## Examples

The following are examples for role bindings that use a resource condition to constrain access to certain
resources. 

*   Grant _Secret Accessor_ access for a specific Secret Manager secret, subject to self-approval:

    **Role**: `roles/secretmanager.secretAccessor`

    **Condition**:

        // Require self-approval
        has({}.jitAccessConstraint) &&

        // Secret
        resource.name == "projects/sample-project/secrets/sample-secret"

*   Grant _Compute Instance Admin_ access for Compute Engine VMs in `asia-southeast1-a`, subject to multi-party approval:

    **Role**: `roles/compute.instanceAdmin.v1`

    **Condition**:

        // Require multi-party approval
        has({}.multiPartyApprovalConstraint) &&

        // asia-southeast1-a only
        resource.name.startsWith("projects/sample-project/zones/asia-southeast1-a/instances/")


*   Grant _Compute Admin_ access for Compute Engine disks and images, subject to multi-party approval:

    **Role**: `roles/compute.admin`

    **Condition**:

        // Require multi-party approval
        has({}.multiPartyApprovalConstraint) &&

        // Disks and images
        (resource.type == "compute.googleapis.com/Disk" || resource.type == "compute.googleapis.com/Image")

!!! note

    Lines starting with `//` are comments and are ignored by JIT Access.