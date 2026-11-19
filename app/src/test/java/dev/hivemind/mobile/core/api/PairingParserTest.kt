package dev.hivemind.mobile.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingParserTest {

    @Test
    fun `parse QR uri com duas urls token e host`() {
        val raw = "hivemind://pair?urls=http://100.101.102.103:47890,http://192.168.0.15:47890&token=abc123&host=meu-pc"
        val parsed = PairingParser.parse(raw)
        assertNotNull(parsed)
        val (urls, token, host) = parsed!!
        assertEquals(2, urls.size)
        assertEquals("http://100.101.102.103:47890", urls[0])
        assertEquals("http://192.168.0.15:47890", urls[1])
        assertEquals("abc123", token)
        assertEquals("meu-pc", host)
    }

    @Test
    fun `parse rejeita uri estranha`() {
        assertNull(PairingParser.parse("http://example.com"))
        assertNull(PairingParser.parse("hivemind://pair?"))
        assertNull(PairingParser.parse("hivemind://pair?urls=http://a&token="))
    }

    @Test
    fun `parse tolera espacos e url vazia na lista`() {
        val parsed = PairingParser.parse("hivemind://pair?urls=http://a:1,, http://b:2 &token=t")
        assertNotNull(parsed)
        assertEquals(listOf("http://a:1", "http://b:2"), parsed!!.first)
    }
}
