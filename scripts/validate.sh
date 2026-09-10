#!/bin/bash
set -e

echo "Validating deployment..."

cd /opt/signal

echo "Checking containers..."
docker compose ps

if ! docker inspect --format='{{.State.Running}}' signal_db 2>/dev/null | grep -q true; then
    echo "ERROR: signal_db is not running."
    exit 1
fi

if ! docker inspect --format='{{.State.Running}}' signal_app 2>/dev/null | grep -q true; then
    echo "ERROR: signal_app is not running."
    exit 1
fi

echo "Waiting for application..."
sleep 10

if curl -f http://localhost:8081/actuator/health; then
    echo "Application health check passed."
else
    echo "Health endpoint unavailable."
    echo "Checking application logs..."
    docker logs --tail 100 signal_app
    exit 1
fi

echo "Deployment validation successful."