#!/bin/bash
set -e

echo "Starting Signal application..."

cd /opt/signal

# ---------------------------------------------------------------------
# Optional local override file for debugging.
# It is NOT in git and NOT in the deploy bundle.
# SSM Parameter Store is the source of truth: variables already set here
# are kept (not overwritten by SSM); everything else is fetched from SSM.
# ---------------------------------------------------------------------
if [ -f /opt/signal/.env ]; then
    echo "Loading /opt/signal/.env (optional local overrides)"
    set -a
    source /opt/signal/.env
    set +a
fi

AWS_REGION="ap-south-1"
ECR_REPOSITORY="signal-app"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_URI="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY"

# ---------------------------------------------------------------------
# Configuration + secrets come from SSM Parameter Store (root-level
# names, flat in the account namespace). Nothing is hardcoded here.
#
#   DB_USER, DB_NAME                              -> String
#   DB_PASSWORD, REDIS_PASSWORD, BROKER_APPKEY    -> SecureString
# ---------------------------------------------------------------------
REQUIRED_PARAMS="DB_USER DB_NAME DB_PASSWORD REDIS_PASSWORD BROKER_APPKEY"

echo "Fetching configuration from SSM Parameter Store..."
MISSING=""
for PARAM in $REQUIRED_PARAMS; do
    if [ -n "${!PARAM:-}" ]; then
        echo "  $PARAM: using local override"
        continue
    fi
    VALUE=$(aws ssm get-parameter \
        --name "$PARAM" \
        --with-decryption \
        --query "Parameter.Value" \
        --output text \
        --region "$AWS_REGION" 2>/dev/null) || VALUE=""
    if [ -z "$VALUE" ]; then
        MISSING="$MISSING $PARAM"
    else
        export "$PARAM=$VALUE"
    fi
done

if [ -n "$MISSING" ]; then
    echo "ERROR: missing SSM parameters:$MISSING"
    echo ""
    echo "Fix by creating these parameters in Parameter Store (region $AWS_REGION)"
    echo "and attaching to this EC2 instance role: ssm:GetParameter on the"
    echo "parameter ARNs + kms:Decrypt (for SecureString values)."
    echo "Deployment aborted - the previously running version keeps running."
    exit 1
fi

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
docker compose up -d signal_frontend

echo "Containers started."

docker compose ps
