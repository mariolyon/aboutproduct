#!/bin/sh

source .env

curl "https://nuextract.ai/api/structured-extraction/$PROJECT_ID/jobs" \
  -X POST \
  -H 'Content-Type: application/octet-stream' \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: image/png" \
  --data-binary "@$1"
