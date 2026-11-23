package dev.hivemind.mobile.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dev.hivemind.mobile.HiveMindApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Aprovar ou negar a partir da notificação. O processo sobe com o
 * [HiveMindApp] e espera o túnel já pareado ficar pronto.
 */
class ApprovalActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ApprovalActions.EXTRA_ID).orEmpty()
        val approved = ApprovalActions.decision(intent.action, id) ?: return
        val pending = goAsync()
        val app = context.applicationContext as? HiveMindApp
        if (app == null) {
            pending.finish()
            return
        }
        app.container.appScope.launch {
            try {
                val api = app.container.api
                var tries = 0
                while (!api.isConfigured && tries < 20) {
                    delay(250)
                    tries++
                }
                if (!api.isConfigured) return@launch
                runCatching { api.resolveApproval(id, approved) }
                    .onSuccess {
                        app.container.hubState.onApprovalResolved(id)
                        NotificationManagerCompat.from(context).cancel("approval", id.hashCode())
                    }
            } finally {
                pending.finish()
            }
        }
    }
}
