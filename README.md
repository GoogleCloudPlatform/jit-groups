# Just-In-Time Access

Just-In-Time Access is an open source application that lets you implement just-in-time privileged access to Google Cloud resources. 

[<img src="doc/documentation.png">](https://googlecloudplatform.github.io/jit-groups/jitaccess-overview/)

Just-In-Time Access works by introducing the notion of _eligible role bindings_ to Cloud IAM. Unlike a [regular
IAM role binding](https://cloud.google.com/iam/docs/overview#cloud-iam-policy), 
an eligible role binding doesn't grant the user access to a project yet:
Instead, a user first has to _activate_ the binding on demand by using the Just-In-Time Access application. As an administrator,
you can decide whether activating a role requires approval, or whether users only need to provide a justification (like a bug or case number).

You can use _eligible role bindings_ to grant users privileged (or break-glass) access to resources
without having to grant them permanent access. This type of just-in-time privileged access helps you to:

* Reduce the risk of someone accidentally modifying or deleting resources. For example, when users have privileged access only when it's needed, it helps prevent them from running scripts at other times that unintentionally affect resources that they shouldn't be able to change.
* Create an audit trail that indicates why privileges were activated.
* Conduct audits and reviews for analyzing past activity.

> [!NOTE]  
> To manage privileged access to Google Cloud resources, you can also use [Privileged Access Manager](https://cloud.google.com/iam/docs/pam-overview), which is now in preview. To learn more about how JIT Access and Privileged Access Manager compare, see [JIT Access vs Privileged Access Manager, and what's next for JIT Access](https://github.com/GoogleCloudPlatform/jit-access/discussions/451).

## Activate roles on demand

<a href='https://googlecloudplatform.github.io/jit-groups/images/JIT-Activation-Screencast.gif?raw=true'>
<img src='https://googlecloudplatform.github.io/jit-groups/images/JIT-Activation_350.png' align='right'>
</a>

As a user, you can activate a role in three steps:

1. Select the project you need to access
2. Select one or more roles to activate (from your list of eligible roles)
3. Enter a justification (like a bug or case number)

After validating your request, the application then [grants you temporary access](https://cloud.google.com/iam/docs/configuring-temporary-access)
to the project.



<img src='doc/pix.gif' width='100%' height='1'>


## Request approval to activate a role

<a href='https://googlecloudplatform.github.io/jit-groups/images/MPA-Activation-Screencast.gif?raw=true'>
<img src='https://googlecloudplatform.github.io/jit-groups/images/MPA-Activation_350.png' align='right'>
</a>

For roles that require [multi-party approval](https://googlecloudplatform.github.io/jit-groups/multi-party-approval/), 
you can request access in four steps:

1. Select the project you need to access
2. Select the role to activate (from your list of eligible roles)
3. Select one or more peers to approve your request (peers are users that share the same level of access as you)
3. Enter a justification (like a bug or case number)

Your selected peers are notified via email and can approve your request. Once approved, the application 
[grants you temporary access](https://cloud.google.com/iam/docs/configuring-temporary-access) to the project
and notifies you via email.



<img src='doc/pix.gif' width='100%' height='1'>


## Grant access

<a href='https://googlecloudplatform.github.io/jit-groups/images/Condition.png?raw=true'>
<img src='https://googlecloudplatform.github.io/jit-groups/images/Condition_350.png' align='right'>
</a>

As an administrator, you can grant a role (to a user or group) and make it _eligible_ by adding a special IAM condition:

* `has({}.jitAccessConstraint)` (no approval required)
* `has({}.multiPartyApprovalConstraint)` ([multi-party approval](https://googlecloudplatform.github.io/jit-groups/multi-party-approval/) required) 

You can create the binding for a specific project, or for an entire folder. Instead of granting eligible
access to individual users, you can also use groups.

To limit access to a subset of resources, you can also include a [resource condition](https://googlecloudplatform.github.io/jit-groups/resource-conditions/)
in the IAM binding.


<img src='doc/pix.gif' width='100%' height='1'>


## Audit access

<a href='https://googlecloudplatform.github.io/jit-groups/images/AuditLog.png?raw=true'>
<img src='https://googlecloudplatform.github.io/jit-groups/images/AuditLog_350.png' align='right'>
</a>

As an administrator, you can use Cloud Logging to review when and why eligible roles have been activated by users. 
For each activation, the Just-In-Time application writes an audit log entry that contains information about:

* the user that requested access
* the user's device, including satisfied [access levels](https://cloud.google.com/access-context-manager/docs/manage-access-levels) 
* the project and role for which access was requested
* the justification provided by the user

<img src='doc/pix.gif' width='100%' height='1'>


## Deploy the application

Just-In-Time Access runs on App Engine (standard) and Cloud Run. The application
is stateless and uses [Identity-Aware-Proxy](https://cloud.google.com/iap/docs/concepts-overview) for authentication and authorization, 
and the [Cloud Asset API](https://cloud.google.com/asset-inventory/docs/reference/rest) and 
[IAM API](https://cloud.google.com/iam/docs/reference/rest) to manage access.

For detailed instructions on deploying Just-In-Time Access, see [Manage just-in-time privileged access to projects ](https://cloud.google.com/architecture/manage-just-in-time-privileged-access-to-project) on the Google Cloud website.

--- 

_Just-In-Time Access is an open-source project and not an officially supported Google product._

_All files in this repository are under the
[Apache License, Version 2.0](LICENSE.txt) unless noted otherwise._
