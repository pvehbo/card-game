#!/bin/bash
# CardGame 发版：前置检查 → 本地预检 → 打 annotated tag → 推送（v* tag 触发 CI 三包 + GitHub Release）。
# 用法: scripts/release.sh 1.5.0 ["发布说明"]
set -euo pipefail
cd "$(dirname "$0")/.."

VER="${1:-}"
[ -n "$VER" ] || { echo "用法: $0 <x.y.z> [发布说明]"; exit 2; }
echo "$VER" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' || { echo "[错误] 版本号必须是 x.y.z（不带 v 前缀）"; exit 2; }
NOTE="${2:-v$VER}"

BRANCH=$(git symbolic-ref --short HEAD)
[ "$BRANCH" = "main" ] || { echo "[错误] 只能在 main 上发版，当前在 $BRANCH"; exit 1; }

REMOTE=$(git remote | grep -Ex 'github|origin' | head -1 || true)
[ -n "$REMOTE" ] || { echo "[错误] 未找到 github/origin remote"; exit 1; }

[ -z "$(git status --porcelain)" ] || { echo "[错误] 工作区有未提交变更：先按 dev-workflow S4 提交，再发版"; exit 1; }

git tag -l "v$VER" | grep -q "^v$VER$" && { echo "[错误] tag v$VER 已存在"; exit 1; }

grep -q "## v$VER" docs/CHANGELOG.md || echo "[warn] docs/CHANGELOG.md 没有 v$VER 小节，建议先补条目再发版"

echo "==> [1/3] 本地预检（只读检查，不含发布动作）"
scripts/dev-verify.sh --fast

echo "==> [2/3] 打 annotated tag v$VER 并推送到 $REMOTE"
git tag -a "v$VER" -m "$NOTE"
if ! git push "$REMOTE" main --follow-tags; then
  git tag -d "v$VER"
  echo "[错误] push 失败，已回滚本地 tag，未发布任何内容"
  exit 1
fi

echo "==> [3/3] 已推送。CI 将产出："
echo "  - CardGame-$VER-windows-x64.exe（安装包）+ portable zip"
echo "  - macOS .dmg + Linux .deb"
echo "  - 以上进 GitHub Release：tag v$VER"
echo "  进度可在仓库 Actions 页面查看。"
