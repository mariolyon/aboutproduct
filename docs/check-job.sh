#!/bin/sh

source .env
set -eu

curl "https://nuextract.ai/api/structured-extraction/jobs/$JOB_ID" \
  -H "Authorization: Bearer $API_KEY"
