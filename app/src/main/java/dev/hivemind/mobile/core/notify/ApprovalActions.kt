package dev.hivemind.mobile.core.notify

import android.content.Intent

/** Ações da notificação de aprovação. O receiver só age com estes nomes. */
object ApprovalActions {
    const val APPROVE = "dev.hivemind.mobile.action.APPROVE"
    const val DENY = "dev.hivemind.mobile.action.DENY"
    const val EXTRA_ID = "approvalId"
    const val EXTRA_WORKSPACE = "workspace"

    fun decision(action: String?, approvalId: String?): Boolean? {
        if (approvalId.isNullOrBlank()) return null
        return when (action) {
            APPROVE -> true
            DENY -> false
            else -> null
        }
    }

    fun intent(action: String, approvalId: String, workspace: String): Intent =
        Intent(action).apply {
            putExtra(EXTRA_ID, approvalId)
            putExtra(EXTRA_WORKSPACE, workspace)
        }
}
