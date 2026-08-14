#!/bin/sh
set -eu
mkdir -p target/classes target/test-classes
javac -d target/classes $(find src/main/java -name '*.java' -print)
javac -cp target/classes -d target/test-classes $(find src/test/java -name '*.java' -print)
java -cp target/classes:target/test-classes dev.infrai.fieldservice.SnapshotPolicyTest
java -cp target/classes dev.infrai.fieldservice.NightlySnapshotRunner "${1:-2026-08-13}"
