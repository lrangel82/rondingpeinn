package com.larangel.rondingpeinn

import DataRawRondin
import MySettings
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.larangel.rondingpeinn.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        mySettings=MySettings(this)
        dataRaw = DataRawRondin(this,CoroutineScope(Dispatchers.IO))

        val parkingSlots = dataRaw?.getParkingSlots()

        readConfig()

        val btnCancel: Button = findViewById(R.id.btnCancelarConf)
        val btnGuardar: Button = findViewById(R.id.btnGuardarConf)
        val btnParkignSlots: Button = findViewById(R.id.btnParkingSlotsConf)

        btnParkignSlots.text = "Parking Slots: ${parkingSlots?.count()}"

        btnGuardar.setOnClickListener{
            salvarConfig()
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
        //UI objects
        val txtNumTags: EditText = findViewById(R.id.txtNumTags)
        val txtBucketName: EditText = findViewById(R.id.txtBucketName)
        val txtRegionStr: EditText = findViewById(R.id.txtRegionStr)
        val txtCodigoActivacion: EditText = findViewById(R.id.txtCodigoActivacion)
//        //val txtMapToken: EditText = findViewById(R.id.txtMapToken)
//        //val txtSheetPermisos: EditText = findViewById(R.id.txtPermisosSheet)
//        val txtRegistroCarrosSheetID: EditText = findViewById(R.id.txtBucketName)
//        val txtParkingSheetID: EditText = findViewById(R.id.txtRegionStr)
//        val txtPermisosSheetID: EditText = findViewById(R.id.txtCodigoActivacion)


        //Config data
        val _numTags            = mySettings?.getInt("rondin_num_tags",22).toString()
        val _bucketName         = mySettings?.getString("BUCKET_NAME","")
        val _regionStr          = mySettings?.getString("REGION_STR","")
        val _codigoActivacion   = mySettings?.getString("CODIGO_ACTIVACION","")

        txtNumTags.setText( _numTags )
        txtBucketName.setText(_bucketName)
        txtRegionStr.setText(_regionStr)
        txtCodigoActivacion.setText(_codigoActivacion)

        //txtMapToken.setText(mySettings?.getString("rondin_map_token",""))
        //txtSheetPermisos.setText(mySettings?.getString("url_googlesheet_permisos",""))
//        txtRegistroCarrosSheetID.setText(mySettings?.getString("REGISTRO_CARROS_SPREADSHEET_ID","13rBJRlnD1qE1qe1dqbytn0zpZggo4uzW4-SQMHRz0cM"))
//        txtParkingSheetID.setText(mySettings?.getString("PARKING_SPREADSHEET_ID","1cTuxxmZlPArfLXg0sR8fG0-rsTLOBRx-1AEeufiKz9M"))
//        txtPermisosSheetID.setText(mySettings?.getString("PERMISOS_SPREADSHEET_ID","1vlbn_jHV5qKLjmZjuMMND1sJYle9fdHmjxSwiAArtos"))
    }

    fun salvarConfig(){
        //UI objects
        val txtNumTags: EditText = findViewById(R.id.txtNumTags)
        val txtBucketName: EditText = findViewById(R.id.txtBucketName)
        val txtRegionStr: EditText = findViewById(R.id.txtRegionStr)
        val txtCodigoActivation: EditText = findViewById(R.id.txtCodigoActivacion)

        mySettings?.saveInt("rondin_num_tags",txtNumTags.text.toString().toInt())
        mySettings?.saveString("BUCKET_NAME", txtBucketName.text.toString())
        mySettings?.saveString("REGION_STR", txtRegionStr.text.toString())
        mySettings?.saveString("CODIGO_ACTIVACION", txtCodigoActivation.text.toString())

        //Procesar la configuracion de la red y la validacion del codigo
        lifecycleScope.launch(Dispatchers.IO) {
            if(isNetworkAvailable()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Sync ACTIVACION",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                mySettings?.fetchAndProcessS3Config(
                    txtBucketName.text.toString(),
                    txtRegionStr.text.toString(),
                    txtCodigoActivation.text.toString()
                )
            }else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "No hay conexion a INTERNET, usando CACHE",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }


//        //mySettings?.saveString("rondin_map_token",txtMapToken.text.toString())
//        //mySettings?.saveString("url_googlesheet_permisos", txtSheetPermisos.text.toString())
//        mySettings?.saveString("REGISTRO_CARROS_SPREADSHEET_ID", txtRegistroCarrosSheetID.text.toString())
//        mySettings?.saveString("PARKING_SPREADSHEET_ID", txtParkingSheetID.text.toString())
//        mySettings?.saveString("PERMISOS_SPREADSHEET_ID", txtPermisosSheetID.text.toString())
    }

    // Utilidad simple para detectar red
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetworkInfo
        return network?.isConnected == true
    }
}