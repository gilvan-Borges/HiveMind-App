package dev.hivemind.mobile.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApprovalActionsTest {
    @Test
    fun approveAndDenyMapOnlyKnownActions() {
        assertEquals(true, ApprovalActions.decision(ApprovalActions.APPROVE, "ap-1"))
        assertEquals(false, ApprovalActions.decision(ApprovalActions.DENY, "ap-1"))
        assertNull(ApprovalActions.decision(ApprovalActions.APPROVE, " "))
        assertNull(ApprovalActions.decision("outro", "ap-1"))
    }
}
