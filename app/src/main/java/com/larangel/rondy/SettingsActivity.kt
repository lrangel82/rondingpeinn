package com.larangel.rondy

import DataRawRondin
import MySettings
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence

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
        val btnAddtags: ImageButton = findViewById(R.id.btnAddTagsConf)
        val btnAyuda: ImageButton = findViewById(R.id.btnAyuda)
        val btnCtaVehiculos: ImageButton = findViewById(R.id.btnCatalogoVehiculos)
        val btnParkignSlots: ImageButton = findViewById(R.id.btnParkingSlots)

        btnGuardar.setOnClickListener{
            salvarConfig()
            //this.finish()
            ///startActivity(Intent(this, ProgramarTags::class.java ))
        }
        btnCancel.setOnClickListener{
            //startActivity(Intent(this, ProgramarTags::class.java ))
            this.finish()
        }
        btnCtaVehiculos.setOnClickListener {
            startActivity(Intent(this, CatalgoVehiculosActivity::class.java ))
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
        btnAyuda.setOnClickListener {
            mostrarAyuda()
        }

        //Es ADMIN?
        val esAdmin = mySettings?.getInt("ESADMIN",0)
        val layOutConfigAdmin: GridLayout = findViewById(R.id.layoutConfigAdmin)
        if (esAdmin == 1) {
            layOutConfigAdmin.visibility = View.VISIBLE
        }
        else {
            layOutConfigAdmin.visibility = View.GONE
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
        val _numTags            = mySettings?.getInt("rondin_num_tags",0).toString()
//        val _bucketName         = mySettings?.getString("BUCKET_NAME","")
//        val _regionStr          = mySettings?.getString("REGION_STR","")
        val _codigoActivacion   = mySettings?.getString("CODIGO_ACTIVACION","gratis")
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
        val txtCodigoActivation: EditText = findViewById(R.id.txtCodigoActivacion)
        val txtPwdPermisos: EditText = findViewById(R.id.txtPwdPermisos)
        val bucketName ="luisrangelapps"
        val region="us-east-2"

        mySettings?.saveInt("rondin_num_tags",txtNumTags.text.toString().toInt())
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
                        "Connectando al cloud..",
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
                withContext(Dispatchers.Main) {
                    //Inizializa el ENUM con los valores correctos del nombre de sheets
                    SheetTable.initializeAll(mySettings)

                    //Verificarmos si es admin
                    if (mySettings?.getInt("ESADMIN",0) == 1)
                        Toast.makeText(
                            this@SettingsActivity,
                            "Modo ADMIN activado!",
                            Toast.LENGTH_SHORT
                        ).show()
                    //Es activo?
                    if (mySettings?.getInt("APP_ACTIVADA",0) == 0)
                        Toast.makeText(
                            this@SettingsActivity,
                            "VALORES ERRONEOS (App Inactiva)",
                            Toast.LENGTH_LONG
                        ).show()
                    else {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Finalizada SYNC",
                            Toast.LENGTH_LONG
                        ).show()
                        this@SettingsActivity.finish()
                    }

                }
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
        val btnTags = findViewById<ImageButton>(R.id.btnAddTagsConf)
        val txtPassword = findViewById<EditText>(R.id.txtPwdPermisos)

        TapTargetSequence(this)
            .targets(
                TapTarget.forView(btnTags, "¡Programa tu tags!", "Aqui programas los TAGS de NFC que colocaras fisicamente para que puedan ser escaneados por tu rondinero.")
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
                TapTarget.forView(txtCodigoAct, "¡Activa tu app!", "Ingresa tu codigo aqui para activar tu app, o ingresa cualquier valor para el modo gratis con la funcionalidad unica de rondinero.")
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
                TapTarget.forView(txtPassword, "Password para permisos", "Si tienes el password de permisos te permite APROBAR/DENEGAR/ELIMINAR los permisos, si no se proporciona el modo de permisos es el default solo visible los actuales y aprobados")
                    // Personalización con los colores de Rondy
                    .outerCircleColor(R.color.teal_700)      // El color de fondo del círculo grande
                    .targetCircleColor(R.color.white)         // El color que rodea al botón
                    .titleTextSize(24)                        // Tamaño del título
                    .descriptionTextSize(16)                  // Tamaño de la descripción
                    .textColor(R.color.white)                 // Color del texto
                    .drawShadow(true)                         // Sombra para profundidad
                    .cancelable(false)                        // No se cierra si tocan fuera
                    .tintTarget(true)                         // Mantiene el color del botón original
                    .transparentTarget(false)                // El botón se ve sólido dentro del círculo
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    // Se ejecuta cuando el usuario termina todo el tour
                }
                override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {
                    // Se ejecuta cada que avanza un paso
                }
                override fun onSequenceCanceled(lastTarget: TapTarget?) {
                    // Se ejecuta si el usuario cancela (si es cancelable)
                }
            })
            .start()

//        TapTargetView.showFor(this,
//            TapTarget.forView(txtCodigoAct, "¡Activa tu aplicacion!", "Ingresa cualquier valor para no regresar a esta pantalla, si tienes un codigo de activacion proporcionado por el programador usalo aqui y podras usar todas las funciones.")
//                // Personalización con los colores de Rondy
//                .outerCircleColor(R.color.teal_700)      // El color de fondo del círculo grande
//                .targetCircleColor(R.color.white)         // El color que rodea al botón
//                .titleTextSize(24)                        // Tamaño del título
//                .descriptionTextSize(16)                  // Tamaño de la descripción
//                .textColor(R.color.white)                 // Color del texto
//                .drawShadow(true)                         // Sombra para profundidad
//                .cancelable(false)                        // No se cierra si tocan fuera
//                .tintTarget(true)                         // Mantiene el color del botón original
//                .transparentTarget(false),                // El botón se ve sólido dentro del círculo
//            object : TapTargetView.Listener() {
//                override fun onTargetClick(view: TapTargetView?) {
//                    super.onTargetClick(view)
//                    // Aquí puedes ejecutar la acción del botón o cerrar la guía
//                }
//            }
//        )
    }






    // Utilidad simple para detectar red
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetworkInfo
        return network?.isConnected == true
    }
}