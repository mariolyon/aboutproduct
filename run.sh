#!/usr/bin/env sh
echo "#RUN"
set -euoa pipefail

source ./docs/.env

mill backend.run
