#!/bin/bash
# CardGame 本地预检：编译 + 全量单测（与 CI 同仓库路径），docker 可用时可选跑 Qodana。
# 用法: scripts/dev-verify.sh [--fast] [--qodana]
#   --fast    只跑 mvn test（跳过 package，日常提交前够用）
#   --qodana  强制本地跑 Qodana（需要 docker；失败即整体失败）
set -euo pipefail
cd "$(dirname "$0")/.."

MODE=verify
FORCE_QODANA=0
for a in "$@"; do
  case "$a" in
    --fast) MODE=test ;;
    --qodana) FORCE_QODANA=1 ;;
    *) echo "未知参数: $a"; exit 2 ;;
  esac
done

command -v mvn >/dev/null 2>&1 || { echo "[错误] 未找到 Maven"; exit 1; }

echo "==> [1/2] mvn ${MODE}（本地仓库 .m2repo，与 CI 一致）"
mvn -B "-Dmaven.repo.local=.m2repo" "$MODE"

echo "==> [2/2] Qodana 静态扫描（可选）"
if command -v docker >/dev/null 2>&1; then
  mkdir -p target/qodana
  if docker run --rm -v "$(pwd)":/project -v "$(pwd)/target/qodana":/qodana/out \
       -e QODANA_TOKEN="${QODANA_TOKEN:-}" jetbrains/qodana-jvm:latest --result-directory=/qodana/out; then
    echo "[ok] Qodana 完成，报告见 target/qodana/"
  elif [ "$FORCE_QODANA" = 1 ]; then
    echo "[错误] Qodana 失败（--qodana 已指定，视为不通过）"; exit 1
  else
    echo "[warn] Qodana 运行失败（不阻塞；PR CI 上 qodana_code_quality.yml 仍会把关）"
  fi
elif [ "$FORCE_QODANA" = 1 ]; then
  echo "[错误] --qodana 需要 docker，本机未安装"; exit 1
else
  echo "[skip] 本机无 docker：跳过本地 Qodana（CI 仍会扫描）"
fi

echo ""
echo "✅ 预检通过，可以提交"
