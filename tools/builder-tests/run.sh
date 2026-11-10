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
javac -encoding UTF-8 -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/builder/BuildProgress.java "$D"/tools/builder-tests/BuildProgressTests.java
java -Dfile.encoding=UTF-8 -cp "$O" BuildProgressTests
javac -encoding UTF-8 -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/builder/PreviewHtml.java "$D"/tools/builder-tests/PreviewHtmlTests.java
java -Dfile.encoding=UTF-8 -cp "$O" PreviewHtmlTests
javac -encoding UTF-8 -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/core/SyncCodec.java "$D"/tools/builder-tests/SyncCodecTests.java
java -Dfile.encoding=UTF-8 -cp "$O" SyncCodecTests
javac -encoding UTF-8 -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/api/SearchRanker.java "$D"/tools/builder-tests/SearchRankerTests.java
java -Dfile.encoding=UTF-8 -cp "$O" SearchRankerTests
javac -encoding UTF-8 -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/utils/FileDelivery.java "$D"/tools/builder-tests/FileDeliveryTests.java
java -Dfile.encoding=UTF-8 -cp "$O" FileDeliveryTests
javac -encoding UTF-8 -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/utils/MemoryExtractor.java "$D"/app/src/main/java/com/virtixstudio/kruxai/utils/MemorySelector.java "$D"/tools/builder-tests/MemoryTests.java
java -Dfile.encoding=UTF-8 -cp "$O" MemoryTests
javac -encoding UTF-8 -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/core/SyncCodec.java "$D"/app/src/main/java/com/virtixstudio/kruxai/core/WorkspaceBundle.java "$D"/tools/builder-tests/WorkspaceBundleTests.java
java -Dfile.encoding=UTF-8 -cp "$O" WorkspaceBundleTests
javac -encoding UTF-8 -d "$O" "$D"/app/src/main/java/com/virtixstudio/kruxai/utils/MemoryExtractor.java "$D"/app/src/main/java/com/virtixstudio/kruxai/utils/MemorySelector.java "$D"/app/src/main/java/com/virtixstudio/kruxai/utils/MemoryBook.java "$D"/tools/builder-tests/MemoryBookTests.java
java -Dfile.encoding=UTF-8 -cp "$O" MemoryBookTests
