#!/usr/bin/env sh
echo "#RUN"
set -euo pipefail

docker run -it --rm \
  --env-file docs/.env \
  -p 8080:8080 \
  ghcr.io/mariolyon/aboutproduct:latest
