package com.larangel.rondy

import DataRawRondin
import VehiculoAdapter
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.larangel.rondy.utils.extraerPlaca
import com.larangel.rondy.utils.extraerTAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CatalgoVehiculosActivity : AppCompatActivity() {
    private lateinit var dataRaw: DataRawRondin
    private var fullVehicleList: List<List<Any>> = emptyList()
    private var filteredList: MutableList<List<Any>> = mutableListOf()
    private var domicilios: List<List<Any>> = emptyList()
    private var listaCalles: List<String> = emptyList()
    private lateinit var vehicleAdapter: VehiculoAdapter
    private var isAutoSelecting = false


    // UI Elements
    private lateinit var etSearch: EditText
    private lateinit var btnTakePhoto: Button
    private lateinit var rvResults: RecyclerView
    private lateinit var layoutForm: View
    private lateinit var btnReport: Button
    private lateinit var btnAdd: Button
    private lateinit var spinnerCalle: Spinner
    private lateinit var spinnerNumero: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_catalgo_vehiculos)

        dataRaw = DataRawRondin(applicationContext, lifecycleScope)
        setupUI()
        loadInitialData()

        vehicleAdapter = VehiculoAdapter(emptyList()) { vehiculoSeleccionado ->
            showForm(vehiculoSeleccionado) // Al tocar un botón, abre el formulario
        }
        rvResults.adapter = vehicleAdapter

        hideAll()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun loadInitialData() = lifecycleScope.launch {
        // Carga asíncrona
        fullVehicleList = withContext(Dispatchers.IO) { dataRaw.getAutoRegistrados() ?: emptyList() }
        domicilios = withContext(Dispatchers.IO) { dataRaw.getDomiciliosUbicacion() ?: emptyList() }
        val txtAyuda = findViewById<TextView>(R.id.textAyudaCatalogoV)
        txtAyuda.setText("Ingrese las Placas del vehiculo, o Escanee el TAG o la marca del vehiculo\n Total Parque Vehicular:${fullVehicleList.size}")
        setupSpinners()
        showResultsList(fullVehicleList as List<List<String>>) //Mostrar todos
    }
    private fun setupUI() {
        etSearch = findViewById(R.id.etSearch)
        rvResults = findViewById(R.id.rvResults)
        layoutForm = findViewById(R.id.layoutForm)

        // Configuración de Grilla en Landscape
        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 1
        rvResults.layoutManager = GridLayoutManager(this, spanCount)

        etSearch.doOnTextChanged { text, start, before, count ->
            val query = text.toString()
            if (query.isNotEmpty() ) {
                val esLectorRFID = query.contains("\n")
                //Esta visible el FORM, y es un tag del lector... escribir en el TAGID de la edicion
                if (layoutForm.visibility == View.VISIBLE && esLectorRFID) {
                    val listaTags: List<String> = query.split("\n")
                    val tagStr: String? = listaTags.firstNotNullOfOrNull { it.extraerTAG() }
                    val etTag = findViewById<EditText>(R.id.etFormTag)
                    etTag.setText(tagStr)
                    etSearch.setText("")
                    hideKeyboard()
                } else {
                    //Realizar busqueda normal
                    performSearch(query)
                }
            }
//            if (query.length >= 3) {
//                performSearch(query)
//            }else{
//                rvResults.visibility = View.GONE
//            }
        }
    }
    override fun onUserInteraction() {
        super.onUserInteraction()

        // Si el foco no está ya en el input, lo regresamos después de un pequeño delay
        // para permitir que el botón presionado ejecute su acción primero.
        if (!etSearch.isFocused) {
            etSearch.postDelayed({
                etSearch.requestFocus()
            }, 800) // 800ms es suficiente para no interferir con el clic
        }
    }

    private fun performSearch(query: String) {
        //Es lectura de lector RFID
        val esLectorRFID = query.contains("\n")
        val lines = query.split("\n")
        filteredList.clear()
        for (line in lines) {
            val _query = if (esLectorRFID == true)  line.extraerTAG() else line
            filteredList.addAll(fullVehicleList.filter { row ->
                row.any { it.toString().contains(_query.toString(), ignoreCase = true) }
            })
        }

        when {
            filteredList.isEmpty() && query.isNotEmpty() -> showNotFoundOptions()
            filteredList.size == 1 -> showForm(filteredList[0])
            filteredList.isEmpty() && query.isEmpty() -> showResultsList(fullVehicleList as List<List<String>>)
            else -> showResultsList(filteredList as List<List<String>>)
        }
    }

    private fun showResultsList(lisData: List<List<String>>) {
        // Aseguramos visibilidad
        rvResults.visibility = View.VISIBLE
        layoutForm.visibility = View.GONE
        findViewById<View>(R.id.notFoundActions).visibility = View.GONE

        // Actualizamos los datos del adapter con la lista filtrada
        vehicleAdapter.updateData(lisData )
    }

    private fun hideAll(){
        findViewById<EditText>(R.id.etSearch).requestFocus()
        rvResults.visibility = View.GONE
        layoutForm.visibility = View.GONE
        findViewById<View>(R.id.notFoundActions).visibility = View.GONE
        hideKeyboard()
    }

    private fun showNotFoundOptions() {
        // 1. Visibilidad: Ocultamos lista y formulario, mostramos acciones de "no encontrado"
        rvResults.visibility = View.GONE
        layoutForm.visibility = View.GONE
        findViewById<View>(R.id.notFoundActions).visibility = View.VISIBLE

        val busquedaActual = etSearch.text.toString().trim()
        var strTag="NA"
        var strPlate="NA"

        if (busquedaActual.all { it.isDigit() })
            strTag = busquedaActual
        else {
            // Contiene letras -> asumimos que es una PLACA
            strPlate = busquedaActual.extraerPlaca().toString()
        }

        // 2. Configurar botón de Reportar (Inexistente)
        btnReport = findViewById<Button>(R.id.btnReportInexistente)
        btnReport.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                // Guardamos el valor que no se encontró para revisión del admin
                val valuesNoVEHICULO = listOf(
                    strPlate,
                    "NA",
                    "NA",
                    "NA",
                    "NA",
                    "NA",
                    strTag,
                    "NA",
                    "-987654321"
                )
                dataRaw.addAutoRegistrados(valuesNoVEHICULO)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CatalgoVehiculosActivity, "Reportado al administrador", Toast.LENGTH_SHORT).show()
                    hideAll()
                }
            }
        }

        // 3. Configurar botón de Agregar Nuevo
        btnAdd = findViewById<Button>(R.id.btnAddVehiculo)
        btnAdd.setOnClickListener {
            findViewById<View>(R.id.notFoundActions).visibility = View.GONE
            showForm(null) // Abrimos formulario vacío

            // Lógica de pre-llenado inteligente
            if (busquedaActual.all { it.isDigit() }) {
                // Es puramente numérico -> asumimos que es un TAG
                findViewById<EditText>(R.id.etFormTag).setText(busquedaActual)
            } else {
                // Contiene letras -> asumimos que es una PLACA
                findViewById<EditText>(R.id.etFormPlaca).setText(strPlate ?: busquedaActual)
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val plateRegex = Regex("[A-Z]{3}-?\\d{3,4}") // Ajusta según formato local
                val match = plateRegex.find(visionText.text)
                if (match != null) {
                    etSearch.setText(match.value)
                } else {
                    Toast.makeText(this, "No se detectó una placa válida", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showForm(row: List<Any>?) {
        hideAll()
        etSearch.setText("")
        layoutForm.visibility = View.VISIBLE


        val etPlaca = findViewById<EditText>(R.id.etFormPlaca)
        val etMarca = findViewById<EditText>(R.id.etFormMarca)
        val etModelo= findViewById<EditText>(R.id.etFormModelo)
        val etColor = findViewById<EditText>(R.id.etFormColor)
        val etTag   = findViewById<EditText>(R.id.etFormTag)

        // Aquí llenas los campos: [placas, calle, numero, marca, modelo, color, tag]
        // Para calle y número, implementas Spinners filtrados con la lista 'domicilios'
        etPlaca.setText( row?.getOrNull(0).toString() )
        etMarca.setText( row?.getOrNull(3).toString() )
        etModelo.setText( row?.getOrNull(4).toString() )
        etColor.setText( row?.getOrNull(5).toString() )
        etTag.setText( row?.getOrNull(6).toString() )
        preseleccionarDomicilio(row?.getOrNull(1).toString(),row?.getOrNull(2).toString())

        //A que le pones el foco?


        val btnCancel = findViewById<Button>(R.id.btnCancel)
        btnCancel.setOnClickListener {
            hideAll()
            etSearch.setText("")
        }
        val btnUpdate = findViewById<Button>(R.id.btnUpdate)
        btnUpdate.setOnClickListener {
            val valuesNew = listOf(
                etPlaca.text.toString().filter { it.isLetterOrDigit() }.uppercase(),
                spinnerCalle.selectedItem.toString(),
                spinnerNumero.selectedItem.toString(),
                etMarca.text.toString(),
                etModelo.text.toString(),
                etColor.text.toString(),
                etTag.text.toString().filter { it.isDigit() },
                row?.getOrNull(7).toString(),
                row?.getOrNull(8).toString()
            )
            updateVehicle(valuesNew)
            hideAll()
        }
    }

    private fun updateVehicle( newRow: List<String>) = lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            dataRaw.updateAutoRegistrados(newRow)
            fullVehicleList = withContext(Dispatchers.IO) { dataRaw.getAutoRegistrados() ?: emptyList() }
        }
        Toast.makeText(this@CatalgoVehiculosActivity, "Actualizado", Toast.LENGTH_SHORT).show()
    }

    private fun setupSpinners() {
        spinnerCalle = findViewById<Spinner>(R.id.spinnerCalle)
        spinnerNumero = findViewById<Spinner>(R.id.spinnerNumero)
        // 1. Obtener lista única de calles (posición 0 del sublistado)
        listaCalles = domicilios.map { it[0].toString() }.distinct().sorted()

        // 2. Configurar el adaptador de Calles
        val adapterCalle = ArrayAdapter(this, android.R.layout.simple_spinner_item, listaCalles)
        adapterCalle.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCalle.adapter = adapterCalle

        // 3. Listener para detectar cambios en Calle
        spinnerCalle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val calleSeleccionada = listaCalles[position]
                if (!isAutoSelecting) {
                    actualizarSpinnerNumeros(calleSeleccionada)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    private fun actualizarSpinnerNumeros(calle: String) {
        // 4. Filtrar la lista global de domicilios por la calle elegida
        // Asumimos que el número está en la posición 1 [calle, numero, lat, lon]
        val numerosFiltrados = domicilios
            .filter { it[0] == calle }
            .map { it[1].toString() }
            .distinct()
            .sorted()

        // 5. Configurar el adaptador de Números con los valores filtrados
        val adapterNumero = ArrayAdapter(this, android.R.layout.simple_spinner_item, numerosFiltrados)
        adapterNumero.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNumero.adapter = adapterNumero
    }
    private fun preseleccionarDomicilio(calleExistente: String, numeroExistente: String) {
        // Seleccionar Calle
        val indexCalle = listaCalles.indexOf(calleExistente)
        if (indexCalle != -1) {
            isAutoSelecting = true
            spinnerCalle.setSelection(indexCalle)

            // El listener de la calle disparará actualizarSpinnerNumeros automáticamente,
            // pero necesitamos esperar un momento o forzar la carga para seleccionar el número.
            val numerosDeEstaCalle = domicilios.filter { it[0] == calleExistente }.map { it[1].toString() }.distinct()
                .sorted()
            val adapterNumero = ArrayAdapter(this, android.R.layout.simple_spinner_item, numerosDeEstaCalle)
            adapterNumero.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerNumero.adapter = adapterNumero

            adapterNumero.notifyDataSetChanged()

            val indexNumero = numerosDeEstaCalle.indexOf(numeroExistente)
            if (indexNumero != -1) {
                spinnerNumero.post {
                    spinnerNumero.setSelection(indexNumero, false)
                    spinnerNumero.post { isAutoSelecting = false }
                }
            }else {
                isAutoSelecting = false
            }
        }
    }

    private fun hideKeyboard() {
        val view = currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

}