#!/bin/sh
echo "#DEPLOY"

cd "$(git rev-parse --show-toplevel)"
kubectl apply -f k8s/deployment.yaml
kubectl rollout restart deployment aboutproduct
