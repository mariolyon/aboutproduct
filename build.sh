#!/usr/bin/env sh
echo "#BUILD"
set -euo pipefail

docker build -t ghcr.io/mariolyon/aboutproduct:latest .
