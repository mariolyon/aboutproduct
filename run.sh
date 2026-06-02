#!/usr/bin/env sh
echo "#RUN"
set -euoa pipefail

source "$(git rev-parse --show-toplevel)/.env"

mill backend.run
