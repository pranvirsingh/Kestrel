#!/bin/bash
cd /home/claude/kestrel
rm -rf build/jvm
SRC=$(ls src/com/pranvir/kestrel/*.kt | grep -v -e MainActivity -e Audio.kt)
./kc.sh build/jvm "" $SRC jvmtest/shim/*.kt jvmtest/*.kt 2>&1 | grep -E "error" | head -30
for m in "$@"; do java -Xmx3g -Djava.awt.headless=true -cp build/jvm:/home/claude/tc/kotlin-stdlib-2.3.10-RC.jar $m 2>&1 | grep -v JAVA_TOOL | tail -40; done
