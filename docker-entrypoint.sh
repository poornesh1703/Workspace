#!/bin/sh
set -eu

if [ -n "${DB_HOST:-}" ]; then
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
fi

exec java -jar /app/app.jar
