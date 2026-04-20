// Extensions.kt
package com.larangel.rondy.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import com.larangel.rondy.AlarmReceiver

/**
 * Busca una placa en un texto usando Regex y devuelve el valor o null
 */
fun String.extraerPlaca(): String? {
    val plateRegex = Regex("([A-Z]{3}[0-9]{3,4}[A-Z]?|[0-9]{2}[A-Z][0-9]{3}|[0-9]{3}[A-Z]{3}|[A-Z]{2}[0-9]{4,5}[A-Z]?|[A-Z][0-9]{4}|[A-Z][0-9]{2}[A-Z]{2,3}|[A-Z]{3}[0-9][A-Z]|[A-Z]{5}[0-9]{2})")
    return plateRegex.find(this.uppercase())?.value
}

/**
 * Busca un TAG valido en el texto
 */
fun String.extraerTAG(): String? {
    val TagRegex = Regex("([1-9][0-9]{6,7})")
    return TagRegex.find(this.uppercase())?.value
}

/**
 * Busca un COLOR valido en el texto
 */
fun String.extraerColor(): String? {
    val ColorRegex = Regex("(rojo|verde|azul|magenta|lila|morado|rosa|turquesa|amarillo|blanco|negro|cafe|marron|violeta|naranja|beige|gris|plata)")
    return ColorRegex.find(this.lowercase())?.value
}

/**
 * Busca un MARCA de un auto valido en el texto
 */
fun String.extraerMarcaAuto(): String? {
    val MarcaRegex = Regex("(YAMAHA|Acura|Alfa Romeo|Audi|Auteco|Bentley|BMW|Changan|Chirey|Chrysler|Fiat|Ford Motor|Foton|General Motors|Great Wall Motor|Honda|Hyundai|Infiniti|Isuzu|JAC|Jaguar|JETOUR|KIA|Land Rover|Lexus|Lincoln|Mazda|Mercedes Benz|MG Motor|MG ROVER|Mini|Mitsubishi|MOTORNATION|Nissan|Omoda|Peugeot|Porsche|Renault|SEAT|Smart|Subaru|Suzuki|Toyota|Volkswagen|Volvo)",
        RegexOption.IGNORE_CASE)
    return MarcaRegex.find(this)?.value
}

fun programarAlarma(context:Context, horaStr: String, nombre: String) {
    val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager

    // Validar si tenemos permiso para alarmas exactas (Solo necesario en Android 12+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (!alarmManager.canScheduleExactAlarms()) {
            return // Detenemos la ejecución hasta que tengamos el permiso
        }
    }

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("nombre", nombre)
        putExtra("hora", horaStr)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    // Convertir "HH:mm" a Calendar
    val partes = horaStr.split(":")
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, partes[0].toInt())
        set(Calendar.MINUTE, partes[1].toInt())
        set(Calendar.SECOND, partes.getOrNull(2)?.toInt() ?: 0 )
        if (before(Calendar.getInstance())) {
            add(Calendar.DATE, 1) // Si ya pasó la hora, programar para mañana
        }
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        horaStr.hashCode(), // ID único basado en la hora
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Programar la alarma exacta
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else {
            // Fallback si no hay permiso de alarmas exactas
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    } else {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    }

}

//*** Buscar TAGS/PLACAS validos
var stopSearchLoop = false
fun buscarTagEnListaCache(tagsCache:List<List<Any>>, strLectorRFID: String): List<List<Any>>{
    val allLines = strLectorRFID.split("\n")
    var matches: MutableList<List<Any>> = mutableListOf()
    stopSearchLoop = false
    for (line in allLines) {
        if (stopSearchLoop) return emptyList()
        val tagValue = line.extraerTAG()
        if (tagValue != null){
            var foundTag=false
            tagsCache?.forEach { tag ->
                if (stopSearchLoop) return@forEach
                val tagId = tag[0].toString()

                // 1. Coincidencia Exacta (Prioridad máxima)
                if (tagId.equals(tagValue, ignoreCase = true)) {
                    foundTag=true
                    matches.clear()
                    matches.add( tag )
                    stopSearchLoop = true
                    return matches
                }

                // 2. Similares (Sugerencias)
                if (tagId.startsWith(tagValue, true) || tagValue.startsWith(tagId)) {
                    foundTag=true
                    matches.add(tag)
                }
            }
            if (foundTag == false) { //No se encontro ninguno
                matches.add(listOf(tagValue,"No registrado","0")) //Tag, calle, numero
            }
        }
    }
    return matches
}
fun buscarPlacaEnListaCache(placasCache:List<List<Any>>, strPlaca:String):List<List<Any>>{
    var matches: MutableList<List<Any>> = mutableListOf()
    stopSearchLoop = false
    val placaValue = strPlaca.extraerPlaca()
    if (placaValue != null){
        var foundTag=false
        for (row in placasCache) {
            if (stopSearchLoop) return emptyList()
            val placaID = row[0].toString()
            if (placaID.isEmpty()) continue

            // 1. Coincidencia Exacta (Prioridad máxima)
            if (placaID.equals(placaValue, ignoreCase = true)) {
                foundTag=true
                matches.clear()
                matches.add( row )
                stopSearchLoop = true
                return matches
            }

            // 2. Similares (Sugerencias)
            else if (placaID.startsWith(placaValue, true) ) {
                foundTag=true
                matches.add(row)
            }

            // 3. Una letra de diferncia
            else if( oneCharDifference(placaID, placaValue)) {
                foundTag=true
                matches.add(row)
            }
        }
        if (foundTag == false) { //No se encontro ninguno
            matches.add(listOf(placaValue,"No registrado","0")) //Tag, calle, numero
        }
    }
    return matches
}

fun oneCharDifference(a: String, b: String): Boolean {
    // Devuelve true si solo difiere en una letra/número
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) {
        if (a[i] != b[i]) diff++
        if (diff > 1) return false
    }
    return diff == 1
}