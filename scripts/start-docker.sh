#!/bin/bash
set -e

echo "Starting Signal application..."

cd /opt/signal

AWS_REGION="ap-south-1"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REPOSITORY="signal-app"
ECR_URI="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY"

echo "Logging in to ECR..."
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

export ECR_REPO="$ECR_URI"
export IMAGE_TAG="latest"

echo "Pulling application image..."
docker pull "$ECR_URI:latest"

echo "Starting containers..."
docker compose up -d signal_db
docker compose up -d signal_app

echo "Containers started."

docker compose ps