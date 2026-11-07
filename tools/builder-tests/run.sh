#!/bin/sh
# Compile et lance les tests (JDK seul, sans Android).
set -e
D="$(cd "$(dirname "$0")/../.." && pwd)"
O="$(mktemp -d)"
javac -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/builder/*.java "$D"/tools/builder-tests/BuilderTests.java
java -cp "$O" BuilderTests
javac -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/live/LiveMotion.java "$D"/tools/builder-tests/LiveMotionTests.java
java -cp "$O" LiveMotionTests
javac -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/core/ModelCooldown.java "$D"/tools/builder-tests/ModelCooldownTests.java
java -cp "$O" ModelCooldownTests
javac -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/builder/*.java "$D"/tools/builder-tests/CheckpointEstimatorTests.java
java -cp "$O" CheckpointEstimatorTests
