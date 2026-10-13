package com.larangel.rondy

import DataRawRondin
import MySettings
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RfidSettingsActivity : AppCompatActivity() {
    // Tu objeto personalizado de persistencia
    private var mySettings: MySettings? = null
    private val byteTextViews = ArrayList<TextView>()
    private var isTranslating = false

    // Variables de configuración activa
    private var currentOffset = 0
    private var currentBytesToRead = 1
    private var currentFormat = "Hexadecimal"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_rfid_settings)

        mySettings = MySettings(applicationContext)

        setupByteLabels()
        setupSpinners()
        setupListeners()

        // Cargar configuraciones guardadas previamente
        readConfig()

        // Botones de Acción de guardado/cancelación
        val btnCancelar: Button = findViewById(R.id.btnCancelarConfRfid)
        val btnGuardar: Button = findViewById(R.id.btnGuardarConfRfid)

        btnCancelar.setOnClickListener {
            this.finish()
        }

        btnGuardar.setOnClickListener {
            salvarConfig()
            this.finish()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // Genera la cuadrícula estandarizada de 12 pares de bytes
    private fun setupByteLabels() {
        val glBytesContainer: GridLayout = findViewById(R.id.glBytesContainer)
        glBytesContainer.removeAllViews()

        for (i in 0 until 12) {
            val tv = TextView(this).apply {
                text = "00"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(8, 16, 8, 16)
                setBackgroundColor(Color.LTGRAY)
                setTextColor(Color.BLACK)

                val param = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4, 4, 4, 4)
                }
                layoutParams = param
            }
            byteTextViews.add(tv)
            glBytesContainer.addView(tv)
        }
    }

    private fun setupSpinners() {
        val spFormat: Spinner = findViewById(R.id.spFormat)
        val formats = listOf("Hexadecimal", "Decimal")
        spFormat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formats)

        updateBytesReaderSpinner()
        updateOffsetSpinner()
    }
    private fun updateBytesReaderSpinner() {
        val spBytesReader: Spinner = findViewById(R.id.spBytesReader)
        val items = (1..12).toList()
        spBytesReader.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        spBytesReader.setSelection(items.indexOf(currentBytesToRead).coerceAtLeast(0))
    }
    private fun updateOffsetSpinner() {
        val spByteOffset: Spinner = findViewById(R.id.spByteOffset)
        val maxOffset = 11 - (currentBytesToRead - 1)
        val items = (0..maxOffset).toList()
        spByteOffset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        spByteOffset.setSelection(items.indexOf(currentOffset).coerceAtLeast(0))
    }

    private fun setupListeners() {
        val txtRfidInput: EditText = findViewById(R.id.etRfidTag)
        val spBytesReader: Spinner = findViewById(R.id.spBytesReader)
        val spByteOffset: Spinner = findViewById(R.id.spByteOffset)
        val spFormat: Spinner = findViewById(R.id.spFormat)

        // Monitoreo del campo de texto
        txtRfidInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isTranslating) return
                processRfidTranslation()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        spBytesReader.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentBytesToRead = parent?.getItemAtPosition(position) as Int
                updateOffsetSpinner() // Ajusta dinámicamente el límite superior del offset
                processRfidTranslation()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spByteOffset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentOffset = parent?.getItemAtPosition(position) as Int
                processRfidTranslation()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spFormat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFormat = parent?.getItemAtPosition(position).toString()
                processRfidTranslation()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // Lee los datos guardados en tu objeto MySettings
    fun readConfig() {
        val txtRfidInput: EditText = findViewById(R.id.etRfidTag)
        val spFormat: Spinner = findViewById(R.id.spFormat)

        // Recuperar valores usando tus llaves personalizadas
        val savedTag = mySettings?.getString("rfid_saved_tag", "") ?: ""
        currentOffset = mySettings?.getInt("rfid_byte_offset", 0) ?: 0
        currentBytesToRead = mySettings?.getInt("rfid_bytes_reader", 1) ?: 1
        currentFormat = mySettings?.getString("rfid_output_format", "Hexadecimal") ?: "Hexadecimal"

        // Asignar a la UI
        txtRfidInput.setText(savedTag)

        val formatItems = listOf("Hexadecimal", "Decimal")
        spFormat.setSelection(formatItems.indexOf(currentFormat).coerceAtLeast(0))

        updateBytesReaderSpinner()
        updateOffsetSpinner()
        processRfidTranslation()
    }

    // Salva la configuración usando tu estructura habitual de persistencia
    fun salvarConfig() {
        val txtRfidInput: EditText = findViewById(R.id.etRfidTag)

        // Extraer la última línea limpia para guardar de forma homogénea
        val rawInput = txtRfidInput.text.toString()
        val lastLineTag = rawInput.split("\n")
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() } ?: ""

        mySettings?.saveString("rfid_saved_tag", lastLineTag)
        mySettings?.saveInt("rfid_byte_offset", currentOffset)
        mySettings?.saveInt("rfid_bytes_reader", currentBytesToRead)
        mySettings?.saveString("rfid_output_format", currentFormat)
    }
    // Procesa el texto filtrando saltos de línea y actualizando la interfaz reactiva
    private fun processRfidTranslation() {
        val txtRfidInput: EditText = findViewById(R.id.etRfidTag)
        val tvTranslatedResult: TextView = findViewById(R.id.tvTranslatedResult)

        val rawInput = txtRfidInput.text.toString()

        // Lógica de filtrado: Divide por salto de línea, limpia espacios y toma la última línea válida
        val lastValidLine = rawInput.split("\n")
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() } ?: ""

        // Solo modificamos el texto si realmente cambió (ej. venían varias líneas o espacios)
        if (rawInput != lastValidLine) {
            try {
                isTranslating = true // Bloqueamos el listener
                txtRfidInput.setText(lastValidLine + "\n")
                // Mueve el cursor al final del texto para que el usuario pueda seguir escribiendo cómodamente
                txtRfidInput.setSelection(lastValidLine.length + 1)
            } finally {
                isTranslating = false // Desbloqueamos el listener pase lo que pase
            }
        }

        // Normalizar a una cadena de 24 caracteres HEX rellenando con ceros de ser necesario
        val cleanHex24 = lastValidLine.padEnd(24, '0').substring(0, 24)

        val startByte = currentOffset
        val endByte = currentOffset + currentBytesToRead - 1
        val selectedHexBuilder = StringBuilder()

        // Actualizar visualmente los 12 contenedores independientes
        for (i in 0 until 12) {
            val byteStr = cleanHex24.substring(i * 2, (i * 2) + 2)
            byteTextViews[i].text = byteStr

            if (i in startByte..endByte) {
                // Fondo Negro y texto Blanco para los bytes que sí serán leídos
                byteTextViews[i].setBackgroundColor(Color.BLACK)
                byteTextViews[i].setTextColor(Color.WHITE)
                selectedHexBuilder.append(byteStr)
            } else {
                // Fondo Gris por defecto
                byteTextViews[i].setBackgroundColor(Color.LTGRAY)
                byteTextViews[i].setTextColor(Color.BLACK)
            }
        }
        val hexResult = selectedHexBuilder.toString()

        // Realizar la conversión final del fragmento según el Spinner seleccionado
        if (currentFormat == "Decimal") {
            try {
                val decimalResult = hexResult.toLong(16)
                tvTranslatedResult.text = decimalResult.toString()
            } catch (e: Exception) {
                tvTranslatedResult.text = "Error (Valor excede rango numérico)"
            }
        } else {
            tvTranslatedResult.text = hexResult
        }
    }

}