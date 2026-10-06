#!/usr/bin/env bash
# (예비 경로) GitHub 을 거치지 않고 vm1 에서 직접 빌드해 device.sw4u.kr 에 올린다.
#   평소 배포는 태그 push → GitHub Actions 빌드 → tools/sync-release.sh 가 자동 게시 (README 참고)
#   사용: ./publish.sh 1.2.3 "변경 내용 1" "변경 내용 2" ...
#   - 같은 버전을 다시 올리면 파일과 목록 항목을 덮어쓴다.
#   - 서명 키: ~/.android-keys/xiaomisync/ (없으면 중단 — 키가 바뀌면 기존 설치본이 업데이트되지 않는다)
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
SITE_DIR=/var/services/web/sw4u/devide
KEY_FILE="$HOME/.android-keys/xiaomisync/keystore.properties"

[ -f "$KEY_FILE" ] || { echo "서명 키가 없습니다: $KEY_FILE" >&2; exit 1; }

VERSION=${1:-}
[[ "$VERSION" =~ ^[0-9]{1,2}\.[0-9]{1,2}\.[0-9]{1,2}$ ]] || { echo "사용: $0 1.2.3 \"변경 내용\" ..." >&2; exit 1; }
shift

cd "$APP_DIR"
./gradlew assembleRelease --no-daemon -q -PappVersion="$VERSION"

APK="$APP_DIR/app/build/outputs/apk/release/app-release.apk"
"$HOME/android-sdk/build-tools/36.0.0/apksigner" verify "$APK"

FILE="xiaomisync-$VERSION.apk"
install -m 644 "$APK" "$SITE_DIR/download/$FILE"
python3 "$APP_DIR/tools/register_release.py" "$SITE_DIR" "$VERSION" "$FILE" "$@"
