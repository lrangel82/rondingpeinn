package com.larangel.rondy.ui

import DataRawRondin
import MySettings
import android.app.AlarmManager
import android.os.VibratorManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.larangel.rondy.AlarmReceiver
import com.larangel.rondy.R
import com.larangel.rondy.VehicleSearchActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.LocalTime

class AlarmaActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Despertar pantalla
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setContentView(R.layout.activity_alarma)
        mySettings = MySettings(applicationContext)
        dataRaw = DataRawRondin(applicationContext,CoroutineScope(Dispatchers.IO))

        // Iniciar Ringtone y vibración aquí...
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        // Forzar que el sonido use el canal de Alarma (ignora si el móvil está en silencio o vibración)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.audioAttributes = audioAttributes
        }
        ringtone?.play()

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        // Crear un patrón de vibración (esperar 0ms, vibrar 500ms, esperar 500ms...)
        val pattern = longArrayOf(0, 500, 500)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // -1 para no repetir, 0 para repetir indefinidamente
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            (vibrator?.vibrate(pattern, 0))
        }

        findViewById<Button>(R.id.btnEnteradoAlarma).setOnClickListener {
            detenerSonido()
            startActivity(Intent(this, VehicleSearchActivity::class.java))
            finish()
        }
        val txtAlarma = findViewById<TextView>(R.id.txtAlarma)
        val nombreAlarma = intent.getStringExtra("nombre") ?: ""
        txtAlarma.setText("Es momento de realizar el RONDIN de las: ${LocalTime.now()}hrs   ${nombreAlarma}")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        val checkPoints = mySettings?.getListCheckPoint("LIST_CHECKPOINT")
        val rondinActivo = checkPoints!!.isNotEmpty()
        val reintentos =  mySettings?.getInt("Alarma_reintentos", 0) ?: 0

        // Lógica de los 5 min (Snooze manual si no se inició el rondín)
        if (!rondinActivo && reintentos < 15) {
            reprogramarSnooze( intent.getStringExtra("nombre")!!, reintentos + 1)
        }else{
            mySettings?.saveInt("Alarma_reintentos",0)
        }
    }
    private fun detenerSonido() {
        try {
            ringtone?.stop()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.cancel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun reprogramarSnooze( nombre: String, intentosRealizados: Int) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        mySettings?.saveInt("Alarma_reintentos",intentosRealizados)

        // Crear el Intent apuntando de nuevo al Receiver
        val intent = Intent(applicationContext, AlarmReceiver::class.java).apply {
            putExtra("nombre", nombre)
            putExtra("hora", "99:99")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            999, // ID fijo para que el snooze anterior se sobrescriba si es necesario
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tiempo actual + 5 minutos (en milisegundos)
        val tiempoSnooze = System.currentTimeMillis() + (5 * 60 * 1000)

        // Programar la alarma exacta
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tiempoSnooze, pendingIntent)
            } else {
                // Fallback si no hay permiso de alarmas exactas
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tiempoSnooze, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tiempoSnooze, pendingIntent)
        }

        Toast.makeText(applicationContext, "Alarma pospuesta 5 min (Intento $intentosRealizados/15)", Toast.LENGTH_SHORT).show()
    }
}