package kr.xiaomisync.kettle

/**
 * 미 주전자 인증에 쓰는 암호 (Mi Home 네이티브 라이브러리를 역분석한 것).
 * 출처: https://github.com/aprosvetova/xiaomi-kettle , 검증본 https://github.com/drndos/mikettle (mikettle.py)
 * 구조는 RC4 와 같다: [cipher] = 키로 순열 만들기 + 순열로 XOR.
 */
object KettleCipher {

    /** "AA:BB:CC:DD:EE:FF" → [FF, EE, DD, CC, BB, AA] */
    fun reversedMac(address: String): ByteArray =
        address.split(":").reversed().map { it.toInt(16).toByte() }.toByteArray()

    fun mixA(mac: ByteArray, productId: Int): ByteArray = byteArrayOf(
        mac[0], mac[2], mac[5], productId.toByte(), productId.toByte(), mac[4], mac[5], mac[1],
    )

    fun mixB(mac: ByteArray, productId: Int): ByteArray = byteArrayOf(
        mac[0], mac[2], mac[5], (productId shr 8).toByte(), mac[4], mac[0], mac[5], productId.toByte(),
    )

    fun cipher(key: ByteArray, input: ByteArray): ByteArray {
        val perm = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + perm[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            perm[i] = perm[j].also { perm[j] = perm[i] }
        }
        var index1 = 0
        var index2 = 0
        return ByteArray(input.size) { i ->
            index1 = (index1 + 1) and 0xFF
            index2 = (index2 + perm[index1]) and 0xFF
            perm[index1] = perm[index2].also { perm[index2] = perm[index1] }
            val k = perm[(perm[index1] + perm[index2]) and 0xFF]
            ((input[i].toInt() and 0xFF) xor k).toByte()
        }
    }
}
