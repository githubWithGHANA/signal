#!/bin/bash
set -e

echo "Starting Signal application..."

cd /opt/signal

# Load deployment secrets (DB_PASSWORD, REDIS_PASSWORD, BROKER_APPKEY, IMAGE_TAG...)
# .env is deployed with the bundle; compose also auto-loads it, but exporting
# here makes the values visible to the hook itself too.
if [ -f /opt/signal/.env ]; then
    echo "Loading /opt/signal/.env"
    set -a
    source /opt/signal/.env
    set +a
else
    echo "WARNING: /opt/signal/.env not found - falling back to built-in defaults"
fi

AWS_REGION="ap-south-1"
ECR_REPOSITORY="signal-app"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_URI="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY"

export ECR_REPO="${ECR_REPO:-$ECR_URI}"
export IMAGE_TAG="${IMAGE_TAG:-latest}"

echo "Logging in to ECR..."
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

echo "Pulling application image $ECR_REPO:$IMAGE_TAG ..."
docker pull "$ECR_REPO:$IMAGE_TAG"

echo "Starting containers..."
docker compose up -d signal_db
docker compose up -d signal_app

echo "Containers started."

docker compose ps
