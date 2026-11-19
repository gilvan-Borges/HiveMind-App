package dev.hivemind.mobile.core.media

/**
 * O relay fecha o quadro WebSocket acima de 12 MiB. O corpo vai em base64
 * dentro do JSON, então o arquivo cru precisa caber com folga para o envelope.
 */
object ImageBudget {
    const val FRAME_BYTES = 12 * 1024 * 1024
    const val RESERVE_BYTES = 256 * 1024

    fun base64Size(rawBytes: Int): Int {
        if (rawBytes <= 0) return 0
        return ((rawBytes + 2) / 3) * 4
    }

    /** `alreadyBase64` é o tamanho dos anexos que já estão no pedido. */
    fun fits(rawBytes: Int, alreadyBase64: Int = 0): Boolean {
        if (rawBytes < 0 || alreadyBase64 < 0) return false
        val total = base64Size(rawBytes).toLong() + alreadyBase64.toLong() + RESERVE_BYTES
        return total <= FRAME_BYTES
    }
}
