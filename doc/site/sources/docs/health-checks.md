JIT Access exposes endpoints for liveness checks and readiness checks.

## Liveness check

The endpoint `/health/alive` verifies that the application has initialized successfully. If the check 
is successful, the endpoint returns HTTP status `200/OK` with a response that looks similar to:

```
{
  "healthy": true,
  "details":{}
}
```

If the check fails, the endpoint returns HTTP status `503/Service unavailable`. A more detailed
error message is written to the log, but not included in the HTTP response.

## Readiness check

The endpoint `/health/ready` verifies that the application has initialized successfully and performs
additonal checks to confirm that the application is able to serve requests. If the check 
is successful, the endpoint returns HTTP status `200/OK` with a response that looks similar to:

```
{
  "healthy": true,
  "details": {
    "DevModeIsDisabled": true
  }
}
```

If the check fails, the endpoint returns HTTP status `503/Service unavailable`.  A more detailed
error message is written to the log, but not included in the HTTP response.

## Programmatic IAP authentication

To access health check endpoints through IAP, you must include an `Authorization` header with
an ID token that satisfies the following criteria:

*  The token is valid and issued to a service account that is authorized to access IAP.
*  The token contains an `email` claim.
*  The `aud` claim matches the client ID used by IAP (`NNN-xxx.apps.googleusercontent.com`).

For additional details, see [Programmatic IAP authentication :octicons-link-external-16:](https://cloud.google.com/iap/docs/authentication-howto):

