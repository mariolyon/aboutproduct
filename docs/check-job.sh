#!/bin/sh

set -eua
source .env

curl "https://nuextract.ai/api/structured-extraction/jobs/$JOB_ID" \
  -H "Authorization: Bearer $API_KEY"
