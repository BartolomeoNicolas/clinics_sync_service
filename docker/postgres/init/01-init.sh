#!/bin/bash
# Creación del usuario de aplicación y de su base.
#
# Corre una sola vez, la primera vez que Postgres inicializa el volumen.
#
# Por qué el usuario de la aplicación NO es el de la imagen: si se creara con
# POSTGRES_USER, sería superusuario y los privilegios no se aplicarían. Un
# superusuario se salta todos los GRANT y REVOKE, y entonces el criterio
# "sync_app no puede conectarse a ninguna otra base" sería falso de hecho.

set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL

    -- Usuario de aplicación: sin superusuario, sin crear bases ni roles.
    CREATE ROLE sync_app WITH
        LOGIN
        PASSWORD '${SYNC_APP_PASSWORD}'
        NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;

    -- Su propia base, de la que es dueño.
    CREATE DATABASE clinics_sync OWNER sync_app;

    -- Aislamiento: sin permiso genérico, el usuario de la aplicación solo
    -- puede conectarse a la base que le corresponde.
    REVOKE ALL ON DATABASE postgres  FROM PUBLIC;
    REVOKE ALL ON DATABASE template1 FROM PUBLIC;

    GRANT CONNECT, TEMPORARY ON DATABASE clinics_sync TO sync_app;

EOSQL

echo "  · sync_app creado, sin superusuario, aislado a clinics_sync"