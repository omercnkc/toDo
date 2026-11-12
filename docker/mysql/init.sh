#!/bin/bash
# MySQL container ilk açılışta (boş volume) bir kez çalıştırır.
# Her servise ayrı şema + yalnızca kendi şemasına yetkili kullanıcı (bkz. docs/05-database.md).
# Şifreler ortam değişkenlerinden gelir; repoya yazılmaz.
set -euo pipefail

: "${AUTH_DB_PASSWORD:?AUTH_DB_PASSWORD tanımlı değil}"
: "${PROJECT_DB_PASSWORD:?PROJECT_DB_PASSWORD tanımlı değil}"
: "${TASK_DB_PASSWORD:?TASK_DB_PASSWORD tanımlı değil}"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS auth_db    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS project_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS task_db    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'auth_user'@'%'    IDENTIFIED BY '${AUTH_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'project_user'@'%' IDENTIFIED BY '${PROJECT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'task_user'@'%'    IDENTIFIED BY '${TASK_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON auth_db.*    TO 'auth_user'@'%';
GRANT ALL PRIVILEGES ON project_db.* TO 'project_user'@'%';
GRANT ALL PRIVILEGES ON task_db.*    TO 'task_user'@'%';
FLUSH PRIVILEGES;
SQL

echo "init.sh: auth_db, project_db, task_db ve kullanıcıları oluşturuldu."
