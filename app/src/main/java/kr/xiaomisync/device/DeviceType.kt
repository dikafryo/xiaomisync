package kr.xiaomisync.device

import androidx.annotation.DrawableRes
import kr.xiaomisync.R
import kr.xiaomisync.ble.FoundDevice

/**
 * 앱에서 연결할 수 있는 샤오미 기기 목록.
 *
 * 새 기기를 추가하려면:
 *  1) 아래 목록에 항목을 하나 추가하고 (available = true)
 *     기기 그림은 tools/gen_device_art.py 로 res/drawable/device_*.xml 을 만들어 imageRes 에 넣는다
 *  2) 해당 기기 전용 화면을 만든 뒤 MainActivity의 DeviceScreen()에 연결하면 된다.
 */
enum class DeviceType(
    val displayName: String,
    val model: String,
    val description: String,
    val available: Boolean,
    /** 기기 선택 화면에 보이는 기기 그림 */
    @DrawableRes val imageRes: Int,
    /** 블루투스 광고 이름이 이 글자로 시작하면 이 기기로 본다 */
    val advertisedNamePrefix: String,
    /** 샤오미 광고(MiBeacon)의 제품 번호. 이름 없이 광고하는 기기도 이 번호로 알아본다 (출처: Bluetooth-Devices/xiaomi-ble devices.py) */
    val productIds: Set<Int>,
) {
    LYWSD02(
        displayName = "블루투스 디지털 시계",
        model = "LYWSD02",
        description = "시간 맞추기 · 온도/습도 · 배터리 확인",
        available = true,
        imageRes = R.drawable.device_lywsd02,
        advertisedNamePrefix = "LYWSD02",
        productIds = setOf(0x045B, 0x16E4, 0x2542),
    ),
    LYWSDCGQ(
        displayName = "블루투스 온습도계",
        model = "LYWSDCGQ/01ZM",
        description = "실시간 온도/습도 · 최저/최고 · 배터리 확인",
        available = true,
        imageRes = R.drawable.device_lywsdcgq,
        advertisedNamePrefix = "MJ_HT_V1",
        productIds = setOf(0x01AA),
    ),
    LYWSD03MMC(
        displayName = "블루투스 온습도계 2",
        model = "LYWSD03MMC",
        description = "실시간 온도/습도 · 최저/최고 · 배터리 확인",
        available = true,
        imageRes = R.drawable.device_lywsd03mmc,
        advertisedNamePrefix = "LYWSD03MMC",
        productIds = setOf(0x055B),
    ),
    MHO_C303(
        displayName = "전자잉크 시계",
        model = "MHO-C303",
        description = "시간 맞추기 · 온도/습도 확인",
        available = false,
        imageRes = R.drawable.device_mho_c303,
        advertisedNamePrefix = "MHO-C303",
        productIds = setOf(0x06D3),
    ),
    MI_SCALE(
        displayName = "체중계",
        model = "XMTZC",
        description = "체중 기록 읽기",
        available = false,
        imageRes = R.drawable.device_mi_scale,
        advertisedNamePrefix = "MI SCALE",
        productIds = emptySet(),
    );

    fun matches(found: FoundDevice): Boolean =
        found.name.startsWith(advertisedNamePrefix, ignoreCase = true) || found.productId in productIds

    /** 같은 종류 안에서 신형 모델이면 화면에 알려 줄 이름 (예: LYWSD02MMC) */
    fun variantName(found: FoundDevice): String? = when (found.productId) {
        0x16E4, 0x2542 -> "LYWSD02MMC (신형)"
        else -> null
    }
}
