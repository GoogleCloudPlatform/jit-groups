You can customize the behavior of JIT Groups by setting environment variables
in your [App Engine configuration file :octicons-link-external-16:](https://cloud.google.com/appengine/docs/standard/java-gen2/config/appref)
or [Cloud Run service YAML :octicons-link-external-16:](https://cloud.google.com/run/docs/reference/yaml/v1).

The following table lists all available configuration options.

## Basic options

| Name                                     | Terraform attribute | Description                                                                                                      | Required | Default          | Available since |
|------------------------------------------|---------------------|------------------------------------------------------------------------------------------------------------------|----------|------------------|-----------------|
| `CUSTOMER_ID` or `RESOURCE_CUSTOMER_ID` | `customer_id`       | [Cloud Identity/Workspace customer ID :octicons-link-external-16:](https://support.google.com/a/answer/10070793) | Yes      |                  | 1.6             |
| `GROUPS_DOMAIN`                          | `groups_domain`     | Domain to use for JIT groups, this can be the primary or a secondary domain                                      | Yes      |                  | 2.0             |
| `APPROVAL_TIMEOUT`                       | -                   | Duration (in minutes) for approval requests to remains valid.                                                    | No       | 60               | 2.0             |

## Email options

The following options let you customize how JIT Groups sends emails.

| Name                    | Terraform attribute | Description                                                                                                        | Required | Default          | Available since |
|-------------------------|---------------------|--------------------------------------------------------------------------------------------------------------------|----------|------------------|-----------------|
| `SMTP_SENDER_NAME`      | -                   | Name used as sender name in notifications.                                                                         | No       | `JIT Groups`     | 1.2             |
| `SMTP_SENDER_ADDRESS`   | `smtp_user`         | Email address to use for notifications.                                                                            | Yes      |                  | 1.2             |
| `SMTP_HOST`             | `smtp_host`         | SMTP server to use for delivering notifications.                                                                   | No       | `smtp.gmail.com` | 1.2             |
| `SMTP_PORT`             | -                   | SMTP port to use for delivering notifications, see remarks below.                                                  | No       | `587`            | 1.2             |
| `SMTP_USERNAME`         | `smtp_user`         | Username for SMTP authentication (optional, only required if your SMTP requires authentication).                   | No       |                  | 1.2             |
| `SMTP_SECRET`           | (automatic)         | Path to a Secrets Manager secret that contains the password for SMTP authentication.                               | No       |                  | 1.4             |
| `SMTP_ADDRESS_MAPPING`  | -                   | Expression for deriving a user's email address, see [Email address mapping](#email-address-mapping).               | No       | 2.0              | 1.7             |
| `SMTP_ENABLE_STARTTLS`  | -                   | Enable StartTLS (required by most mail servers).                                                                   | No       | `true`           | 1.2             |
| `SMTP_OPTIONS`          | -                   | Comma-separated list of additional JavaMail options for delivering email, see remarks.                             | No       |                  | 1.2             |
| `NOTIFICATION_TIMEZONE` | -                   | Timezone to use for dates in notification emails, for example `Australia/Melbourne` or `Europe/Berlin`.            | No       |                  | 1.2             |

Remarks:

+  JIT Groups uses port `587` by default [because port 25 can't be used on Google Cloud](https://cloud.google.com/compute/docs/tutorials/sending-mail#using_standard_email_ports).
+  For a list of JavaMail options to use in  `SMTP_OPTIONS`, see [JavaMail documentation :octicons-link-external-16:](https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html).
   Most mail servers don't require any additional options.
+  For a list of time zone identifiers, see the [IANA Time Zone Database (TZDB) :octicons-link-external-16:](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones)

## Email address mapping

By default, JIT Groups assumes that all Cloud Identity/Workspace user IDs (such as alice@example.com) are valid
email addresses, and can be used to deliver email notifications.

If some or all of your Cloud Identity/Workspace user IDs do not correspond to valid email addresses, 
you can use `SMTP_ADDRESS_MAPPING` to specify a CEL expression that derives a valid email address from the user ID.

CEL expressions can use the following macros and functions:

+   [Standard macros :octicons-link-external-16:](https://github.com/google/cel-java/blob/main/extensions/src/main/java/dev/cel/extensions/README.md#strings)
    such as `filter`, `map`, or `matches` (for [RE2](https://github.com/google/re2/wiki/Syntax) regular expressions).
+   [String functions :octicons-link-external-16:](https://github.com/google/cel-java/blob/main/extensions/src/main/java/dev/cel/extensions/README.md#strings)
    such as `replace`, `substring`, or `trim`.
+   [Encoder functions :octicons-link-external-16:](https://github.com/google/cel-java/blob/main/extensions/src/main/java/dev/cel/extensions/README.md#strings)
    such as `base64.encode` and `base64.decode`.
+   [`extract` :octicons-link-external-16:](https://cloud.google.com/iam/docs/conditions-attribute-reference#extract)

Examples:

+    The following CEL expression replaces the domain `example.com` with `test.example.com` for all users:
     
         user.email.extract('{handle}@example.com') + '@test.example.com'
   
+    The following CEL expression substitutes the domain `external.example.com` with `otherdomain.example`, 
     but keeps all other domains:

         user.email.endsWith('@external.example.com') 
           ? user.email.extract('{handle}@external.example.com') + '@otherdomain.example' 
           : user.email

## Networking options


| Name                      | Description                                             | Required  | Default | Available since |
|---------------------------|---------------------------------------------------------|-----------|---------|-----------------|
| `IAP_VERIFY_AUDIENCE`     | Enable audience verification, see remarks.              | No        | `true`  | 1.8.1           |
| `IAP_BACKEND_SERVICE_ID`  | ID of the load balancer backend, see remarks.           | No        |         | 1.3             |
| `BACKEND_CONNECT_TIMEOUT` | Connection timeout for Google API requests, in seconds. | No        | `5`     | 1.5             | 
| `BACKEND_READ_TIMEOUT`    | Read timeout for Google API requests, in seconds.       | No        | `20`    | 1.5             | 
| `BACKEND_WRITE_TIMEOUT`   | Write timeout for Google API requests, in seconds.      | No        | `5`     | 1.5             |

Remarks:

+    When `IAP_VERIFY_AUDIENCE` is `true` (default), JIT Groups 
     [verifies the audience of IAP assertions :octicons-link-external-16:](https://cloud.google.com/iap/docs/signed-headers-howto#verifying_the_jwt_payload).
     On Cloud Run, this requires `IAP_BACKEND_SERVICE_ID` to contain the backend ID of the load balancer.

     When `IAP_VERIFY_AUDIENCE` is `false` JIT Groups verifies the authenticity of IAP assertions, but does not verify their audience.


## Compatibility

The following options let you configure compatibility with JIT Access 1.x. The options only affect groups in the
`classic` environment.


| Name                       | Terraform attribute | Description                      | Required  | Default          | Available since  |
|----------------------------|---------------------|----------------------------------|-----------|------------------|------------------|
| `RESOURCE_SCOPE`           | `resource_scope`    | [Details](configuration-options) | No        | -                | 2.0              |
| `RESOURCE_CATALOG`         | -                   | [Details](configuration-options) | No        | `AssetInventory` | 2.0              |
| `ACTIVATION_TIMEOUT`       | -                   | [Details](configuration-options) | No        | -                | 2.0              |
| `JUSTIFICATION_HINT`       | -                   | [Details](configuration-options) | No        | -                | 2.0              |
| `JUSTIFICATION_PATTERN`    | -                   | [Details](configuration-options) | No        | -                | 2.0              |
| `AVAILABLE_PROJECTS_QUERY` | -                   | [Details](configuration-options) | No        | -                | 2.0              |

The following options from JIT Access 1.x are not supported in JIT Groups:

+ `ACTIVATION_REQUEST_MAX_ROLES`
+ `ACTIVATION_REQUEST_MIN_REVIEWERS`
+ `ACTIVATION_REQUEST_MAX_REVIEWERS`
+ `NOTIFICATION_TOPIC`
