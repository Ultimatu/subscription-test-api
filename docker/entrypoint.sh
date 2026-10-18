#!/bin/sh
# Lance l'application en adaptant les conventions d'un hébergeur (Render, Heroku…) à celles de Spring Boot.
# Rien n'est modifié si SERVER_PORT ou SPRING_DATASOURCE_URL sont déjà fournis.
set -eu

# PORT : le port imposé par l'hébergeur
if [ -n "${PORT:-}" ] && [ -z "${SERVER_PORT:-}" ]; then
	export SERVER_PORT="$PORT"
fi

# DATABASE_URL : postgresql://utilisateur:mot-de-passe@hôte[:port]/base, que le pilote JDBC ne lit pas
if [ -n "${DATABASE_URL:-}" ] && [ -z "${SPRING_DATASOURCE_URL:-}" ]; then
	address="${DATABASE_URL#*://}"
	credentials="${address%@*}"
	export SPRING_DATASOURCE_USERNAME="${credentials%%:*}"
	export SPRING_DATASOURCE_PASSWORD="${credentials#*:}"
	export SPRING_DATASOURCE_URL="jdbc:postgresql://${address##*@}"
fi

# JAVA_OPTS sans guillemets : chaque option doit devenir un argument distinct
# shellcheck disable=SC2086
exec java ${JAVA_OPTS:-} -jar application.jar "$@"
