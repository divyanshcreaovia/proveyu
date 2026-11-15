#!/usr/bin/env bash
# Starts an isolated MySQL 8 for local development on port 3310 (data kept in the proveyu-mysql-data volume).
set -euo pipefail
if docker ps -a --format '{{.Names}}' | grep -q '^proveyu-mysql$'; then
  docker start proveyu-mysql >/dev/null
else
  docker run -d --name proveyu-mysql -p 3310:3306 -v proveyu-mysql-data:/var/lib/mysql \
    -e MYSQL_ROOT_PASSWORD=proveyu_dev_root -e MYSQL_DATABASE=PROVEYU -e MYSQL_USER=proveyu -e MYSQL_PASSWORD=proveyu_dev mysql:8.0 >/dev/null
fi
until docker exec proveyu-mysql mysqladmin -uroot -pproveyu_dev_root ping >/dev/null 2>&1; do sleep 2; done
echo "MySQL ready on 127.0.0.1:3310 (db PROVEYU, user proveyu / proveyu_dev)"
