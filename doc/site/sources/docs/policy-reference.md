Policy documents define the configuration for an [environment](#environment) and use YAML format.
A minimal policy document looks as follows:

```yaml
schemaVersion: 1
environment:
  name: "my-environment"
  description: "Example environment"
```

`schemaVersion` **Required**

:   Defines the version of the file format. Must be set to `1`.

`environment` **Required**

:   Contains the configuration for the environment, see [next section](#environment) for details.


## Environment

An environment represents a set of resources that have similar access requirements. An environment 
typically corresponds to a folder in a Google Cloud organization.

```yaml  hl_lines="3-9"
...
environment:
  name: "my-environment"              
  description: "Example environment"  
  access: []                          
  constraints:
    join: []
    approve: []
  systems: []
```

`name` **Required**

:   Name of the environment.

    The environment name must match the name of the corresponding service account. For example,
    if your environment's service account is named `jit-datamart@PROJECT-ID.iam.gserviceaccount.com`, 
    then you must set `name` to `datamart`.

    The application uses the environment name as unique identifier and incorporates it into the name
    of Cloud Identity groups. Names must therefore comply with the following restrictions:

    +   Names are case-insensitive. You can't have two environments whose names only differ in casing.
    +   Names must be no longer than 16 characters.
    +   Names must only use the following characters: `A-Z`, `a-z`, `0-9`, `-`.

`description` **Optional**

:   Text that describes the purpose of the environment. The text is shown in the user interface and
    is for informational purposes only.

`access` **Optional**

:   [Access control list](#access-control-list) (ACL) for the environment. The environment ACL serves two purposes:

    1.  It controls who is allowed to view the environment and browse its [systems](#system) and [groups](#jit-groups)
        in the user interface. To view the environment, users need at least `VIEW` access.

    2.  It defines default access for the [systems](#system) and [JIT groups](#jit-groups) of this
        environment. Similar to how the bindings of Google Cloud IAM policies _inherit down_ from folder to project
        to resource, access control entries _inherit down_ from environment to system to JIT group.

    If you don't specify an access control list, then the application uses the following default ACL which
    grants `VIEW` access to all users:

    ```yaml
    access:
    - principal: "class:iapUsers"
      allow: "VIEW"
    ```

    !!!note
        Access to the application is guarded by Identity-Aware Proxy (IAP). The principal `class:iapUsers`
        therefore only includes users that have been authorized by IAP to access the application.

`constraints` **Optional**

:   Default `join` and `approve` [constraints](#constraint) that apply to all JIT groups of this environment.

`systems` **Optional**

:   List of systems, see [next section](#system) for details.

## System

A system represents a set of resources that logically belong together. A system typically corresponds to
a small set of projects in a Google Cloud organization.

```yaml hl_lines="5-11"
...
environment:
  ...
  systems:
    - name: "datamart"
      description: "Contains groups that manage access to the corporate data mart"
      access: []
      constraints:
        join: []
        approve: []
      groups:
```

`name` **Required**

:   Name of the system.

    The application uses the system name as unique identifier and incorporates it into the name
    of Cloud Identity groups. Names must therefore comply with the following restrictions:

    +   Names are case-insensitive. You can't have two systems in the same environment whose names only differ in casing.
    +   Names must be no longer than 16 characters.
    +   Names must only use the following characters: `A-Z`, `a-z`, `0-9`, `-`.

`description` **Optional**

:   Text that describes the purpose of the system. The text is shown in the user interface and
    is for informational purposes only.

`access` **Optional**

:   [Access control list](#access-control-list) (ACL) for the system. Similar to the environment ACL,
    the system ACL serves two purposes:

    1.  It controls who is allowed to view the system and browse its [groups](#jit-groups)
        in the user interface. To view the system, users need at least `VIEW` access.

    2.  It defines default access for the [JIT groups](#jit-groups) of this
        system. Similar to how the bindings of Google Cloud IAM policies _inherit down_ from folder to project
        to resource, access control entries _inherit down_ from environment to system to JIT group.

    !!!note
        As a result of ACL inheritance, the _effective_ ACL of a system includes all 
        entries from the environment ACL _plus_ all entries defined in `access`. 

`constraints` **Optional**

:   Default `join` and `approve` [constraints](#constraint) that apply to all JIT groups of this system.

`groups` **Optional**

:   List of JIT groups, see [next section](#jit-group) for details.

## JIT Group

A JIT group represents a job function or role and bundles all access that's required for users to
perform this job function or role.


```yaml hl_lines="7-12"
...
environment:
  ...
  systems:
  - name: "datamart"
    groups:
    - name: "datamart-admins"
      description: "Admin-level access to data and stuff"
      access: []
      constraints:
        join: []
        approve: []
```

`name` **Required**

:   Name of the JIT group.

    The application uses the system name as unique identifier and incorporates it into the name
    of Cloud Identity groups. Names must therefore comply with the following restrictions:

    +   Names are case-insensitive. You can't have two groups whose names only differ in casing.
    +   Names must be no longer than 24 characters.
    +   Names must only use the following characters: `A-Z`, `a-z`, `0-9`, `-`.

`description` **Optional**

:   Text that describes the purpose of the JIT group. The text is shown in the user interface and
    is for informational purposes only.

`access` **Optional**

:   [Access control list](#access-control-list) (ACL) for the JIT Group. The ACL controls 
    who which users are allowed to view or join this group, and who is allowed to approve
    join requests. See [Access control list](#access-control-list) for details on the individual
    permissions that you can grant.

    !!!note
        As a result of ACL inheritance, the _effective_ ACL of a JIT group includes all 
        entries from the environment ACL _plus_ all entries the parent system ACL
        _plus_ all entries defined in `access`. 

`constraints` **Optional**

:   `join` and `approve` [constraints](#constraint) that apply to this JIT group.

## Access Control List

An Access Control List (ACL) contains one or more access control entries (ACE).

```yaml hl_lines="2-9"
access:
- principal: "class:iapUsers"         # Everybody can view
  allow: "VIEW"
- principal: "group:devops-staff@example.com"   # Devops staff can request to join
  allow: "JOIN"
- principal: "user:mike.manager@example.com"    # Only Mike can approve
  allow: "APPROVE_OTHERS"
- principal: "group:summer-interns@example.com" # Interns are exempt
  deny: "JOIN"
```

Each ACE can have the following attributes:

`principal` **Required**

:   The principal identifier that selects the user or group that this ACE applies to:

    | Principal        | Description      | Example          |
    |------------------|------------------|------------------|
    | `user:USER_EMAIL` | User with primary email address `USER_EMAIL`. | `user:bob@example.com` |
    | `group:GROUP_EMAIL` | Includes all _direct_ members of the Cloud Identity/Workspace group `GROUP_EMAIL`.| `group:devops-staff@example.com`|
    | `class:iapUsers` |  Includes all users that have been authorized by IAP to access the application ||

    **Remarks**:

    +   The principal identifier `group:GROUP_EMAIL` does not apply to JIT groups, it only applies to regular
        Cloud Identity/Workspace security and discussion-forum groups.
    +   You can grant access to users and groups from external Cloud Identity/Workspace accounts. 

`allow` or `deny` **Required**

:   The permission that this ACE allows or denies.
    
    | Permission       | Description                                                                          | Applies to                 |
    |------------------|--------------------------------------------------------------------------------------|----------------------------|
    | `VIEW`           | View the environment, system, or group.                                              | Environment, system, group |
    | `JOIN`           | Request to join a group.                                                             | Group                      |
    | `APPROVE_SELF`   | Join group without approval. Only effective when granted in combination with `JOIN`. | Group                      |
    | `APPROVE_OTHERS` | Approve other's requests to join the group.                                          | Group                      |
    | `EXPORT`         | View or export the policy in the user interface.                                     | Environment                |
    | `RECONCILE`      | Reconcile the policy.                                                                | Environment                |
    | `ALL`            | Full access, includes all other permissions.                                         | Environment, system, group |
    
    **Remarks**:
    
    +   `APPROVE_OTHERS` does _not_ allow users to approve their own join requests. Only the `APPROVE_SELF` permission
        allows users to join without approval.
    +   The  `VIEW` permission is implied by all other permissions.

## Constraint

JIT groups can have `join` and `approve` constraints:

+   `join` constraints define conditions that users must meet before they can request to join the group.
+   `approve` constraints define conditions that users must meet before they can approve a join request.

Like Access Control Lists, constraints support inheritance:

+    Constraints defined at the environment-level apply to all systems and JIT groups.
+    Constraints defined at the system-level apply to all JIT groups of that system.
+    Constraints defined at the JIT group-level only apply to that group.

There are two types of constrains, `expiry` and `expression` constraints.

### Expiry constraint

An `expiry` constraint defines for how long a user can request to join a JIT group:

```yaml hl_lines="3-5"
constraints:
  join:
    - type: "expiry"
      min: "PT1H"
      max: "P7D"
```

All JIT groups must have an `expiry` `join`-constraint. Expiry constraints defined at a 
lower level _override_ inherited expiry constraints.

An `expiry` constraint can have the following attributes:

`type` **Required**

:   Type of constraint, must be `expiry`.

`min`, `max` **Required**

:   The minimum and maximum expiry.

    +   If you choose different values for `min` and `max`, users can choose an expiry that suits their needs, but
        they can't go above or below the `min` and `max` values. 
    +   If you set `min` and `max` to the same value, then the expiry is fixed and users aren't offered a choice.

    The expiry value uses the ISO 8601 duration format:

    ```
    P(n)DT(n)H(n)M
    ```

    Where:
    
    +   `P` is a fixed prefix.
    +   `(n)D` defines the number of days.
    +   `(n)H` defines number of hours.
    +   `(n)M`defines the number of minutes.

    For example:

    | Duration | Description |
    |----------|-------------|
    | `PT1H`  | One hour |
    | `P1D` or `PT24H` | One day |
    | `P1DT6H` | One day and 6 hours |
    | `P90D`   | 90 days |

    **Remarks**:
    
    +   Days are always counted as 24 hours, regardless of time zone and daylight saving times.
    +   You can't specify durations in weeks or months, use days instead. 

### Expression constraint

An `expression` constraint defines a custom condition that users must meet:

```yaml hl_lines="3-7"
constraints:
  join:
  - type: "expression"
    name: "ticketnumber"
    displayName: "You must provide a ticket number as justification"
    expression: "input.ticketnumber.matches('^[0-9]+$')"
    variables:
      - type: "string"
        name: "ticketnumber"
        displayName: "Ticket number"
        min: 1
        max: 10
```

Constraints defined at a lower level _override_ inherited constraints if their `name` matches.

An `expression` constraint can have the following attributes:

`type` **Required**

:   Type of constraint, must be `expression`.

`name` **Required**

:   Name of the constraint. The name must only include letters, digits and hyphens.

`displayName` **Required**

:   Text that describes the constraint, typically phrased as `You must ...`.

`expression` **Required**

:   A [CEL expression :octicons-link-external-16:](https://github.com/google/cel-spec/blob/master/doc/langdef.md) that evaluates to
    `true` (in which case the constraint is satisfied) or `false`.

    CEL expressions can access the following variables:

    | Variable  | Description   |
    |-----------|---------------|
    | `subject.email` | Email address of the current user. |
    | `subject.principals` | List of principals of the current user, including its groups and JIT groups.|
    | `group.environment` | Name of the environment that this JIT group belongs to.|
    | `group.system` | Name of the system that this JIT group belongs to.|
    | `group.name` | Name of the JIT group.|
    | `input.NAME` | Custom input variable, see below.|

    CEL expressions can use the following macros and functions:

    +   [Standard macros :octicons-link-external-16:](https://github.com/google/cel-java/blob/main/extensions/src/main/java/dev/cel/extensions/README.md#strings)
        such as `filter`, `map`, or `matches` (for [RE2](https://github.com/google/re2/wiki/Syntax) regular expressions).
    +   [String functions :octicons-link-external-16:](https://github.com/google/cel-java/blob/main/extensions/src/main/java/dev/cel/extensions/README.md#strings)
        such as `replace`, `substring`, or `trim`.
    +   [Encoder functions :octicons-link-external-16:](https://github.com/google/cel-java/blob/main/extensions/src/main/java/dev/cel/extensions/README.md#strings)
        such as `base64.encode` and `base64.decode`.
    +   [`extract` :octicons-link-external-16:](https://cloud.google.com/iam/docs/conditions-attribute-reference#extract)

### Expression variables

Variables let you prompt the user to provide input, and you can then check this input in the CEL
expression:

```yaml hl_lines="8-12"
constraints:
  join:
  - type: "expression"
    name: "ticketnumber"
    displayName: "You must provide a ticket number as justification"
    expression: "input.ticketnumber.matches('^[0-9]+$')"
    variables:
      - type: "string"
        name: "ticketnumber"
        displayName: "Ticket number"
        min: 1
        max: 10
```

`type` **Required**

:   Data type, must be one of `string`, `int`, or `boolean`.

`name` **Required**

:   Name of the variable. The name is case-sensitive and must only include letters, digits and hyphens.

`displayName` **Required**

:   Display name, shown as label for the input field in the user interface.

`min`, `max` **Optional**

:   Limit the allowed range:

    +   For `string`-typed variables,  `min` and `max` define the minimum and maximum input length, in characters.
    +   For `int`-typed variables,  `min` and `max` define the minimum and maximum value (inclusive).

## Privilege

Privileges define the projects that members of a JIT groups are granted access to:

```yaml hl_lines="2-9"
privileges:
  iam:
  - resource: "projects/project-1"      # Allow view-access to project-1
    role: "roles/compute.viewer"
    
  - resource: "projects/project-3"      # Allow limited view-access to project-3
    role: "roles/compute.viewer"
    description: "View Compute Engine instances"
    condition: "resource.type == 'compute.googleapis.com/Instance'"
```

You can list any number of privileges under the `iam` key. Each privilege can have the following attributes:

`resource` **Required**

:   Resource that you want to grant access to, this can be one of the following:
    
    | Resource                                                  | Example                 |
    |-----------------------------------------------------------|-------------------------|
    | `projects/ID`, where `ID` is a [project ID :octicons-link-external-16:](https://cloud.google.com/resource-manager/docs/creating-managing-projects#before_you_begin)                 | `projects/my-project-1`, `my-project-1` |
    | `folders/ID`, where `ID` is a folder ID                   | `folders/1234567890`|
    | `organizations/ID` where `ID` is [an organization ID :octicons-link-external-16:](https://cloud.google.com/resource-manager/docs/creating-managing-organization#retrieving_your_organization_id)|`organizations/1234567890`|

    The `projects/` prefix is optional.

`role` **Required**

:   Name of an IAM role. This can be a predefined role or a custom role.

`description` **Optional**

:   Text that describes the scope or purpose of this privilege. The text is shown in the user interface and 
    is for informational purposes only.

`condition` **Optional**

:   An [IAM condition :octicons-link-external-16:](https://cloud.google.com/iam/docs/conditions-overview). The application adds this
    condition when creating the respective IAM bindings.

    You can use an IAM condition to grant access to [individual resources such as storage buckets,
    billing accounts, or VM instances :octicons-link-external-16:](https://cloud.google.com/iam/docs/conditions-resource-attributes).