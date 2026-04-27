#!/bin/sh

source .env
curl "https://nuextract.ai/api/structured-extraction" \
  -H "Authorization: Bearer $NU_API_KEY" | jq
