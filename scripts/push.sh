#!/bin/sh
echo "#PUSH"

cd "$(git rev-parse --show-toplevel)"
docker push ghcr.io/mariolyon/aboutproduct:latest
