#!/usr/bin/env sh
echo "#RUN"
set -euoa pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd ${ROOT}

source ".env"
mill backend.run
