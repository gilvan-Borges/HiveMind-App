package dev.hivemind.mobile.core.api

import dev.hivemind.mobile.core.relay.NaClBox
import dev.hivemind.mobile.core.store.PairingStore
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingParserTest {

    @Test
    fun `parse QR v2 no formato do bridge`() {
        val raw = "hivemind://pair?exp=1900000000&host=meu-pc&offer=AAAAAAAAAAAAAAAAAAAAAA&pk=BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB&urls=http%3A%2F%2F100.101.102.103%3A47890%2Chttp%3A%2F%2F192.168.0.15%3A47890&v=2"
        val parsed = PairingParser.parse(raw) as PairQr.Offer
        assertEquals(listOf("http://100.101.102.103:47890", "http://192.168.0.15:47890"), parsed.urls)
        assertEquals("AAAAAAAAAAAAAAAAAAAAAA", parsed.offer)
        assertEquals("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", parsed.pk)
        assertEquals(1_900_000_000L, parsed.exp)
        assertEquals("meu-pc", parsed.host)
        assertEquals(16, NaClBox.decodeFlexible(parsed.offer).size)
        assertEquals(32, NaClBox.decodeFlexible(parsed.pk).size)
    }

    @Test
    fun `parse aceita query sem encode e host opcional`() {
        val pk = NaClBox.encodeKey(ByteArray(32) { 7 })
        val offer = NaClBox.encodeKey(ByteArray(16) { 3 })
        val raw = "hivemind://pair?v=2&urls=http://10.0.0.8:47890,, http://192.168.0.15:47890&offer=$offer&pk=$pk&exp=1900000000"
        val parsed = PairingParser.parse(raw) as PairQr.Offer
        assertEquals(listOf("http://10.0.0.8:47890", "http://192.168.0.15:47890"), parsed.urls)
        assertEquals("", parsed.host)
    }

    @Test
    fun `recusa token na query inclusive em v2`() {
        assertTrue(PairingParser.parse("hivemind://pair?urls=http://10.0.0.8:47890&token=abc123&host=pc") is PairQr.RejectedToken)
        val v2 = goPairUri() + "&token=abc"
        assertTrue(PairingParser.parse(v2) is PairQr.RejectedToken)
        assertTrue(PairingParser.parse("hivemind://pair?v=2&token=&urls=http://a") is PairQr.Invalid)
    }

    @Test
    fun `rejeita uri estranha oferta curta e exp invalido`() {
        assertTrue(PairingParser.parse("http://example.com") is PairQr.Invalid)
        assertTrue(PairingParser.parse("hivemind://pair?") is PairQr.Invalid)
        assertTrue(PairingParser.parse("hivemind://link?relay=https://r&code=ABCD") is PairQr.Invalid)
        val pk = NaClBox.encodeKey(ByteArray(32) { 1 })
        assertTrue(PairingParser.parse("hivemind://pair?v=2&urls=http://a:1&offer=aa&pk=$pk&exp=10") is PairQr.Invalid)
        val offer = NaClBox.encodeKey(ByteArray(16) { 1 })
        assertTrue(PairingParser.parse("hivemind://pair?v=2&urls=http://a:1&offer=$offer&pk=$pk&exp=0") is PairQr.Invalid)
        assertTrue(PairingParser.parse("hivemind://pair?v=2&urls=&offer=$offer&pk=$pk&exp=10") is PairQr.Invalid)
    }

    @Test
    fun `datastore do pareamento nao tem chave de token`() {
        assertFalse(PairingStore.persistedNames.contains("token"))
        assertFalse(PairingStore.persistedNames.contains("privateKey"))
        assertFalse(PairingStore.persistedNames.contains("boxPub"))
        assertTrue(PairingStore.persistedNames.contains("device_id"))
        assertTrue(PairingStore.persistedNames.contains("active_url"))
    }

    private fun goPairUri(): String {
        val q = listOf(
            "exp" to "1900000000",
            "host" to "meu-pc",
            "offer" to "AAAAAAAAAAAAAAAAAAAAAA",
            "pk" to "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
            "urls" to "http://100.101.102.103:47890,http://192.168.0.15:47890",
            "v" to "2",
        ).joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "hivemind://pair?$q"
    }
}
