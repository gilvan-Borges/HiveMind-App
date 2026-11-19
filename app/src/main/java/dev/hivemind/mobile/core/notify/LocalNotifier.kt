package dev.hivemind.mobile.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.hivemind.mobile.MainActivity
import dev.hivemind.mobile.R
import dev.hivemind.mobile.core.models.ApprovalInfo
import dev.hivemind.mobile.core.models.LaneSnapshot

/** Notificações locais: aprovações pendentes e término de equipe. */
class LocalNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_EVENTS = "hivemind_events"
        const val CHANNEL_SERVICE = "hivemind_service"
    }

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val events = NotificationChannel(
                CHANNEL_EVENTS, "Eventos do HiveMind", NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Aprovações pendentes e término de equipes" }
            val service = NotificationChannel(
                CHANNEL_SERVICE, "Conexão em segundo plano", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Mantém o app conectado ao PC" }
            manager.createNotificationChannels(listOf(events, service))
        }
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notifyApproval(info: ApprovalInfo) {
        if (!canPost()) return
        val text = if (info.prompt.length > 120) info.prompt.take(120) + "…" else info.prompt
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Aprovação pendente — HiveMind")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.prompt))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        NotificationManagerCompat.from(context).notify("approval", info.approvalId.hashCode(), notification)
    }

    fun notifyFinished(snapshot: LaneSnapshot) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Equipe concluiu — ${snapshot.teamName.ifBlank { "HiveMind" }}")
            .setContentText(
                if (snapshot.lastError.isNotBlank()) snapshot.lastError
                else "Execução finalizada · ${formatTokens(snapshot.turnTokens)} tokens no turno",
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        NotificationManagerCompat.from(context).notify("finish", snapshot.laneId.hashCode(), notification)
    }

    fun serviceNotification(text: String): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("HiveMind Mobile")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun formatTokens(tokens: Long): String {
        if (tokens >= 1_000_000) return "%.1fM".format(tokens / 1_000_000.0)
        if (tokens >= 1_000) return "%.1fk".format(tokens / 1_000.0)
        return tokens.toString()
    }
}
