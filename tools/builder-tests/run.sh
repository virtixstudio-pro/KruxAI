#!/bin/sh
# Compile et lance les tests (JDK seul, sans Android).
set -e
D="$(cd "$(dirname "$0")/../.." && pwd)"
O="$(mktemp -d)"
javac -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/builder/*.java "$D"/tools/builder-tests/BuilderTests.java
java -cp "$O" BuilderTests
