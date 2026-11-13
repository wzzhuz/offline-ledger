#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 准备 Android 签名密钥（release 构建用）
#
# 输入（由 workflow 的 env 段注入 Secrets，缺失时为空串）：
#   RELEASE_KEYSTORE_B64  base64 编码的 keystore
#   STORE_PW              密钥库口令
#   KEY_ALIAS_V           别名
#   KEY_PW                私钥口令（PKCS12 下必须与 STORE_PW 相同）
#
# 输出：
#   stdout：成功时输出 4 行 OFFLINELEDGER_*=value，由调用方追加到 $GITHUB_ENV
#   stderr：人类可读的状态说明
#   退出码：始终 0（缺 Secret 时回退仓库内 debug.keystore，流程继续）
#
# 用法：
#   bash .github/scripts/prepare-signing.sh >> "$GITHUB_ENV"
# ---------------------------------------------------------------------------
set -euo pipefail

log() { echo "$*" >&2; }
emit() { [ -n "${GITHUB_OUTPUT:-}" ] && echo "$1" >> "$GITHUB_OUTPUT"; }

MISSING=""
[ -n "${RELEASE_KEYSTORE_B64:-}" ] || MISSING="$MISSING RELEASE_KEYSTORE_BASE64"
[ -n "${STORE_PW:-}" ]            || MISSING="$MISSING KEYSTORE_PASSWORD"
[ -n "${KEY_ALIAS_V:-}" ]         || MISSING="$MISSING KEY_ALIAS"
[ -n "${KEY_PW:-}" ]              || MISSING="$MISSING KEY_PASSWORD"

if [ -n "$MISSING" ]; then
  log "⚠️  缺少 Secret:$MISSING"
  log "   → 回退使用仓库内的 debug.keystore（与历次构建同签名，可直接覆盖安装）"
  log "   → 仅供个人自用，密钥是公开的，请勿对外分发"
  log "   补齐路径：Settings → Secrets and variables → Actions → Repository secrets"
  emit "using_release=false"
  exit 0
fi

KS_DIR="${RUNNER_TEMP:-/tmp}"
mkdir -p "$KS_DIR"
KS_FILE="$KS_DIR/offline-ledger-release.keystore"
if ! echo "$RELEASE_KEYSTORE_B64" | base64 -d > "$KS_FILE" 2>/dev/null; then
  log "❌ keystore base64 解码失败：RELEASE_KEYSTORE_BASE64 不是合法的 base64"
  log "   → 回退 debug 签名。请重新生成：base64 -w 0 your-release.keystore"
  emit "using_release=false"
  exit 0
fi
if [ ! -s "$KS_FILE" ]; then
  log "❌ 解码后 keystore 为空文件 → 回退 debug 签名"
  emit "using_release=false"
  exit 0
fi

log "✅ 使用 Secrets 中的正式签名密钥"
log "   别名：$KEY_ALIAS_V"
log "   密钥库：$KS_FILE（$(du -h "$KS_FILE" | cut -f1)）"

# PKCS12 只有一把锁：私钥即用 storepass 加密，keytool 会忽略 -keypass。
# 不一致时提前警告，免得签名阶段才报 Cannot recover key。
if [ "$STORE_PW" != "$KEY_PW" ]; then
  log "⚠️  KEY_PASSWORD 与 KEYSTORE_PASSWORD 不一致"
  log "   PKCS12 私钥即用 storepass 加密，两者必须相同，"
  log "   否则签名阶段会失败：Cannot recover key / keystore password was incorrect"
fi

emit "using_release=true"

# 只有这 4 行走 stdout，供调用方追加进 $GITHUB_ENV
echo "OFFLINELEDGER_KEYSTORE_FILE=$KS_FILE"
echo "OFFLINELEDGER_STORE_PASSWORD=$STORE_PW"
echo "OFFLINELEDGER_KEY_ALIAS=$KEY_ALIAS_V"
echo "OFFLINELEDGER_KEY_PASSWORD=$KEY_PW"
