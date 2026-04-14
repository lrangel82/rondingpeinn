package com.larangel.rondy

import MySettings
import android.app.NotificationChannel
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.larangel.rondy.ui.AlarmaActivity

class AlarmReceiver : BroadcastReceiver() {
    private var mySettings: MySettings? = null

    //            // Lanzar el Activity de Alarma
//            val intentAlarma = Intent(context, AlarmaActivity::class.java).apply {
//                putExtra("nombre", nombre)
//                putExtra("hora", hora)
//                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
//            }
//            context.startActivity(intentAlarma)
    override fun onReceive(context: Context, intent: Intent) {

        mySettings = MySettings(context)
        val isRondinActivo = mySettings?.getListCheckPoint("LIST_CHECKPOINT")?.isNotEmpty()
        val nombre = intent.getStringExtra("nombre") ?: "Rondín"
        val hora = intent.getStringExtra("hora") ?: "--:--"

        if (!isRondinActivo!!) {

            val canalId = "alarmas_rondy"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Crear el canal de notificación (necesario para Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val canal = NotificationChannel(canalId, "Alarmas Rondín", NotificationManager.IMPORTANCE_HIGH)
                notificationManager.createNotificationChannel(canal)
            }

            // Configurar el Intent que abrirá tu Activity
            val intentAlarma = Intent(context, AlarmaActivity::class.java).apply {
                putExtra("nombre", nombre)
                putExtra("hora", hora)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intentAlarma,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Construir la notificación con fullScreenIntent
            val builder = NotificationCompat.Builder(context, canalId)
                .setSmallIcon(R.drawable.notification_alarm)
                .setContentTitle("¡Hora de Rondín!")
                .setContentText("Rony--> DEBE INICIAR RONDIN: $nombre")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true) // ESTA ES LA CLAVE
                .setAutoCancel(true)

            notificationManager.notify(1, builder.build())
        }
    }
}