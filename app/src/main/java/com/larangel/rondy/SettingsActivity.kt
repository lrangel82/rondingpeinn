package com.larangel.rondy

import DataRawRondin
import MySettings
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.larangel.rondy.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView

class SettingsActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        mySettings=MySettings(applicationContext)
        dataRaw = DataRawRondin(applicationContext,CoroutineScope(Dispatchers.IO))

        val parkingSlots = dataRaw?.getParkingSlots()

        readConfig()

        val btnCancel: Button = findViewById(R.id.btnCancelarConf)
        val btnGuardar: Button = findViewById(R.id.btnGuardarConf)
        val btnParkignSlots: Button = findViewById(R.id.btnParkingSlotsConf)
        val btnAddtags: ImageButton = findViewById(R.id.btnAddTagsConf)

        btnParkignSlots.text = "Parking Slots: ${parkingSlots?.count()}"

        btnGuardar.setOnClickListener{
            salvarConfig()
            this.finish()
            ///startActivity(Intent(this, ProgramarTags::class.java ))
        }
        btnCancel.setOnClickListener{
            //startActivity(Intent(this, ProgramarTags::class.java ))
            this.finish()
        }
        btnParkignSlots.setOnClickListener{
            startActivity(Intent(this, ParkingSlotsActivity::class.java ))
            this.finish()
        }
        btnAddtags.setOnClickListener {
            startActivity(Intent(this, ProgramarTags::class.java ))
            //this.finish()
        }

        val yaVioAyuda = mySettings?.getBoolean("ayuda_settings_activity", false)
        if (yaVioAyuda == false) {
            mostrarAyuda()
            mySettings?.saveBoolean("ayuda_settings_activity", true) // Lo marcamos como visto
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
//        val txtBucketName: EditText = findViewById(R.id.txtBucketName)
//        val txtRegionStr: EditText = findViewById(R.id.txtRegionStr)
        val txtCodigoActivacion: EditText = findViewById(R.id.txtCodigoActivacion)
        val txtPwdPermisos: EditText = findViewById(R.id.txtPwdPermisos)

        //Config data
        val _numTags            = mySettings?.getInt("rondin_num_tags",22).toString()
//        val _bucketName         = mySettings?.getString("BUCKET_NAME","")
//        val _regionStr          = mySettings?.getString("REGION_STR","")
        val _codigoActivacion   = mySettings?.getString("CODIGO_ACTIVACION","")
        val _pwdPermisos        = mySettings?.getString("PASSWORD_PERMISOS","")

        txtNumTags.setText( _numTags )
//        txtBucketName.setText(_bucketName)
//        txtRegionStr.setText(_regionStr)
        txtCodigoActivacion.setText(_codigoActivacion)
        txtPwdPermisos.setText(_pwdPermisos)

    }

    fun salvarConfig(){
        //UI objects
        val txtNumTags: EditText = findViewById(R.id.txtNumTags)
//        val txtBucketName: EditText = findViewById(R.id.txtBucketName)
//        val txtRegionStr: EditText = findViewById(R.id.txtRegionStr)
        val txtCodigoActivation: EditText = findViewById(R.id.txtCodigoActivacion)
        val txtPwdPermisos: EditText = findViewById(R.id.txtPwdPermisos)
        val bucketName ="luisrangelapps"
        val region="us-east-2"

        mySettings?.saveInt("rondin_num_tags",txtNumTags.text.toString().toInt())
//        mySettings?.saveString("BUCKET_NAME", txtBucketName.text.toString())
//        mySettings?.saveString("REGION_STR", txtRegionStr.text.toString())
        mySettings?.saveString("BUCKET_NAME", bucketName)
        mySettings?.saveString("REGION_STR", region)
        mySettings?.saveString("CODIGO_ACTIVACION", txtCodigoActivation.text.toString())
        mySettings?.saveString("PASSWORD_PERMISOS", txtPwdPermisos.text.toString())
        mySettings?.saveInt("DIA_VALIDADO_CODIGO", 0)

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
                //Limpiar si o si la cache
                mySettings?.cleanPreferenceS3Config()
                //Buscar y descargar nueva configuracion
                mySettings?.fetchAndProcessS3Config(
                    bucketName,
                    region,
                    txtCodigoActivation.text.toString()
                )
                //Inizializa el ENUM con los valores correctos del nombre de sheets
                SheetTable.initializeAll(mySettings)
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

    }

    fun mostrarAyuda() {
        val txtCodigoAct = findViewById<EditText>(R.id.txtCodigoActivacion)

        TapTargetView.showFor(this,
            TapTarget.forView(txtCodigoAct, "¡Activa tu aplicacion!", "Ingresa cualquier valor para no regresar a esta pantalla, si tienes un codigo de activacion proporcionado por el programador usalo aqui y podras usar todas las funciones.")
                // Personalización con los colores de Rondy
                .outerCircleColor(R.color.teal_700)      // El color de fondo del círculo grande
                .targetCircleColor(R.color.white)         // El color que rodea al botón
                .titleTextSize(24)                        // Tamaño del título
                .descriptionTextSize(16)                  // Tamaño de la descripción
                .textColor(R.color.white)                 // Color del texto
                .drawShadow(true)                         // Sombra para profundidad
                .cancelable(false)                        // No se cierra si tocan fuera
                .tintTarget(true)                         // Mantiene el color del botón original
                .transparentTarget(false),                // El botón se ve sólido dentro del círculo
            object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView?) {
                    super.onTargetClick(view)
                    // Aquí puedes ejecutar la acción del botón o cerrar la guía
                }
            }
        )
    }





    // Utilidad simple para detectar red
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetworkInfo
        return network?.isConnected == true
    }
}