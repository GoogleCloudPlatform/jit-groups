# jitgroups-environment-v2

Terraform module that stores a [JIT Groups policy](https://googlecloudplatform.github.io/jit-groups/policy-reference/)
in [Parameter Manager](https://docs.cloud.google.com/secret-manager/parameter-manager/docs/overview).

Because arameter Manager is not available in all Google Cloud regions, the module currently
only supports `global` as location for parameters.