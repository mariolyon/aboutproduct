#!/usr/bin/env sh
echo "#RUN"
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd ${ROOT}

./scripts/build.sh
docker run -it --rm \
  --env-file ".env" \
  -p 8080:8080 \
  ghcr.io/mariolyon/aboutproduct:latest
