package com.larangel.rondingpeinn

import MySettings
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        mySettings=MySettings(this)

        readConfig()

        val btnCancel: Button = findViewById(R.id.btnCancelarConf)
        val btnGuardar: Button = findViewById(R.id.btnGuardarConf)
        btnGuardar.setOnClickListener{
            val txtNumTags: EditText = findViewById(R.id.txtNumTags)
            val txtMapToken: EditText = findViewById(R.id.txtMapToken)
            mySettings?.saveInt("rondin_num_tags",txtNumTags.text.toString().toInt())
            mySettings?.saveString("rondin_map_token",txtMapToken.text.toString())
            startActivity(Intent(this, ProgramarTags::class.java ))
        }
        btnCancel.setOnClickListener{
            startActivity(Intent(this, ProgramarTags::class.java ))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    fun readConfig(){
        val txtNumTags: EditText = findViewById(R.id.txtNumTags)
        val txtMapToken: EditText = findViewById(R.id.txtMapToken)
        txtNumTags.setText( mySettings?.getInt("rondin_num_tags",22).toString() )
        txtMapToken.setText(mySettings?.getString("rondin_map_token",""))

    }
}