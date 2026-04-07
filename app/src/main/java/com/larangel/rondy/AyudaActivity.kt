package com.larangel.rondy

import AyudaAdapter
import AyudaSlideItem
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.tbuonomo.viewpagerdotsindicator.DotsIndicator

class AyudaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ayuda)

        val listaImagenes = listOf<AyudaSlideItem>(
            AyudaSlideItem(R.drawable.paso1,"Puede poner el logo de su empresa/condominio"),
            AyudaSlideItem(R.drawable.paso2,"Gestione los permisos o actividades aprobadas"),
            AyudaSlideItem(R.drawable.paso3,"Lleve el control de las incidencias reportadas")
        )
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val dotsIndicator = findViewById<DotsIndicator>(R.id.dotsIndicator)
        val btnNext = findViewById<Button>(R.id.btnNext)
        val btnSkip = findViewById<TextView>(R.id.btnSkip)

        viewPager.adapter = AyudaAdapter(listaImagenes)
        dotsIndicator.attachTo(viewPager)

        btnNext.setOnClickListener {
            if (viewPager.currentItem + 1 < listaImagenes.size) {
                viewPager.currentItem += 1
            } else {
                finish() // Al llegar al final, cerramos la ayuda
            }
        }

        btnSkip.setOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}