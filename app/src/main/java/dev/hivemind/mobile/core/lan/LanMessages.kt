package dev.hivemind.mobile.core.lan

object LanMessages {
    const val REBIND =
        "O pareamento em claro não funciona mais. Escaneie o QR novo da IDE."
    const val EXPIRED =
        "Esta oferta já venceu ou já foi usada. Gere um novo QR na IDE."
    const val RATE_LIMITED =
        "Muitas tentativas de pareamento. Espere 15 minutos e tente de novo."
    const val TOKEN_QR =
        "Esse QR traz o token na URL e não é mais aceito. Gere um QR novo na IDE."
    const val BAD_QR =
        "QR inválido — aponte para o QR exibido na IDE (App Mobile)."
    const val NO_URL =
        "Nenhuma URL respondeu (verifique Tailscale/Wi-Fi e o Bridge na IDE)."
    const val BAD_RESPONSE =
        "Não foi possível abrir a resposta do PC. Gere um QR novo na IDE."
    const val SEQ =
        "Não foi possível sincronizar a sessão com o PC. Tente de novo."
    const val BAD_OFFER =
        "Não foi possível concluir o pareamento. Gere um QR novo e tente de novo."
}
