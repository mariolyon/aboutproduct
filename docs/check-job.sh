#!/bin/sh

set -eua
source "$(git rev-parse --show-toplevel)/.env"

curl "https://nuextract.ai/api/structured-extraction/jobs/$JOB_ID" \
  -H "Authorization: Bearer $API_KEY"
