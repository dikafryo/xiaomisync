package kr.xiaomisync.device

/**
 * 앱에서 연결할 수 있는 샤오미 기기 목록.
 *
 * 새 기기를 추가하려면:
 *  1) 아래 목록에 항목을 하나 추가하고 (available = true)
 *  2) 해당 기기 전용 화면을 만든 뒤 MainActivity의 DeviceScreen()에 연결하면 된다.
 */
enum class DeviceType(
    val displayName: String,
    val model: String,
    val description: String,
    val available: Boolean,
    /** 블루투스 광고 이름이 이 글자로 시작하면 이 기기로 본다 */
    val advertisedNamePrefix: String,
) {
    LYWSD02(
        displayName = "블루투스 디지털 시계",
        model = "LYWSD02",
        description = "시간 맞추기 · 온도/습도 · 배터리 확인",
        available = true,
        advertisedNamePrefix = "LYWSD02",
    ),
    LYWSD03MMC(
        displayName = "블루투스 온습도계 2",
        model = "LYWSD03MMC",
        description = "온도/습도 · 배터리 확인",
        available = false,
        advertisedNamePrefix = "LYWSD03MMC",
    ),
    MHO_C303(
        displayName = "전자잉크 시계",
        model = "MHO-C303",
        description = "시간 맞추기 · 온도/습도 확인",
        available = false,
        advertisedNamePrefix = "MHO-C303",
    ),
    MI_SCALE(
        displayName = "체중계",
        model = "XMTZC",
        description = "체중 기록 읽기",
        available = false,
        advertisedNamePrefix = "MI SCALE",
    );

    fun matches(advertisedName: String): Boolean =
        advertisedName.startsWith(advertisedNamePrefix, ignoreCase = true)
}
