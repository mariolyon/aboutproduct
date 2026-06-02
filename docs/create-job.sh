#!/bin/sh
set -a
source "$(git rev-parse --show-toplevel)/.env"

curl "https://nuextract.ai/api/structured-extraction/$PROJECT_ID/jobs" \
  -X POST \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: image/png" \
  --data-binary "@$1"
