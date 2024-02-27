Multi-party approval (MPA) is an optional feature that lets you demand that users solicit approval from a peer before
they can activate certain roles. Multi-party approval is a complement to just-in-time self-approval. It comes in two variations Peer approval and External approval and differs in the following ways:

<table>
<tr>
    <th></th>
    <th>Self-approval</th>
    <th>Peer approval</th>
    <th>External approval</th>
</tr>
<tr>
    <th>IAM condition</th>
    <td><code>has({}.jitAccessConstraint)</code></td>
    <td><code>has({}.multiPartyApprovalConstraint)</code></td>
    <td><code>has({}.externalApprovalConstraint)</code></td>
</tr>
<tr>
    <th>Activation requirements</th>
    <td>Justification (such as a case number)</td>
    <td>
        <ol>
            <li>Justification (such as a case number)</li>
            <li>Approval from a peer</li>
        </ol>
    </td>
    <td>
        <ol>
            <li>Justification (such as a case number)</li>
            <li>Approval from an external reviewer</li>
        </ol>
    </td>
</tr>
</table>

Peer approval is handled on a peer-to-peer basis:
*   If two users are granted a role binding for, say `roles/compute.admin` on `project-1` with the Peer approval
    IAM condition, then they can approve each other’s requests.

External approval is handled slightly differently:
*  A user is granted a role binding say `roles/compute.admin` on `project-1` with the External approval
    IAM condition.
*  Another user is granted an equivalent role binding i.e. `roles/compute.admin` on the same scope with the 
    `has({}.reviewerPrivilege)` IAM condition.
*  The first user can request activation of the role whereas the second user can review and approve the 
    activation request of the first user. Note that the first user cannot review requests and the second user
    cannot request activation.

In either case:
*   When soliciting approval, a user can see the list of qualified reviewers, and can choose the reviewer(s)
    to request approval from. It’s sufficient to gain approval from a single reviewer.

The approval process is driven by email, as illustrated by the following example:

![Overview](images/mpa-overview.png)


1.  Alice opens the JIT Access application, chooses a role, and enters a
    justification for why she needs to activate that role binding (such as `BUG-12345`).
2.  Among the lists of qualified approvers, she selects Bob and requests his approval.
3.  Bob receives an email indicating that Alice seeks his approval to activate a certain role binding.
4.  Following a link in the email, Bob opens the JIT Access application. He sees the resource, role,
    and justification provided by Alice and approves (or disregards) the request.
5.  Alice receives an email confirming that her access has been approved.

## Teams
To provide more fine-grained control over who can approve which requests one can specify a team as part of the IAM condition in the following manner:

```has({}.multiPartyApprovalConstraint.codeNinjas)```

Here the IAM condition specifies the `codeNinjas` team but any name can be used here and it does not need to match any existing groups etc. 

Users given a role binding specifying another different team say

```has({}.multiPartyApprovalConstraint.cloudExperts)```

cannot review the activation requests of users granted the `codeNinjas` team IAM condition and vice versa. 

Teams can be specified both for Peer approval and External approval. In case of external approval the team of the `has({}.externalApprovalConstraint)` condition must match the team of the `has({}.reviewerPrivilege)` condition.

Users granted an IAM binding with either Peer approval privilege or reviewer privilege that does not specify a team can review activation requests originating from the users of any team.

## Activation tokens

The Just-in-Time Access application doesn't maintain a database of approval requests and their state.
Instead, the application uses _activation tokens_ to pass information about approval requests between parties.

Activation tokens are JSON Web Tokens (JWTs) that encode all information about an activation request, including:

* The username of the requesting party (beneficiary)
* The email addresses of reviewers
* The requested role and resource
* The start and end time for the activation 

To protect against tampering, the application signs activation tokens using the Google-managed
service account key of its service account.

Example of a decoded activation token:

``` 
{
  "alg": "RS256",
  "kid": "dfdf92ea289752b4a04f43430f613b97e20c8cae",
  "typ": "JWT"
}.{
  "aud": "jitaccess@PROJECTID.iam.gserviceaccount.com",
  "exp": 1672872064,
  "iss": "jitaccess@PROJECTID.iam.gserviceaccount.com",
  "jti": "mpa-okLRSysI53M73mv5",
  "beneficiary": "alice@example.org",
  "reviewers": [
    "bob@example.org"
  ],
  "resource": "//cloudresourcemanager.googleapis.com/projects/my-project-123",
  "role": "roles/compute.admin",
  "justification": "BUG-12345",
  "start": 1672871764,
  "end": 1672872064
}.[Signature]
```

The Just-in-Time Access application embeds an activation token in the email that it sends to reviewers. Reviewers
can then use the token to view and approve a request, provided that:

1. They've authenticated to the Just-in-Time Access application using Identity-Aware-Proxy
2. Their username is included in the list of reviewers in the token
3. They (still) have (MPA-) eligible access to the role and resource listed in the token

The possession of an activation token alone is insufficient to approve a request. Leaking an activation token
to a third party therefore doesn't entitle that party to approve requests. Because users can't choose themselves as reviewers 
when requesting access, users also can't use activation tokens to approve their own requests.

## What's next

* [Configure Multi-party approval](configure-multi-party-approval.md)