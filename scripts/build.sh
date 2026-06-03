#!/usr/bin/env sh
echo "#BUILD"
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
docker build -t ghcr.io/mariolyon/aboutproduct:latest .
