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

if ! docker inspect --format='{{.State.Running}}' signal_frontend 2>/dev/null | grep -q true; then
    echo "ERROR: signal_frontend is not running."
    exit 1
fi

echo "Waiting for application..."

for i in {1..12}; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/ql/ || true)

    if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 400 ]; then
        echo "Backend application is responding. HTTP status: $HTTP_CODE"
        break
    fi

    echo "Backend not ready yet. Attempt $i/12..."
    sleep 5
done

# Frontend: index page + SPA fallback must respond
if curl -sf -o /dev/null http://localhost/ && curl -sf -o /dev/null http://localhost/discover; then
    echo "Frontend health check passed."
else
    echo "ERROR: frontend not responding on port 80."
    docker logs --tail 50 signal_frontend
    exit 1
fi

echo "Deployment validation successful."
