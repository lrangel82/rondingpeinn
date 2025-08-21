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
        val btnParkignSlots: Button = findViewById(R.id.btnParkingSlotsConf)
        btnGuardar.setOnClickListener{
            val txtNumTags: EditText = findViewById(R.id.txtNumTags)
            val txtMapToken: EditText = findViewById(R.id.txtMapToken)
            val txtSheetPermisos: EditText = findViewById(R.id.txtPermisosSheet)
            val txtRegistroCarrosSheetID: EditText = findViewById(R.id.txtRegistroCarrosID)
            val txtParkingSheetID: EditText = findViewById(R.id.txtParkingSheetID)
            mySettings?.saveInt("rondin_num_tags",txtNumTags.text.toString().toInt())
            mySettings?.saveString("rondin_map_token",txtMapToken.text.toString())
            mySettings?.saveString("url_googlesheet_permisos", txtSheetPermisos.text.toString())
            mySettings?.saveString("REGISTRO_CARROS_SPREADSHEET_ID", txtRegistroCarrosSheetID.text.toString())
            mySettings?.saveString("PARKING_SPREADSHEET_ID", txtParkingSheetID.text.toString())
            startActivity(Intent(this, ProgramarTags::class.java ))
        }
        btnCancel.setOnClickListener{
            startActivity(Intent(this, ProgramarTags::class.java ))
        }
        btnParkignSlots.setOnClickListener{
            startActivity(Intent(this, ParkingSlotsActivity::class.java ))
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
        val txtSheetPermisos: EditText = findViewById(R.id.txtPermisosSheet)
        val txtRegistroCarrosSheetID: EditText = findViewById(R.id.txtRegistroCarrosID)
        val txtParkingSheetID: EditText = findViewById(R.id.txtParkingSheetID)
        txtNumTags.setText( mySettings?.getInt("rondin_num_tags",22).toString() )
        txtMapToken.setText(mySettings?.getString("rondin_map_token",""))
        txtSheetPermisos.setText(mySettings?.getString("url_googlesheet_permisos",""))
        txtRegistroCarrosSheetID.setText(mySettings?.getString("REGISTRO_CARROS_SPREADSHEET_ID","13rBJRlnD1qE1qe1dqbytn0zpZggo4uzW4-SQMHRz0cM"))
        txtParkingSheetID.setText(mySettings?.getString("PARKING_SPREADSHEET_ID","1cTuxxmZlPArfLXg0sR8fG0-rsTLOBRx-1AEeufiKz9M"))
    }
}