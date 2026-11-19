package dev.hivemind.mobile.core.connect

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.hivemind.mobile.HiveMindApp

/**
 * Serviço em primeiro plano (dataSync) que mantém o processo e a conexão SSE
 * vivos com o PC host enquanto o app está em segundo plano — reconexão
 * resiliente é responsabilidade do SseStreamManager.
 */
class ConnectivityService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as HiveMindApp
        val text = app.container.hubState.pairing.value
            ?.let { "Conectado ao PC: ${it.host.ifBlank { "HiveMind IDE" }} (${it.routeLabel})" }
            ?: "Bridge do PC conectado"
        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            app.container.notifier.serviceNotification(text),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val FOREGROUND_ID = 101

        fun start(context: Context) {
            val intent = Intent(context, ConnectivityService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectivityService::class.java))
        }
    }
}
