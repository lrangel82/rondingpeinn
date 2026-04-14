package com.larangel.rondy

import DataRawRondin
import MySettings
import android.app.AlertDialog
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.larangel.rondy.utils.programarAlarma
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CatalogoAlarmasActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null
    private lateinit var adapter: AlarmasAdapter
    private var listaAlarmas = mutableListOf<MutableList<Any>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_catalogo_alarmas)

        mySettings = MySettings(applicationContext)
        dataRaw = DataRawRondin(applicationContext,CoroutineScope(Dispatchers.IO))

        setupRecyclerView()
        loadAlarmas()

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            mostrarDialogoAlarma(null) // null para nueva alarma
        }
        findViewById<Button>(R.id.btnCerrar).setOnClickListener {
            this.finish()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvAlarmas)
        rv.layoutManager = LinearLayoutManager(this)

        adapter = AlarmasAdapter(listaAlarmas,
            onEdit = { index ->
                // Before editing, we might want to cancel the old one if the time changes
                mostrarDialogoAlarma(index)
            },
            onDelete = { index ->
                val alarmaToDelete= listaAlarmas[index]
                val horaParaCancelar = alarmaToDelete[0].toString()
                cancelarAlarmaEnSistema(horaParaCancelar) // 1. Remove from System

                lifecycleScope.launch(Dispatchers.IO) {
                    dataRaw?.eliminarAlarma(alarmaToDelete[0].toString(),alarmaToDelete[1].toString()) // 3. Persist change
                }

                listaAlarmas.removeAt(index) // 2. Remove from List
                adapter.notifyItemRemoved(index)


            }
        )
        rv.adapter = adapter
    }

    private fun loadAlarmas() = lifecycleScope.launch {
        val data = dataRaw?.getAlarmas(true)
        listaAlarmas = data?.map { it.toMutableList() }?.toMutableList() ?: mutableListOf()
        adapter.update(listaAlarmas)
    }
    private fun cancelarAlarmaEnSistema(hora: String) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)

        // The ID must be the same used in programarAlarma (hora.hashCode())
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            hora.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
    private fun mostrarDialogoAlarma(index: Int?) {
        val al = if (index != null) listaAlarmas[index] else null

        // 1. Seleccionar Hora
        val timePicker = TimePickerDialog(this, { _, hour, minute ->
            val horaFormateada = String.format("%02d:%02d", hour, minute)

            // 2. Pedir Nombre
            val input = EditText(this).apply { setText(al?.get(1)?.toString() ?: "") }
            AlertDialog.Builder(this)
                .setTitle("Nombre de la Alarma")
                .setView(input)
                .setPositiveButton("Guardar") { _, _ ->
                    //Cancelar el anterior
                    cancelarAlarmaEnSistema(al?.get(0).toString())
                    val nuevoNombre = input.text.toString()
                    guardarCambios(index, horaFormateada, nuevoNombre)
                }.show()
        }, 12, 0, true)
        timePicker.show()
    }

    private fun guardarCambios(index: Int?, hora: String, nombre: String) {
        var oldData = mutableListOf<Any>("99:99","")
        if (index == null) {
            listaAlarmas.add(mutableListOf(hora, nombre))
        } else {
            oldData = listaAlarmas[index].toMutableList()
            listaAlarmas[index][0] = hora
            listaAlarmas[index][1] = nombre
        }

        lifecycleScope.launch(Dispatchers.IO) {
            dataRaw?.updateAlarma(oldData as List<String>,listOf(hora,nombre)) // Asumiendo que existe esta función
            withContext(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
                programarAlarma(applicationContext, hora, nombre)
                // IMPORTANTE: Aquí debes llamar a tu función de programarAlarma
                // para que el sistema registre el cambio.
            }
        }
    }
}

class AlarmasAdapter(
    private var alarmas: MutableList<MutableList<Any>>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<AlarmasAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHora: TextView = view.findViewById(R.id.tvHora)
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarma, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val al = alarmas[position]
        holder.tvHora.text = al[0].toString()
        holder.tvNombre.text = al[1].toString()

        holder.itemView.setOnClickListener { onEdit(position) }
        holder.btnDelete.setOnClickListener { onDelete(position) }
    }

    override fun getItemCount() = alarmas.size

    fun update(newList: MutableList<MutableList<Any>>) {
        alarmas = newList
        notifyDataSetChanged()
    }
}