#!/bin/sh
echo "#DEPLOY"
kubectl apply -f ./k8s/deployment.yaml
kubectl rollout restart deployment aboutproduct
