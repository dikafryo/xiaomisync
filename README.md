# 샤오미 기기 연결 (XiaomiSync)

Mi Home 앱 없이 블루투스로 샤오미 기기에 바로 연결하는 안드로이드 앱입니다.
현재 지원
- **LYWSD02 블루투스 디지털 시계** (시간 맞추기, 온도/습도, 배터리, 온도 단위 바꾸기)
- **LYWSDCGQ/01ZM 블루투스 온습도계(둥근형, 이름 `MJ_HT_V1`)** (실시간 온도/습도, 연결 중 최저/최고, 배터리) — 프로토콜 참고 https://github.com/ratcashdev/mitemp
- **MHO-C303 전자잉크 시계** (LYWSD02 와 같은 명령: 시간 맞추기, 온도/습도, 온도 단위 — 없는 항목은 건너뜀)
- **LYWSD03MMC 블루투스 온습도계 2** (실시간 온도/습도, 연결 중 최저/최고, 전압으로 계산한 배터리) — 순정 펌웨어 기준, 참고 https://github.com/JsBergbau/MiTemperature2
- **YM-K1501 스마트 전기주전자** (물 온도·상태, 보온 온도/방식/시간 설정 — 끓이기·보온 시작은 본체 버튼만 가능). 샤오미 인증 필요, 참고 https://github.com/aprosvetova/xiaomi-kettle , https://github.com/drndos/mikettle
- **XMTZC 미 체중계** (연결 없이 광고 0x181D/0x181B 에서 체중 읽기), 해석은 openScale 참고 https://github.com/oliexdev/openScale
- 기기 찾기는 블루투스 이름 또는 샤오미 광고(MiBeacon 0xFE95)의 제품 번호로 한다 → 이름 없이 광고하는 신형(LYWSD02MMC 등)도 목록에 나온다

## 다운로드
**https://device.sw4u.kr** 에서 최신 APK를 받습니다. 고정 링크: `https://device.sw4u.kr/download/xiaomisync-latest.apk`
(사이트: `/var/services/web/sw4u/devide`, vhost `devide.sw4u.kr.conf` — `device`·`devide` 둘 다 받음)

## 배포 — 태그만 push 하면 된다
```bash
git tag -a v1.0.1 -m "바뀐 점 1" -m "바뀐 점 2"   # 태그 메시지의 각 줄 = 릴리스 노트
git push origin main v1.0.1
```
1. GitHub Actions(`.github/workflows/release.yml`)가 **서명 없는** 릴리스 APK를 빌드해 GitHub Release 에 올린다
   - 버전은 태그에서: `v1.2.3` → versionName `1.2.3`, versionCode `10203` (각 자리 0~99). `build.gradle.kts` 는 고치지 않는다
2. vm1 cron 이 5분마다 `tools/sync-release.sh` 실행 → 새 릴리스면 받아서 **vm1 키로 정렬·서명** → `sw4u/devide/download/` 에 올리고
   `data/releases.json`·`xiaomisync-latest.apk` 링크 갱신 (로그: `sync-release.log`)
- 서명 키: `~/.android-keys/xiaomisync/` (저장소·GitHub·웹 밖). **키를 잃거나 바꾸면 이미 설치한 사람은 업데이트할 수 없고 지웠다 다시 깔아야 한다** → 백업 대상
- GitHub Release 의 `*-unsigned.apk` 는 설치용이 아니다. 사용자는 device.sw4u.kr 에서 받는다
- 예비 경로(GitHub 을 거치지 않고 vm1 에서 직접): `./publish.sh 1.0.2 "바뀐 점"`
- 빌드 도구(vm1): `~/android-sdk`, JDK 21, Gradle 8.7(래퍼), AGP 8.5.2. `local.properties` 는 PC마다 다르므로 저장소 제외

## 개발용 빌드(Android Studio)
1. [Android Studio](https://developer.android.com/studio)를 설치합니다.
2. Android Studio에서 **Open** → 이 폴더를 선택합니다.
3. 처음 열면 필요한 파일을 자동으로 내려받습니다(몇 분 걸림). 아래쪽 진행 표시가 끝날 때까지 기다립니다.
4. 휴대폰을 USB로 연결하고(휴대폰의 '개발자 옵션 > USB 디버깅' 켜기) 위쪽 ▶ 버튼을 누르면 설치·실행됩니다.
   - APK 파일만 필요하면: 메뉴 **Build > Build App Bundle(s) / APK(s) > Build APK(s)** → `app/build/outputs/apk/debug/app-debug.apk`
   - 디버그 APK는 PC마다 서명이 달라, device.sw4u.kr 에서 받은 앱 위에 덮어 설치되지 않습니다(지우고 설치).

## 사용 방법
1. 앱을 켜면 **기기 선택** 화면이 나옵니다 → "블루투스 디지털 시계"를 누릅니다.
2. **기기 찾기 시작**을 누릅니다. (처음 한 번 '주변 기기' 권한을 허용)
3. 목록에 나온 `LYWSD02`의 **연결**을 누릅니다.
4. **시계 시간 맞추기**를 누르면 휴대폰 시간으로 시계가 맞춰집니다.

> Mi Home 앱이 같은 시계에 연결되어 있으면 연결이 안 될 수 있습니다. 먼저 Mi Home 앱을 닫아 주세요.

## 안 될 때 — 진단 로그
첫 화면 아래 **진단 로그 보기** → 안 되는 동작을 한 번 더 해 본 뒤 **공유**(또는 복사)로 보낸다.
찾은 기기(이름·주소·제품 번호·서비스 데이터), 연결 상태 코드, 기기의 서비스/특성 목록, 읽기·쓰기·알림 바이트, 화면에 뜬 오류가 시간순으로 남는다.
메모리에만 최근 400줄 (`ble/DiagLog.kt`) — 파일로 저장하거나 밖으로 보내지 않는다. 기기 MAC 주소가 들어 있다.

## 새 기기 추가하는 법 (개발자용)
| 할 일 | 파일 |
|---|---|
| 기기 목록에 추가 (`available = true`) | `device/DeviceType.kt` |
| 기기 전용 명령(UUID, 데이터 형식) | 예: `clock/Lywsd02Client.kt`, `thermo/Lywsd03Client.kt` 참고 |
| 기기 전용 화면 + ViewModel | 시계는 `clock/`, 온습도계는 `thermo/` 공통 화면을 쓰고 클라이언트만 추가 |
| 화면 연결 | `MainActivity.kt`의 `DeviceScreen()` |
| 다운로드 페이지의 지원 기기 표 | `/var/services/web/sw4u/devide/index.php` 의 `$devices` |

블루투스 연결·읽기·쓰기·알림은 `ble/GattSession.kt`가 공통으로 처리하므로, 새 기기는 UUID와 데이터 해석만 작성하면 됩니다.

## 참고
- LYWSD02 프로토콜: https://github.com/h4/lywsd02
