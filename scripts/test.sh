#!/usr/bin/env bash

# Exit immediately if a command exits with a non-zero status
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd ${ROOT}

echo "=== Running Backend Tests ==="
mill backend.test

echo "=== Running Frontend Tests ==="
mill frontend.test

echo "=== Running UI E2E Tests (Playwright) ==="
# Ensure node_modules are installed
if [ ! -d "node_modules" ]; then
  echo "Installing Node.js dependencies..."
  npm install
fi

# Ensure Playwright browsers are installed
npx playwright install chromium

npm run test:ui

echo "=== All tests passed successfully! ==="
