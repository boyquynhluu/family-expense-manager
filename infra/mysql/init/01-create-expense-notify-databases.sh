#!/bin/bash
# Runs once, only when the mysql-db container initializes an empty data directory
# (docker-entrypoint-initdb.d convention). fem_auth is already created by the
# MYSQL_DATABASE/MYSQL_USER env vars on the mysql-db service itself — this script
# creates the other two databases + their dedicated users, each restricted to its
# own database only (no cross-service access), matching the FEM_AUTH pattern.
set -e

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE DATABASE IF NOT EXISTS fem_expense;
    CREATE USER IF NOT EXISTS '${FEM_EXPENSE_DB_USER}'@'%' IDENTIFIED BY '${FEM_EXPENSE_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON fem_expense.* TO '${FEM_EXPENSE_DB_USER}'@'%';

    CREATE DATABASE IF NOT EXISTS fem_notify;
    CREATE USER IF NOT EXISTS '${FEM_NOTIFY_DB_USER}'@'%' IDENTIFIED BY '${FEM_NOTIFY_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON fem_notify.* TO '${FEM_NOTIFY_DB_USER}'@'%';

    FLUSH PRIVILEGES;
EOSQL
