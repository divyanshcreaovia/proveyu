#!/usr/bin/env bash
# Starts the Spring Boot backend with variables from Backend/.env (copy Backend/.env.example first).
set -euo pipefail
cd "$(dirname "$0")/../Backend"
if [ ! -f .env ]; then echo "Backend/.env missing - copy Backend/.env.example and fill it in"; exit 1; fi
set -a; . ./.env; set +a
MVN=${MVN:-mvn}
command -v "$MVN" >/dev/null 2>&1 || MVN=$(ls -d ~/.m2/wrapper/dists/apache-maven-*/*/apache-maven-*/bin/mvn 2>/dev/null | head -1)
exec "$MVN" -q spring-boot:run
