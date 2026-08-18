package com.larangel.rondy.ui

import DataRawRondin
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.larangel.rondy.R
import com.larangel.rondy.utils.extraerTAGHexToDec
import com.larangel.rondy.utils.stopSearchLoop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class VehiculoFormDialog (
    private val row: List<Any>? = null,
    private val dataRaw: DataRawRondin,
    private val onConfirm: (List<String>) -> Unit
) : DialogFragment() {
    private var domicilios: List<List<Any>> = emptyList()
    private var listaCalles: List<String> = emptyList()
    private lateinit var spinnerCalle: Spinner
    private lateinit var spinnerNumero: Spinner
    private var isAutoSelecting = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflamos el layout que ya tienes (asegúrate de que el ID sea el correcto)
        return inflater.inflate(R.layout.dialog_vehiculo_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //loadInitialData2()

        val etPlaca = view.findViewById<EditText>(R.id.etFormPlaca)
        val etMarca = view.findViewById<EditText>(R.id.etFormMarca)
        val etModelo = view.findViewById<EditText>(R.id.etFormModelo)
        val etColor = view.findViewById<EditText>(R.id.etFormColor)
        val etTag = view.findViewById<EditText>(R.id.etFormTag)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnUpdate = view.findViewById<Button>(R.id.btnUpdate)

        // 1. Llenar datos si es edición
        row?.let {
            etPlaca.setText(it.getOrNull(0).toString())
            etMarca.setText(it.getOrNull(3).toString())
            etModelo.setText(it.getOrNull(4).toString())
            etColor.setText(it.getOrNull(5).toString())
            etTag.setText(it.getOrNull(6).toString())
            // Aquí llamarías a tu lógica de preseleccionar domicilios
            preseleccionarDomicilio(it.getOrNull(1).toString(), it.getOrNull(2).toString())
        }

        // 2. Lanzar la carga de datos de forma segura
        lifecycleScope.launch {
            // Bloqueamos de forma secuencial dentro de la corrutina usando withContext
            domicilios = withContext(Dispatchers.IO) {
                dataRaw.getDomiciliosUbicacion() ?: emptyList()
            }
            Log.d("SPINNER", "Datos cargados: ${domicilios.toString()}")

            // 3. Una vez que existen los datos, configuramos Spinners y seleccionamos valores
            setupSpinners(view)

            // Si es edición, preseleccionamos ahora que los Spinners ya tienen los adapters llenos
            row?.let {
                preseleccionarDomicilio(it.getOrNull(1).toString(), it.getOrNull(2).toString())
            }
        }


        btnCancel.setOnClickListener { dismiss() }

        btnUpdate.setOnClickListener {
            val valuesNew = listOf(
                etPlaca.text.toString().filter { it.isLetterOrDigit() }.uppercase(),
                spinnerCalle.selectedItem.toString() ?: "",
                spinnerNumero.selectedItem.toString() ?: "",
                etMarca.text.toString(),
                etModelo.text.toString(),
                etColor.text.toString(),
                etTag.text.toString().filter { it.isDigit() },
                row?.getOrNull(7)?.toString() ?: "", // Manejo de nulos para creación
                row?.getOrNull(8)?.toString() ?: ""
            )
            onConfirm(valuesNew) // Devolvemos los datos al Activity
            dismiss()
        }

        etTag.doOnTextChanged { text, start, before, count ->
            val query = text.toString()
            if (query.isNotEmpty() && query.length > 2 ) {
                val tagStr: String? = query.split("\n").firstNotNullOfOrNull { it.extraerTAGHexToDec() }
                //Esta visible el FORM, y es un tag del lector... escribir en el TAGID de la edicion
                if ( tagStr != null) {
                    etTag.setText(tagStr)
                    etTag.setSelection(tagStr.length)
                }
            }
        }

    }

    private fun setupSpinners(view: View) {
        spinnerCalle = view.findViewById<Spinner>(R.id.spinnerCalle)
        spinnerNumero = view.findViewById<Spinner>(R.id.spinnerNumero)
        // 1. Obtener lista única de calles (posición 0 del sublistado)
        listaCalles = domicilios.map { it[0].toString() }.distinct().sorted()
        Log.d("SPINNER", "setupSpinners listaCalles:" + listaCalles.toString())

        // 2. Configurar el adaptador de Calles
        val adapterCalle = ArrayAdapter(view.context, android.R.layout.simple_spinner_item, listaCalles)
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
        val adapterNumero = ArrayAdapter(this.requireContext(), android.R.layout.simple_spinner_item, numerosFiltrados)
        adapterNumero.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNumero.adapter = adapterNumero
    }
    private fun preseleccionarDomicilio(calleExistente: String, numeroExistente: String) {
        // Seleccionar Calle
        val indexCalle = listaCalles.indexOf(calleExistente)
        Log.d("SPINNER", "indexCalle:" + indexCalle.toString() + " listaCalles:" + listaCalles.toString() + " calleExistente:" + calleExistente + " numeroExistente:" + numeroExistente)
        if (indexCalle != -1) {
            isAutoSelecting = true
            spinnerCalle.setSelection(indexCalle)
            Log.d("SPINNER", "preseleccionarDomicilio selectedItem:" + spinnerCalle.selectedItem )

            // El listener de la calle disparará actualizarSpinnerNumeros automáticamente,
            // pero necesitamos esperar un momento o forzar la carga para seleccionar el número.
            val numerosDeEstaCalle = domicilios.filter { it[0] == calleExistente }.map { it[1].toString() }.distinct()
                .sorted()
            val adapterNumero = ArrayAdapter(this.requireContext(), android.R.layout.simple_spinner_item, numerosDeEstaCalle)
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

    // Opcional: Ajustar el tamaño del diálogo para que no se vea pequeño
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}