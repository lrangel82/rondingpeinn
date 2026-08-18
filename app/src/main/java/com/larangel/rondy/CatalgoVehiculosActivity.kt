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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.larangel.rondy.ui.VehiculoFormDialog
import com.larangel.rondy.utils.extraerPlaca
import com.larangel.rondy.utils.extraerTAG
import com.larangel.rondy.utils.extraerTAGHexToDec
import com.larangel.rondy.utils.stopSearchLoop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class CatalgoVehiculosActivity : AppCompatActivity() {
    private lateinit var dataRaw: DataRawRondin
    private var fullVehicleList: List<List<Any>> = emptyList()
    private var filteredList: MutableList<List<Any>> = mutableListOf()
    private lateinit var vehicleAdapter: VehiculoAdapter

    // UI Elements
    private lateinit var etSearch: EditText
    private lateinit var btnTakePhoto: Button
    private lateinit var limparBtn: Button
    private lateinit var rvResults: RecyclerView
    //private lateinit var layoutForm: View
    private lateinit var btnReport: Button
    private lateinit var btnAdd: Button
    private lateinit var spinnerCalle: Spinner
    private lateinit var spinnerNumero: Spinner

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_catalgo_vehiculos)

        dataRaw = DataRawRondin(applicationContext, lifecycleScope)
        setupUI()

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayoutCatalogoVehiculos)
        swipeRefreshLayout.setOnRefreshListener {
            loadInitialData(true)
        }

        vehicleAdapter = VehiculoAdapter(emptyList()) { vehiculoSeleccionado ->
            openVehiculoDialog(vehiculoSeleccionado) // Al tocar un botón, abre el formulario
        }

        rvResults.adapter = vehicleAdapter

        hideAll()

        loadInitialData()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun loadInitialData(forceLoad: Boolean = false) = lifecycleScope.launch {
        swipeRefreshLayout.isRefreshing = true
        // Carga asíncrona
        fullVehicleList = withContext(Dispatchers.IO) { dataRaw.getAutoRegistrados(forceLoad) ?: emptyList() }
        //domicilios = withContext(Dispatchers.IO) { dataRaw.getDomiciliosUbicacion() ?: emptyList() }
        val txtAyuda = findViewById<TextView>(R.id.textAyudaCatalogoV)
        txtAyuda.setText("Ingrese las Placas del vehiculo, o Escanee el TAG o la marca del vehiculo\n Total Parque Vehicular:${fullVehicleList.size}")
        showResultsList(fullVehicleList as List<List<String>>) //Mostrar todos
        swipeRefreshLayout.isRefreshing = false
    }
    private fun setupUI() {
        etSearch = findViewById(R.id.etSearch)
        rvResults = findViewById(R.id.rvResults)
        limparBtn = findViewById(R.id.btnLimpiarCatV)

        // Configuración de Grilla en Landscape
        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 1
        rvResults.layoutManager = GridLayoutManager(this, spanCount)

        etSearch.doOnTextChanged { text, start, before, count ->
            val query = text.toString()
            if (query.isNotEmpty() && query.length > 2 ) {
                stopSearchLoop=true
//                val tagStr: String? = query.split("\n").firstNotNullOfOrNull { it.extraerTAGHexToDec() }
//                //Esta visible el FORM, y es un tag del lector... escribir en el TAGID de la edicion
//                if (layoutForm.visibility == View.VISIBLE && tagStr != null) {
//                    val etTag = findViewById<EditText>(R.id.etFormTag)
//                    etTag.setText(tagStr)
//                    etSearch.setText("")
//                    hideKeyboard()
//                } else {
                    //Realizar busqueda normal
                performSearch(query)
                //}
            }
//            if (query.length >= 3) {
//                performSearch(query)
//            }else{
//                rvResults.visibility = View.GONE
//            }
        }
        limparBtn.setOnClickListener {
            etSearch.setText("")
            performSearch("")
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
        stopSearchLoop = false
        val lines = query.split("\n")
        filteredList.clear()
        for (line in lines) {
            if (stopSearchLoop == true) return
            var _query = line.extraerTAGHexToDec() //if (esLectorRFID == true)  line.extraerTAGHexToDec() else line
            if (_query.isNullOrEmpty()) _query = line
            if (_query.isNotEmpty()) {
                filteredList.addAll(fullVehicleList.filter { row ->
                    if (stopSearchLoop == true) false
                    else row.any { it.toString().contains(_query.toString(), ignoreCase = true) }
                })
            }
        }
        if (stopSearchLoop == true) return

        when {
            filteredList.isEmpty() && query.isNotEmpty() -> showNotFoundOptions()
            filteredList.size == 1 -> openVehiculoDialog(filteredList[0])
            filteredList.isEmpty() && query.isEmpty() -> showResultsList(fullVehicleList as List<List<String>>)
            else -> showResultsList(filteredList as List<List<String>>)
        }
    }

    private fun showResultsList(lisData: List<List<String>>) {
        // Aseguramos visibilidad
        rvResults.visibility = View.VISIBLE
        //layoutForm.visibility = View.GONE
        findViewById<View>(R.id.notFoundActions).visibility = View.GONE

        // Actualizamos los datos del adapter con la lista filtrada
        vehicleAdapter.updateData(lisData )
    }

    private fun hideAll(){
        findViewById<EditText>(R.id.etSearch).requestFocus()
        rvResults.visibility = View.GONE
        //layoutForm.visibility = View.GONE
        findViewById<View>(R.id.notFoundActions).visibility = View.GONE
        hideKeyboard()
    }

    private fun showNotFoundOptions() {
        // 1. Visibilidad: Ocultamos lista y formulario, mostramos acciones de "no encontrado"
        rvResults.visibility = View.GONE
        //layoutForm.visibility = View.GONE
        findViewById<View>(R.id.notFoundActions).visibility = View.VISIBLE

        val busquedaActual = etSearch.text.toString().trim()
        val tagValido = busquedaActual.extraerTAGHexToDec()
        var strTag="NA"
        var strPlate="NA"

        if (tagValido != null)
            strTag = tagValido
        else {
            // Contiene letras -> asumimos que es una PLACA
            strPlate = busquedaActual.extraerPlaca().toString()
        }
        findViewById<TextView>(R.id.txtNoRegistro).setText("El registro [[${tagValido ?: strPlate}]] no existe en la base de datos, que desea hacer?")

        // 2. Configurar botón de Reportar (Inexistente)
        btnReport = findViewById<Button>(R.id.btnReportInexistente)
        btnReport.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                // Guardamos el valor que no se encontró para revisión del admin
                val valuesNoVEHICULO = listOf(
                    strPlate,
                    "NA",
                    "NA",
                    LocalDate.now().toString(),
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
            openVehiculoDialog(null) // Abrimos formulario vacío

            // Lógica de pre-llenado inteligente
            if (tagValido != null) {
                // Es puramente numérico -> asumimos que es un TAG
                findViewById<EditText>(R.id.etFormTag).setText(tagValido)
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

    private fun openVehiculoDialog(row: List<Any>?) {
        val dialog = VehiculoFormDialog(row,dataRaw) { datosActualizados ->
            updateVehicle(datosActualizados)
        }
        dialog.show(supportFragmentManager, "VehiculoDialog")
    }

    private fun updateVehicle( newRow: List<String>) = lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            dataRaw.updateAutoRegistrados(newRow)
            fullVehicleList = withContext(Dispatchers.IO) { dataRaw.getAutoRegistrados() ?: emptyList() }
        }
        Toast.makeText(this@CatalgoVehiculosActivity, "Actualizado", Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val view = currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

}