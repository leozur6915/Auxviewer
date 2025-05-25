#!/usr/bin/env sh
# ——— Gradle wrapper bootstrap (Unix) ———
DIR=$(cd "$(dirname "$0")" && pwd)
exec "$DIR"/gradle/wrapper/gradlew "$@"
