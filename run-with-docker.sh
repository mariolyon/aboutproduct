#!/usr/bin/env sh
echo "#RUN"
set -euo pipefail

./build.sh

docker run -it --rm \
  --env-file "$(git rev-parse --show-toplevel)/.env" \
  -p 8080:8080 \
  ghcr.io/mariolyon/aboutproduct:latest
