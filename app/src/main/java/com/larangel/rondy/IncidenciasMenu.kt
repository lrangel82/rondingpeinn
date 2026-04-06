package com.larangel.rondy

import DataRawRondin
import MySettings
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

class IncidenciasMenu : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null

    private lateinit var FechaIncidencias : LocalDate
    private lateinit var radioAntier: RadioButton
    private lateinit var radioAyer: RadioButton
    private lateinit var radioHoy: RadioButton

    private lateinit var currentPhotoPath: Uri
    private var currentTipo: String? = null
    private var currentDescripcion: String? = null
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_LOCATION_PERMISSION = 101
    private val REQUEST_IMAGE_CAPTURE = 102
    private val REQUEST_IMAGE_PICK = 103
    private val REQUEST_STORAGE_PERMISSION = 104

    private val arrayBotonesIncidencias: ArrayList<Button>? = ArrayList<Button>()
    private var indexBtnClicked: Int = -1
    private var configuraIncidencias: List<List<String>>? = listOf()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_incidencias_menu)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mySettings = MySettings(applicationContext)
        dataRaw = DataRawRondin(applicationContext,CoroutineScope(Dispatchers.IO))

        //Configuracion incidencia
        configuraIncidencias = dataRaw?.getIncidenciasConfig() as List<List<String>>?

        // Initialize UI components
        radioAntier                 = findViewById(R.id.radioButtonDayAntier)
        radioAyer                   = findViewById(R.id.radioButtonDayAyer)
        radioHoy                    = findViewById(R.id.radioButtonDayHoy)

        FechaIncidencias = LocalDate.now()
        radioHoy.isChecked = true
        radioAntier.setOnClickListener {
            FechaIncidencias = LocalDate.now().minusDays(2)
            loadIncidencias()
        }
        radioAyer.setOnClickListener {
            FechaIncidencias = LocalDate.now().minusDays(1)
            loadIncidencias()
        }
        radioHoy.setOnClickListener {
            FechaIncidencias = LocalDate.now()
            loadIncidencias()
        }

        //Agregar Botones
//        val contenedor = findViewById<LinearLayout>(R.id.layoutBotonesIncidencias)
//        configuraIncidencias?.forEachIndexed { index, rowIncidencia ->
//            val nuevoBoton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
//            nuevoBoton.text = rowIncidencia[1].toString()
//            nuevoBoton.id = index
//            // Configura los parámetros de diseño (LayoutParams)
//            val layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.MATCH_PARENT, // Ancho
//                LinearLayout.LayoutParams.WRAP_CONTENT // Alto
//            )
//            layoutParams.setMargins(16, 8, 16, 8) // Márgenes (izquierda, arriba, derecha, abajo)
//            nuevoBoton.layoutParams = layoutParams
//            nuevoBoton.setOnClickListener {
//                indexBtnClicked = index
//                showOpcionesDialog(rowIncidencia[0].toString())
//            }
//            // Agrega el botón al contenedor
//            contenedor.addView(nuevoBoton)
//            arrayBotonesIncidencias!!.add(nuevoBoton)
//        }

        findViewById<Button>(R.id.btnBackIncidenciasMenu).setOnClickListener { finish() }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayoutIncidencias)
        swipeRefreshLayout.setOnRefreshListener {
            loadIncidencias(true)
        }

        loadIncidencias()
    }

    private fun crearBotonesIncidenciaDinamicos(){
        val contenedor = findViewById<GridLayout>(R.id.contenedorBotones)
        contenedor.removeAllViews()
        arrayBotonesIncidencias!!.clear()

        // Configurar columnas según la orientación
        val orientacion = resources.configuration.orientation
        if (orientacion == Configuration.ORIENTATION_LANDSCAPE) {
            contenedor.columnCount = 3 // Máximo 3 columnas en horizontal
        } else {
            contenedor.columnCount = 1 // 1 columna (lista vertical) en retrato
        }

        configuraIncidencias?.forEachIndexed { index, rowIncidencia ->
            val nuevoBoton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
            nuevoBoton.text = rowIncidencia[1].toString()
            nuevoBoton.id = index
            nuevoBoton.layoutParams = GridLayout.LayoutParams().apply {
                // Esto hace que el botón use el espacio disponible
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8) // Espaciado entre botones
            }
            nuevoBoton.setOnClickListener {
                indexBtnClicked = index
                showOpcionesDialog(rowIncidencia[0].toString())
            }
            // Agrega el botón al contenedor
            contenedor.addView(nuevoBoton)
            arrayBotonesIncidencias.add(nuevoBoton)
        }
    }

    private fun loadIncidencias(forceLoad: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                swipeRefreshLayout.isRefreshing = true

                configuraIncidencias = dataRaw?.getIncidenciasConfig(forceLoad) as List<List<String>>?
                val totalEventos = dataRaw?.getIncidenciasEventos(forceLoad)?.count() ?: 0

                withContext(Dispatchers.Main) {
                    //Crear Botones
                    crearBotonesIncidenciaDinamicos()

                    //Total por categoria y fecha seleccionada
                    configuraIncidencias?.forEachIndexed { index, rowIncidencia ->
                        val tipo = rowIncidencia[0].toString()
                        val textoBoton = rowIncidencia[1].toString()
                        val eventos = dataRaw?.getIncidenciasEventosTipo(tipo, FechaIncidencias)
                        arrayBotonesIncidencias?.get(index)?.text = "$textoBoton (${eventos?.size})"
                    }
                    //Total por dia
                    val totalhoy = dataRaw?.getIncidenciasEventosDesde(LocalDate.now())?.count() ?: 0
                    val totalayer =
                        dataRaw?.getIncidenciasEventosDesde(LocalDate.now().minusDays(1))?.count() ?: 0
                    val totalanti =
                        dataRaw?.getIncidenciasEventosDesde(LocalDate.now().minusDays(2))?.count() ?: 0
                    radioHoy.text = "Hoy (${totalhoy})"
                    radioAyer.text = "Ayer (${(totalayer - totalhoy).coerceAtLeast(0)})"
                    radioAntier.text = "Antier(${(totalanti - totalayer - totalhoy).coerceAtLeast(0)})"

                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(
                        this@IncidenciasMenu,
                        "Incidencias cargadas: ${totalanti}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(
                        this@IncidenciasMenu,
                        "Error loading Incidencias: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun showOpcionesDialog(tipo: String) {
        val titulo = configuraIncidencias?.get(indexBtnClicked)[1].toString()
        val descripcionIncidencia = configuraIncidencias?.get(indexBtnClicked)[3].toString()
        val builder = AlertDialog.Builder(this)
        builder.setTitle("$titulo")
        builder.setMessage("Reglamento: $descripcionIncidencia")
        builder.setPositiveButton("Nuevo") { _, _ -> solicitarDescripcion(tipo) }
        builder.setNeutralButton("Listado") { _, _ -> mostrarListadoIncidencias(tipo) }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }
    fun solicitarDescripcion(tipo: String) {
        currentTipo = tipo
        val input = EditText(this)
        input.hint = "Descripción corta"
        AlertDialog.Builder(this)
            .setTitle("Nueva Incidencia")
            .setMessage("Escriba una descripción corta")
            .setView(input)
            .setPositiveButton("Tomar foto") { _, _ ->
                currentDescripcion = input.text.toString() ?: "n/a"
                tomarFoto()
            }
            .setNeutralButton("Galeria"){_,_ ->
                currentDescripcion = input.text.toString() ?: "n/a"
                pickFromGallery()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    fun tomarFoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile: File? = createImageFile()
        if (photoFile != null) {
            currentPhotoPath = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoPath)
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            } else {
                AlertDialog.Builder(this)
                    .setTitle("No Camera Available")
                    .setMessage("Do you want to select a photo from the gallery?")
                    .setPositiveButton("Yes") { _, _ ->
                        pickFromGallery()
                    }
                    .setNegativeButton("No") { _, _ -> }
                    .show()
            }
        } else {
            Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show()
        }

    }
    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val storageDir = getExternalFilesDir("photos")
            File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        } catch (e: Exception) {
            println("LARANGEL Error createImageFile: ${e}")
            e.printStackTrace()
            Toast.makeText(this@IncidenciasMenu, "Error createImageFile: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }
    private fun pickFromGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            startGalleryIntent()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_STORAGE_PERMISSION)
        }
    }
    private fun startGalleryIntent() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        } else {
            // Fallback to ACTION_PICK if ACTION_GET_CONTENT fails
            val fallbackIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            if (fallbackIntent.resolveActivity(packageManager) != null) {
                startActivityForResult(fallbackIntent, REQUEST_IMAGE_PICK)
            } else {
                Toast.makeText(this, "No gallery or file picker app available. Please install one.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun solicitarCalleYNumero() {
        var numeroSeleccionado: String? = null
        var calleSeleccionada: String? = null

        //Verificar si la imagen contiene una PLACA valida y si podemos extraer la calle y numero de ahi
        val image = InputImage.fromFilePath(this, currentPhotoPath)
        var placas = obtenerPlacasImagen(image)
        if (placas.length >= 3){
            //##### Buscar Domicilio de las PLACAS #####
            val (calle,numero) = getDireccionFromPlacas(placas)
            if (calle.isNotEmpty() and numero.isNotEmpty()) {
                guardarIncidencia(calle, numero)
                return
            }
        }

        //##### PREGUNTAR por la calle y numero
        val domicilios = dataRaw?.getDomiciliosUbicacion() ?: return
        val calles = domicilios.map { it[0].toString() }.distinct()

        // Seleccionar calle
        AlertDialog.Builder(this)
            .setTitle("Seleccione la Calle")
            .setItems(calles.toTypedArray()) { _, which ->
                calleSeleccionada = calles[which]
                // Filtrar números de la calle seleccionada
                val numeros =
                    domicilios.filter { it[0] == calleSeleccionada }.map { it[1].toString() }
                // Seleccionar número
                AlertDialog.Builder(this)
                    .setTitle("Seleccione el Número")
                    .setItems(numeros.toTypedArray()) { _, numWhich ->
                        numeroSeleccionado = numeros[numWhich]
                        guardarIncidencia(calleSeleccionada, numeroSeleccionado)
                    }
                    .show()
            }
            .show()

    }

    private fun obtenerPlacasImagen(image: InputImage): String{
        var plate=""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                plate = visionText.textBlocks.joinToString(" ") { it.text }.trim()
                var re = Regex("[^A-Za-z0-9 ]")
                plate = re.replace(plate, "") //Eliminar carcteres no deseados
                re = Regex("([A-Z]{3}[0-9]{3,4}[A-Z]?|[0-9]{2}[A-Z][0-9]{3}|[0-9]{3}[A-Z]{3}|[A-Z]{2}[0-9]{4,5}[A-Z]?|[A-Z][0-9]{4}|[A-Z][0-9]{2}[A-Z]{2,3}|[A-Z]{3}[0-9][A-Z]|[A-Z]{5}[0-9]{2})")
                val matchRegult = re.find(plate) //Match Placa
                plate = if (matchRegult != null) {
                    matchRegult.value
                } else {
                    ""
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        return plate
    }
    private fun getDireccionFromPlacas(plate: String): Pair<String, String>{
        try {
            //val vehicles = getCachedVehiclesData()
            var match: List<Any>? = null

            if (plate.toIntOrNull() != null){
                //###### PUEDE SER TAG #########
                val tags = dataRaw?.getTagsCache()
                tags?.forEach { tag->
                    if (tag.size >= 3 && tag[0].toString().equals(plate, ignoreCase = true)) {
                        match = tag
                        return@forEach
                    }
                }
            }
            else {
                //########## ES PLACA ############
                // 1. Buscar coincidencia exacta
                val vehicles = dataRaw?.getCachedVehiclesData()
                if (vehicles != null) {
                    for (row in vehicles) {
                        if (row.size >= 3 && row[0].toString().equals(plate, ignoreCase = true)) {
                            match = row
                            break
                        }
                    }
                }
            }


            if (match != null) {
                return Pair(match[1].toString(), match[2].toString())
            } else {
                return Pair("","")
            }

        } catch (e: Exception) {
            return Pair("","")
        }
    }

    private fun savePhotoLocally(uri: Uri?, tipo: String): String? {
        if (uri == null) return null

        try {
            val msgMapa="GuadalupeInn Incidencias\n$currentTipo: (${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())})\nnotas:$currentDescripcion"
            val inputStream = contentResolver.openInputStream(uri)
            // 1. Copy of bitmap
            var bitmap = BitmapFactory.decodeStream(inputStream)!!.copy(Bitmap.Config.ARGB_8888, true)
            // Requiere escalar?
            if (bitmap.width > 1024){
                val targetWidth = 1024
                val factorscale =targetWidth.toFloat()/bitmap.width.toFloat()
                val targetHeight = (bitmap.height.toFloat() * factorscale).toInt()
                bitmap = bitmap.scale(targetWidth,targetHeight) // Reduced size
            }

            // 2. Create a Canvas to draw on the Bitmap
            val canvas = Canvas(bitmap)
            // 3. Define the Paint object for the text
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.WHITE // Set text color
            paint.textSize = bitmap.height.toFloat()/30 // Set text size in pixels 10 rows
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.setShadowLayer(2f, 1f, 1f, Color.BLACK) // Add a slight shadow

            // Position the text at the top corner left of the image
            val x = 20f //(textBounds.width()) / 2f + 20f
            var y = 20f //(textBounds.height()) /2f + 20f // 20f for padding

            val textBounds = Rect()
            for (line in msgMapa.split("\n")) {
                // 4. Calculate the text position
                paint.getTextBounds(line, 0, line.length, textBounds)
                y += textBounds.height() + 10f
                // 5. Draw the text onto the canvas
                canvas.drawText(line, x, y, paint)

            }

//            val targetWidth = 1024
//            val factorscale =bitmap.width.toFloat()/targetWidth.toFloat()
//            val targetHeight = (bitmap.height.toFloat() * factorscale).toInt()
//            val reducedBitmap = bitmap.scale(targetHeight,targetWidth) // Reduced size
//            val outputStream = ByteArrayOutputStream()
//            reducedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
//            val byteArray = outputStream.toByteArray()

            // Save to local storage
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val storageDir = getExternalFilesDir("photos")
            val localFile = File(storageDir, "incidente_${tipo}_${timeStamp}.jpg")
            FileOutputStream(localFile).use { fos ->
                //fos.write(byteArray)
                //fos.flush()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            return localFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return  null
        }
    }
    fun guardarIncidencia(calle: String, numero: String) {
        val localPhotoPath = savePhotoLocally(currentPhotoPath, currentTipo.toString())
        if (localPhotoPath == null) {
            Toast.makeText(this, "Failed to save photo locally", Toast.LENGTH_SHORT).show()
            return
        }
        val tipo = currentTipo ?: return
        val descripcion = currentDescripcion ?: ""
        //val localPhotoPath = currentPhotoPath ?: ""
        val date = LocalDate.now().toString()
        val datetime = LocalDateTime.now().toString()
        val newrow = listOf(calle, numero, date, datetime, tipo, localPhotoPath, descripcion)
        try{
            val result = dataRaw?.addIncidenciaEvento(newrow)
            AlertDialog.Builder(this)
                .setTitle("Confirmación")
                .setMessage(if (result == true) "Registro guardado correctamente." else "Error al guardar el registro.")
                .setPositiveButton("OK") {_, _ ->
                    loadIncidencias()
                    if (result == true){
                        guardarWarningIncidencias(newrow)
                    }

                }
                .show()
        }catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Error al guardar incidencia")
                .setMessage("Error al guardar el registro. $e")
                .setPositiveButton("OK") {_, _ ->
                    loadIncidencias()
                }
                .show()
        }
    }
    fun guardarWarningIncidencias(incidenciaRow: List<String>){
        //listOf(calle, numero, date, datetime, tipo, localPhotoPath, descripcion)
        try {
            val calle = incidenciaRow[0]
            val number = incidenciaRow[1]
            val tipo = incidenciaRow[4]
            val maxWarnings = configuraIncidencias?.get(indexBtnClicked)[2]!!.toInt()
            val descripcionIncidencia = configuraIncidencias?.get(indexBtnClicked)[3].toString()
            val values = listOf(
                calle,
                number,
                0,
                tipo
            )
            val countWarnings = dataRaw?.updateDomicilioWarning(values as List<String>)

            if (countWarnings!! >= maxWarnings) {
                AlertDialog.Builder(this@IncidenciasMenu)
                    .setTitle("Acredor a Multa")
                    .setMessage("El domicilio es acredor a una multa (aviso #$maxWarnings)\n $descripcionIncidencia")
                    .setPositiveButton("OK") { _, _ ->
                        saveToMultasGeneradas( tipo,calle,number, descripcionIncidencia)
                        shareViaWhatsApp(tipo,calle,number, maxWarnings)
                    }
                    .show()
            } else {
                Toast.makeText(this@IncidenciasMenu, "Avisos acumulados a $calle:$number, count: $countWarnings", Toast.LENGTH_SHORT).show()
            }
        }catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Error al guardar warning")
                .setMessage("Error al guardar el warning. $e")
                .setPositiveButton("OK") {_, _ ->
                    loadIncidencias()
                }
                .show()
        }
    }
    private fun saveToMultasGeneradas(tipo: String, calle:String, numero: String, concatenatedDescription: String) {
        val now = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTime = now.format(timeFormatter)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val values = listOf(
                    formattedTime,
                    calle,
                    numero,
                    tipo,
                    concatenatedDescription
                )

                //########## GUARDAR NUEVA MULTA ######################
                if (dataRaw?.updateMulta(values  as List<String>) == false)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@IncidenciasMenu,
                            "NO Internet: ERROR salvando MULTA\nAuto reintento en 5 minutos",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                else
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@IncidenciasMenu, "Nueva Multas guardada", Toast.LENGTH_SHORT).show()
                    }
                //#####################################################

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@IncidenciasMenu, "Error saving to MultasGeneradas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun shareViaWhatsApp(tipo: String, calle: String, numero: String, numWarnings: Int) {
        val now = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTime = now.format(timeFormatter)

        val incidencias = dataRaw?.getIncidenciasEventos()
        val incidenciasFilter = incidencias?.filter { it[4].toString().uppercase()==tipo.uppercase() && it[0].toString() == calle && it[1].toString() == numero }?.reversed()
        var arryUrl : ArrayList<Uri> = ArrayList<Uri>()
        incidenciasFilter?.forEachIndexed { index, inciden ->
            val file = File(inciden[5].toString())
            if (file.exists()) {
                arryUrl.add( FileProvider.getUriForFile(this, "${packageName}.fileprovider", file) )
            }
            if (index >= numWarnings) return@forEachIndexed
        }
        val descripcionIncidencia = configuraIncidencias?.get(indexBtnClicked)[3].toString()
        val shareText = "Nueva multa $calle:$numero por $tipo legal: $formattedTime $descripcionIncidencia"

        val intent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE // Use ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arryUrl) // Add image URIs
            putExtra(Intent.EXTRA_TEXT, shareText) // Add the text
            type = "*/*" // Use a wildcard MIME type for mixed content

            // Add flags to grant read permissions to the receiving app
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun mostrarListadoIncidencias(tipo: String) {
        val intent = Intent(this, ListadoIncidenciasActivity::class.java)
        intent.putExtra("TIPO", tipo)
        intent.putExtra("FECHA", FechaIncidencias.toString())
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    solicitarCalleYNumero()
                }
                REQUEST_IMAGE_PICK -> {
                    currentPhotoPath = data?.data!!
                    solicitarCalleYNumero()
                }
            }
        }else {
            Toast.makeText(this, "Image selection cancelled", Toast.LENGTH_SHORT).show()
        }
//        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
//            solicitarCalleYNumero()
//        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    tomarFoto()
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
//            REQUEST_LOCATION_PERMISSION -> {
//                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    saveNewEvent()
//                } else {
//                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
//                }
//            }
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickFromGallery()
                } else {
                    println("Storage permission denied")
                    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        println("Storage permission permanently denied")
                        AlertDialog.Builder(this)
                            .setTitle("Permission Required")
                            .setMessage("Storage permission is needed to select photos from the gallery. Please enable it in Settings.")
                            .setPositiveButton("Go to Settings") { _, _ ->
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.parse("package:$packageName")
                                startActivity(intent)
                            }
                            .setNegativeButton("Cancel") { _, _ -> }
                            .show()
                    } else {
                        Toast.makeText(this, "Storage permission denied. Cannot access gallery.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }




}