#!/bin/bash
set -euxo pipefail

# Replace (region) and (projectID) with your instance of artifact registry
REGISTRY="(region)-docker.pkg.dev/(projectID)/docker" #Hardcoded from artifact-registry.tf

# update the submodule to the latest
git submodule update --init --recursive --remote

docker build -t "$REGISTRY"/jitaccess:latest \
    --platform linux/amd64 \
    -f ./Dockerfile \
    ./jit-access/sources

docker push "$REGISTRY"/jitaccess:latest 


echo "#############################################"
echo "Please update just-in-time-access/jit.tf to use the new image sha"