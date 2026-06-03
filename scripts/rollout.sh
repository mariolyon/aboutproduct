#!/bin/sh

ROOT="$(git rev-parse --show-toplevel)"
cd ${ROOT}

./scripts/build.sh && ./scripts/push.sh && ./scripts/deploy.sh
