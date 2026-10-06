#!/usr/bin/env bash
# GitHub(dikafryo/xiaomisync)의 최신 Release 를 확인해, 아직 게시하지 않은 버전이면
# 서명 안 된 APK 를 받아 vm1 키로 정렬·서명한 뒤 device.sw4u.kr 에 게시한다.
#   cron(dikafryo): */5 * * * * /var/services/web/apps/xiaomi/tools/sync-release.sh >> /var/services/web/apps/xiaomi/sync-release.log 2>&1
#   서명 키는 GitHub 에 올리지 않는다 — 여기서만 쓴다.
set -euo pipefail

REPO=dikafryo/xiaomisync
SITE_DIR=/var/services/web/sw4u/devide
KEY_FILE="$HOME/.android-keys/xiaomisync/keystore.properties"
BUILD_TOOLS="$HOME/android-sdk/build-tools/36.0.0"
TOOLS_DIR="$(cd "$(dirname "$0")" && pwd)"

exec 9>"/tmp/xiaomisync-sync.lock"
flock -n 9 || exit 0

log() { echo "[$(date '+%F %T')] $*"; }

[ -f "$KEY_FILE" ] || { log "서명 키가 없습니다: $KEY_FILE"; exit 1; }

# 최신 태그는 API 대신 releases/latest 리다이렉트로 본다 (비인증 API 는 IP당 시간 60회 한도)
LATEST_URL=$(curl -fsS -o /dev/null -w '%{redirect_url}' "https://github.com/$REPO/releases/latest") || {
    log "GitHub 최신 릴리스 확인 실패"; exit 0; }
TAG=${LATEST_URL##*/tag/}
[ "$TAG" != "$LATEST_URL" ] || exit 0   # 릴리스가 아직 없음
VERSION=${TAG#v}
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { log "태그 형식이 아님: $TAG"; exit 1; }

FILE="xiaomisync-$VERSION.apk"
if python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); sys.exit(0 if any(r.get("version")==sys.argv[2] for r in d["releases"]) else 1)' \
    "$SITE_DIR/data/releases.json" "$VERSION" 2>/dev/null; then
    exit 0   # 이미 게시됨
fi

# 새 버전일 때만 API 로 APK 주소·노트(줄 단위)를 가져온다
RELEASE_JSON=$(curl -fsSL -H 'Accept: application/vnd.github+json' "https://api.github.com/repos/$REPO/releases/tags/$TAG") || {
    log "$TAG 릴리스 정보 조회 실패 (API 한도일 수 있음, 다음 주기에 재시도)"; exit 0; }

mapfile -t META < <(python3 -c '
import json, sys
r = json.load(sys.stdin)
assets = [a for a in r.get("assets", []) if a["name"].endswith("-unsigned.apk")]
print(assets[0]["browser_download_url"] if assets else "")
for line in (r.get("body") or "").splitlines():
    if line.strip():
        print(line.strip())
' <<<"$RELEASE_JSON")

URL=${META[0]}
NOTES=("${META[@]:1}")
[ -n "$URL" ] || { log "$TAG 에 *-unsigned.apk 가 없습니다 (빌드 중일 수 있음)"; exit 0; }

log "새 버전 발견: $TAG → 내려받아 서명합니다"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

curl -fsSL -o "$WORK/unsigned.apk" "$URL"
"$BUILD_TOOLS/zipalign" -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"

prop() { sed -n "s/^$1=//p" "$KEY_FILE" | tail -n 1; }
KS_PASS=$(prop storePassword) KEY_PASS=$(prop keyPassword) \
    "$BUILD_TOOLS/apksigner" sign \
        --ks "$(prop storeFile)" --ks-key-alias "$(prop keyAlias)" \
        --ks-pass env:KS_PASS --key-pass env:KEY_PASS \
        --out "$WORK/signed.apk" "$WORK/aligned.apk"
"$BUILD_TOOLS/apksigner" verify "$WORK/signed.apk"

install -m 644 "$WORK/signed.apk" "$SITE_DIR/download/$FILE"
python3 "$TOOLS_DIR/register_release.py" "$SITE_DIR" "$VERSION" "$FILE" "${NOTES[@]}"
log "게시 완료: https://device.sw4u.kr/download/$FILE"
