#!/bin/bash
# CardGame macOS 一键打包：编译 → 收集依赖 → jpackage 生成 .app
set -e
cd "$(dirname "$0")"

echo "=========================================="
echo " CardGame - macOS 打包（自包含 .app）"
echo "=========================================="

command -v mvn >/dev/null 2>&1 || { echo "[错误] 未找到 Maven"; exit 1; }
command -v jpackage >/dev/null 2>&1 || { echo "[错误] 未找到 jpackage（需要 JDK 17+）"; exit 1; }

echo "[1/3] 编译打包 jar ..."
mvn -Dmaven.repo.local=.m2repo package -DskipTests

echo "[2/3] 收集运行依赖 ..."
rm -rf target/pkg/input
mkdir -p target/pkg/input
mvn -Dmaven.repo.local=.m2repo dependency:copy-dependencies \
  -DoutputDirectory=target/pkg/input -DincludeScope=runtime
cp target/card-game-1.0-SNAPSHOT.jar target/pkg/input/

echo "[3/3] jpackage 生成应用 ..."
rm -rf target/pkg/output/CardGame.app
mkdir -p target/pkg/output
jpackage --type app-image --name CardGame --app-version 1.0.0 \
  --input target/pkg/input --main-jar card-game-1.0-SNAPSHOT.jar \
  --main-class org.example.card.ui.Main --dest target/pkg/output \
  --java-options "--enable-native-access=ALL-UNNAMED"

echo ""
echo "=========================================="
echo " 完成！应用：target/pkg/output/CardGame.app"
echo " 双击即可运行（已包含 Java 运行时，无需安装 JDK）"
echo " 如需 dmg：jpackage --type dmg"
echo "=========================================="