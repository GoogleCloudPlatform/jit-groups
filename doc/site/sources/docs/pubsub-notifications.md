You can configure JIT Access to publish notification messages to Pub/Sub when certain events occur. Other applications can consume these messages to implement additional logic, such as posting to chat rooms or triggering additional workflows.

JIT Access currently supports the following notifications.

## ActivationSelfApproved

JIT Access publishes a message of type `ActivationSelfApproved` when a user activates a role binding that permits self-approval.

The message body looks similar to the following:

```
{
    type: "ActivationSelfApproved",
    attributes: {
        role: "roles/compute.viewer",
        beneficiary: "alice@example.com",
        start_time: "2023-11-28T22:13:44Z",
        end_time: "2023-11-28T22:23:44Z",
        justification: "Working on CASE-123, need to view VMs",
        project_id: "project-1"
    }
}
```


## RequestActivation

JIT Access publishes a message of type `RequestActivation` when a user requests approval for activating a role binding that requires 
[multi-party approval](multi-party-approval.md).

The message body looks similar to the following:

```
{
    "type": "RequestActivation",
    "attributes": {
        "role": "roles/compute.admin",
        "beneficiary": "alice@example.com",
        "start_time": "2023-11-28T22:19:06Z",
        "end_time": "2023-11-28T22:29:06Z",
        "reviewers": [
            "bob@example.com",
            "carol@example.com"
        ],
        "justification": "Working on CASE-123, need to redeploy VMs",
        "action_url": "https://jitaccess.example.com/?activation=JhbGciOi...",
        "request_expiry_time": "2023-11-28T23:19:06Z",
        "base_url": "https://jitaccess.example.com/",
        "project_id": "project-1"
    }
}
```

## ActivationApproved

JIT Access publishes a message of type `ActivationApproved` when a user approves another user's activation request.

The message body looks similar to the following:

```
{
    "type": "ActivationApproved",
    "attributes": {
        "role": "roles/compute.admin",
        "beneficiary": "alice@example.com",
        "start_time": "2023-11-28T22:19:06Z",
        "end_time": "2023-11-28T22:29:06Z",
        "reviewers": [
            "bob@example.com",
            "carol@example.com"
        ],
        "justification": "Working on CASE-123, need to redeploy VMs",
        "base_url": "https://jitaccess.example.com/",
        "approver": "bob@example.com",
        "project_id": "project-1"
    }
}
```


## What's next

* [Configure Pub/Sub notifications](configure-pubsub-notifications.md)