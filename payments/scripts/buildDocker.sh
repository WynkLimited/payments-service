#!/usr/bin/env bash

cd ..

env=$1
env=${env:-local}

echo "################ Buidling revenue-payment for environment : $env ###################"
mvn clean install -P revenue-payment

echo "################ Building revenue-payment for environment : $env ###################"
$(aws ecr get-login --no-include-email --region ap-south-1)
docker build -f payments/scripts/Dockerfile -t "revenue-payment" --build-arg profile=${env} .
