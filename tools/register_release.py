#!/usr/bin/env python3
"""서명된 APK 한 건을 device.sw4u.kr 배포 목록(data/releases.json)에 기록한다.

사용: register_release.py <site_dir> <version> <apk 파일명> [노트 ...]
  - APK 는 미리 <site_dir>/download/<파일명> 에 놓여 있어야 한다.
  - 같은 버전이 있으면 덮어쓴다.
  - download/xiaomisync-latest.apk 를 가장 높은 버전으로 가리키게 한다 (고정 링크용).
publish.sh(vm1 직접 빌드)와 sync-release.sh(GitHub 빌드 수신)가 함께 쓴다.
"""
import hashlib
import json
import os
import sys
from datetime import datetime

LATEST = "xiaomisync-latest.apk"


def version_key(version):
    return tuple(int(part) for part in version.split("."))


def main():
    site_dir, version, file, *notes = sys.argv[1:]
    manifest = os.path.join(site_dir, "data", "releases.json")
    download_dir = os.path.join(site_dir, "download")
    path = os.path.join(download_dir, file)

    data = {"app": "XiaomiSync", "releases": []}
    if os.path.exists(manifest):
        with open(manifest, encoding="utf-8") as f:
            data = json.load(f)

    with open(path, "rb") as f:
        sha256 = hashlib.sha256(f.read()).hexdigest()

    entry = {
        "version": version,
        "file": file,
        "size": os.path.getsize(path),
        "sha256": sha256,
        "publishedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
        "notes": notes,
    }
    data["releases"] = [r for r in data["releases"] if r.get("version") != version] + [entry]

    tmp = manifest + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    os.chmod(tmp, 0o644)
    os.replace(tmp, manifest)

    newest = max(data["releases"], key=lambda r: version_key(r["version"]))
    link_tmp = os.path.join(download_dir, LATEST + ".tmp")
    if os.path.lexists(link_tmp):
        os.remove(link_tmp)
    os.symlink(newest["file"], link_tmp)
    os.replace(link_tmp, os.path.join(download_dir, LATEST))

    print(f"배포 완료: v{version} {file} sha256={sha256} (최신 링크 → {newest['file']})")


if __name__ == "__main__":
    main()
