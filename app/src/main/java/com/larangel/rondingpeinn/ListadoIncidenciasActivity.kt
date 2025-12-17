package com.larangel.rondingpeinn

import DataRawRondin
import IncidenciaAdapter
import MySettings
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate


class ListadoIncidenciasActivity : AppCompatActivity() {
    private var mySettings: MySettings? = null
    private var dataRaw: DataRawRondin? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listado_incidencias)

        mySettings = MySettings(this)
        dataRaw = DataRawRondin(this,CoroutineScope(Dispatchers.IO))

        val tipo = intent.getStringExtra("TIPO") ?: "Desconocido"
        val FechaIncidencias = LocalDate.parse( intent.getStringExtra("FECHA")) ?: LocalDate.now()
        findViewById<TextView>(R.id.txtTipoTitulo).text = "Incidencias $tipo (${FechaIncidencias})"
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        val incidencias = dataRaw?.getIncidenciasEventosTipo(tipo, FechaIncidencias as LocalDate) ?:mutableListOf<List<Any>>()

        val recycler = findViewById<RecyclerView>(R.id.recyclerIncidencias)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = IncidenciaAdapter(incidencias)
    }
}