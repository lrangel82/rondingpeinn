package com.larangel.rondingpeinn

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.larangel.rondingpeinn.R
import com.larangel.rondingpeinn.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var counterAdmin: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val btn: Button = findViewById(R.id.btn_StartRondin)
        btn.setOnClickListener{
            val intent: Intent = Intent(this, StartRondinActivity::class.java )
            startActivity(intent)
        }
        val btnPermisos: Button = findViewById(R.id.btn_permisos)
        btnPermisos.setOnClickListener{
            val intent: Intent = Intent(this, PermisosActivity::class.java )
            startActivity(intent)
        }
        val btn_vehiculos: Button = findViewById(R.id.btn_vehiculos)
        btn_vehiculos.setOnClickListener{
            val intent: Intent = Intent(this, VehicleSearchActivity::class.java )
            startActivity(intent)
        }

        val copyRAdmin: TextView = findViewById<TextView>(R.id.textCopyright)
        copyRAdmin.setOnClickListener{
            val clickRequired: Int = 9
            if (counterAdmin++ >= clickRequired ) {
                // Modo Admin
                val intent: Intent = Intent(this, ProgramarTags::class.java )
                startActivity(intent)
            }
            else if ( counterAdmin >= clickRequired - 2 ) {
                Toast.makeText(this, "Admin left clicks: ${ clickRequired - counterAdmin }", Toast.LENGTH_SHORT)
                    .show()
            }

        }

//        val navView: BottomNavigationView = binding.navView
//
//        val navController = findNavController(R.id.nav_host_fragment_activity_main)
//        // Passing each menu ID as a set of Ids because each
//        // menu should be considered as top level destinations.
//        val appBarConfiguration = AppBarConfiguration(
//            setOf(
//                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
//            )
//        )
//        setupActionBarWithNavController(navController, appBarConfiguration)
//        navView.setupWithNavController(navController)
    }
}