#!/bin/sh
set -a
source "$(git rev-parse --show-toplevel)/.env"
curl "https://nuextract.ai/api/structured-extraction" \
  -H "Authorization: Bearer $NU_API_KEY" | jq
