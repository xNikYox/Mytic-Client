#!/bin/bash
# Baut die Mytic-Mod für alle unterstützten Minecraft-Versionen und legt sie in launcher/resources/mods/<version>/.
# Benötigt: JDK 21 in JAVA21_HOME, JDK 25 in JAVA25_HOME.
set -e
cd "$(dirname "$0")"
J21=${JAVA21_HOME:?JAVA21_HOME setzen}
J25=${JAVA25_HOME:?JAVA25_HOME setzen}
VERSION=$(grep '^version' mod/build.gradle.kts | cut -d'"' -f2)
put() { mkdir -p "launcher/resources/mods/$1" && rm -f "launcher/resources/mods/$1"/*.jar && cp "$2" "launcher/resources/mods/$1/"; echo "  $1 ✓"; }
retry() { for t in 1 2 3; do "$@" && return 0; echo "  … erneuter Versuch"; sleep 8; done; return 1; }

echo "1.21.11"
(cd mod && JAVA_HOME=$J21 retry ./gradlew --no-daemon -q build)
put 1.21.11 "mod/build/libs/mytic-client-$VERSION.jar"

for v in ${MODS_121:-1.21.10 1.21.9 1.21.8 1.21.7 1.21.6}; do
  echo "$v"
  (cd mod-121 && JAVA_HOME=$J21 retry ./gradlew --no-daemon -q build -Pmc=$v)
  put $v "mod-121/build/libs/mytic-client-$VERSION+$v.jar"
done

for v in 26.3 26.2 26.1.2 26.1.1 26.1; do
  echo "$v"
  (cd mod-26 && JAVA_HOME=$J25 retry ./gradlew --no-daemon -q build -Pmc=$v)
  put $v "mod-26/build/libs/mytic-client-$VERSION+$v.jar"
done
