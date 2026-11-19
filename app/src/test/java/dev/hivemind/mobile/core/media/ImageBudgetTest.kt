package dev.hivemind.mobile.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageBudgetTest {

    @Test
    fun `arquivo de 9 MiB nao cabe no quadro depois do base64`() {
        assertFalse(ImageBudget.fits(9 * 1024 * 1024))
    }

    @Test
    fun `arquivo de 1 MiB cabe sozinho`() {
        assertTrue(ImageBudget.fits(1024 * 1024))
    }

    @Test
    fun `o segundo anexo respeita o que ja esta no pedido`() {
        val first = ImageBudget.base64Size(6 * 1024 * 1024)
        assertFalse(ImageBudget.fits(6 * 1024 * 1024, alreadyBase64 = first))
        assertTrue(ImageBudget.fits(200_000, alreadyBase64 = first))
    }
}
