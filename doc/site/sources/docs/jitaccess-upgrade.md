# Prerequisites

- must use AssetInventory catalog

## Functional changes

The following table summarizes key differences between JIT Groups and JIT Access:

|                                  | JIT Groups                                                                                     | JIT Access                   |
|----------------------------------|------------------------------------------------------------------------------------------------|------------------------------|
| Use cases                        | - Entitlement management<br>- Self-service access management<br>- Privileged access management | Privileged access management |
| Entitlements being managed       | Memberships to Cloud Identity security groups                                                  | IAM role bindings            |
| Granularity of entitlements      | Groups can "bundle" any number of roles, across one or more projects                           | Single IAM role              |
| Scope of policies                | JIT group, system, environment                                                                 | Global only                  |
| IAM conditions                   | :material-check:                                                                               | :material-check:             |
| Activation without approval      | :material-check:                                                                               | :material-check:             |
| Activation with peer approval    | :material-check:                                                                               | :material-check:             |
| Activation with manager approval | :material-check:                                                                               | :x:                          |


## Compatibility with JIT Access

JIT Groups supports "JIT Access-style" eligible role bindings. When you enable JIT Access compatibility, JIT Groups
surfaces such eligible role bindings as a JIT Group:

+    Role bindings that use the IAM condition `has({}.jitAccessConstraint)` are mapped to JIT Groups that permit
     self-approval.
+    Role bindings that use the IAM condition `has({}.multiPartyApprovalConstraint)` are mapped to JIT Groups that require
     peer approval.

This compatibility is subject to the following limitations:

+    The web interface lists all available projects instead of a personalized list of projects. This behavior is similar
     to the [`AssetInventory`](configure-catalogs.md) catalog.
+    A small number of predefined IAM roles can't be mapped because their name exceeds the limits imposed by Cloud Identity for group names. 
+    Roles that use [resource conditions](resource-conditions.md) can't be mapped.
+    When requesting approval to join a group, users can't select among possible approvers. Instead, the approval
     request is sent to all users who are allowed to approve.
+    JIT Groups doesn't support [Pub/Sub notifications](pubsub-notifications.md).