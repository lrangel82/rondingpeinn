package com.larangel.rondy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AcercadeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_acercade)

        // 1. Mostrar la versión real
        val txtVersion: TextView = findViewById(R.id.txtVersionInfo)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            txtVersion.text = "Versión ${pInfo.versionName} (${pInfo.versionCode})"
        } catch (e: Exception) { e.printStackTrace() }

        // 2. Botón de Soporte (Envía un correo preconfigurado)
        findViewById<Button>(R.id.btnContactSupport).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:luisrangel@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Soporte Técnico - Rondy App")
                putExtra(Intent.EXTRA_TEXT, "Escribe aquí tu duda o problema...")
            }
            startActivity(Intent.createChooser(intent, "Enviar correo a soporte"))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}