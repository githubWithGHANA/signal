#!/bin/bash
set -e

echo "Stopping existing Signal containers..."

cd /opt/signal || true

if docker compose ps -q signal_app >/dev/null 2>&1; then
    docker compose stop signal_app || true
    docker compose rm -f signal_app || true
fi

echo "Application container stopped."

# Keep PostgreSQL container/data available.
if docker compose ps -q signal_db >/dev/null 2>&1; then
    docker compose stop signal_db || true
fi

echo "Stop phase completed."

# #!/bin/bash
# docker-compose stop signal_app signal_db
# docker-compose rm signal_app signal_db -f

# # Optionally, remove unused volumes and networks
# docker-compose down --volumes --remove-orphans

# # Remove all unused Docker images
# docker image prune -af

# # Remove unused Docker volumes and networks
# docker volume prune -f
# docker network prune -f
