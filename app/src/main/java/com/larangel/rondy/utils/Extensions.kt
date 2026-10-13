// Extensions.kt
package com.larangel.rondy.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.icu.util.Calendar
import android.os.Build
import android.util.Base64
import com.google.android.gms.maps.model.LatLng
import com.larangel.rondy.AlarmReceiver
import com.larangel.rondy.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
fun String.extraerTAG( bytesToRead: Int = 3, outputFormat: String="Decimal"): String? {
    val regex = if (outputFormat == "Decimal") {
        // --- CASO DECIMAL ---
        // 1 byte max: 255 (3 dígitos)
        // 2 bytes max: 65,535 (5 dígitos)
        // 3 bytes max: 16,777,215 (8 dígitos) -> Tu regla actual
        // 4 bytes max: 4,294,967,295 (10 dígitos)

        val maxDigits = when (bytesToRead) {
            1 -> 3
            2 -> 5
            3 -> 8
            else -> bytesToRead * 3 // Estimación segura para bytes mayores
        }

        // Busca secuencias de números de entre 1 y 'maxDigits' de longitud.
        // Se usa [0-9] para permitir números que comiencen con cero si el lector los entrega así.
        Regex("[0-9]{1,$maxDigits}")
    } else {
        // --- CASO HEXADECIMAL ---
        // 1 byte = 2 caracteres HEX (ej: A4)
        // 2 bytes = 4 caracteres HEX (ej: A4B2)
        // 3 bytes = 6 caracteres HEX -> Tu regla actual
        val hexLength = bytesToRead * 2

        // Busca una secuencia exacta de caracteres Hexadecimales (0-9, A-F)
        Regex("[0-9A-F]{$hexLength}")
    }

    // Buscamos la primera coincidencia en el texto (pasado a mayúsculas por si es HEX)
    return regex.find(this.uppercase())?.value
}
fun String.extraerTAGHexToDec():String?{
    // 1. Limpiar y normalizar la cadena a mayúsculas
    val cleanInput = this.trim().uppercase()

    // 2. Validar que tenga exactamente 24 caracteres y que sean hexadecimales válidos
    val hexRegex = Regex("^[0-9A-F]{24}$")
    if (!hexRegex.matches(cleanInput)) return null

    return try {
        val hexadecimalUtil: String

        // 3. Comprobar si los últimos caracteres son ceros (Opción 1: TAG con ceros de relleno)
        if (cleanInput.endsWith("0")) {
            // Quitamos todos los ceros finales de relleno
            val sinCerosFinales = cleanInput.dropLastWhile { it == '0' }

            // Si la cadena quedó vacía o más corta, tomamos los caracteres útiles restantes
            // Aseguramos un máximo de 8 caracteres (Wiegand 34), rellenando a la izquierda si es necesario
            val utiles = sinCerosFinales.takeLast(8)
            hexadecimalUtil = utiles.padStart(8, '0')
        } else {
            // 4. Si NO termina en ceros (Opción 2: TAG con datos completos)
            // Tomamos estrictamente los últimos 8 caracteres hexadecimales
            hexadecimalUtil = cleanInput.takeLast(8)
        }

        // 5. Convertir el bloque final de 8 caracteres hexadecimales a Decimal
        // Usamos toLong(16) por seguridad, aunque 8 caracteres hex caben justo en un entero sin signo.
        hexadecimalUtil.toLong(16).toString()
    } catch (e: Exception) {
        null
    }
}
/**
 * Extensión para traducir un TAG en base a las configuraciones guardadas.
 * @param offset Byte de inicio de lectura (0 a 11)
 * @param bytesToRead Cantidad de bytes a leer
 * @param outputFormat Formato de salida esperado ("Decimal" o cualquier otro para Hexadecimal)
 * @return El valor traducido o null si el formato intermedio no es válido
 */
fun String.extraerTAGConConfig(offset: Int, bytesToRead: Int, outputFormat: String): String? {
    // 1. Limpiar espacios y tomar la última línea por si viene un bloque con saltos de línea
    val lastValidLine = this.split("\n")
        .map { it.trim() }
        .lastOrNull { it.isNotEmpty() } ?: ""

    // 2. Normalizar a una cadena de 24 caracteres HEX rellenando con ceros a la derecha
    val cleanHex24 = lastValidLine.padEnd(24, '0').substring(0, 24).uppercase()

    // 3. Validar que la cadena normalizada sea hexadecimal válida de 24 caracteres
    val hexRegex = Regex("^[0-9A-F]{24}$")
    if (!hexRegex.matches(cleanHex24)) return null

    return try {
        val startByte = offset
        val endByte = offset + bytesToRead - 1
        val selectedHexBuilder = StringBuilder()

        // 4. Replicar la extracción de bytes según el offset y bytes a leer (1 byte = 2 caracteres hex)
        for (i in 0 until 12) {
            if (i in startByte..endByte) {
                val byteStr = cleanHex24.substring(i * 2, (i * 2) + 2)
                selectedHexBuilder.append(byteStr)
            }
        }
        val hexResult = selectedHexBuilder.toString()

        // 5. Aplicar la conversión final según el formato guardado
        if (outputFormat == "Decimal") {
            // Convierte el fragmento hex extraído a Decimal
            hexResult.toLong(16).toString()
        } else {
            // Retorna el fragmento en Hexadecimal puro
            hexResult
        }
    } catch (e: Exception) {
        null // Retorna null si ocurre un desbordamiento o error de parseo
    }
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
fun buscarTagEnListaCache(tagsCache:List<List<Any>>, tagValue: String): List<List<Any>>{
    //val allLines = strLectorRFID.split("\n")
    var matches: MutableList<List<Any>> = mutableListOf()
    //var tagsBuscados: MutableList<String> = mutableListOf()
    //stopSearchLoop = false
    //for (line in allLines) {
    //    if (stopSearchLoop) return emptyList()
    //    val tagValue = line.extraerTAGHexToDec()

    //    if (tagValue != null && !tagsBuscados.contains(tagValue)){
    //        tagsBuscados.add(tagValue)
            var foundTag=false
            tagsCache?.forEach { tag ->
                if (stopSearchLoop) return@forEach
                val tagId = tag[0].toString()
                // 1. Coincidencia Exacta (Prioridad máxima)
                if (tagId.equals(tagValue, ignoreCase = true)) {
                    foundTag = true
                    matches.clear()
                    matches.add(tag)
                    stopSearchLoop = true
                    return matches
                }

                // 2. Similares (Sugerencias)
                if (tagId.startsWith(tagValue, true) || tagValue.startsWith(tagId)) {
                    foundTag = true
                    matches.add(tag)
                }

            }
            if (foundTag == false) { //No se encontro ninguno
                matches.add(listOf(tagValue,"No registrado","0")) //Tag, calle, numero
            }
    //    }
    //}
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
            if (row.isEmpty()) continue
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
    else if(strPlaca.length > 2 && strPlaca.length<8){
        //Buscar cualquier coincidencia
        var foundTag=false
        for (row in placasCache){
            if (stopSearchLoop) return emptyList()
            if (row.isEmpty()) continue
            val placaID = row[0].toString().uppercase()
            if (placaID.isEmpty()) continue

            if (placaID.contains(strPlaca.uppercase()) ) {
                foundTag=true
                matches.add(row)
            }
        }
        if (foundTag == false) { //No se encontro ninguno
            matches.add(listOf(strPlaca,"No registrado","0")) //Tag, calle, numero
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

//***** Imagenes agregar datos unicos
fun fomatearImagenLogo(context: Context,
                       originalBitmap: Bitmap,
                       location: LatLng?,
                       logoBase64: String?,
                       address: String? = null): Bitmap {
    val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)
    val width = canvas.width.toFloat()
    val height = canvas.height.toFloat()

    // Configuración de pinceles (Paints)
    val textPaint = Paint().apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        textSize = height * 0.08f // Letras grandes
        isAntiAlias = true
    }

    val smallTextPaint = Paint().apply {
        color = Color.WHITE
        typeface = Typeface.SANS_SERIF
        textSize = textPaint.textSize / 4
        isAntiAlias = true
    }

    val linePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 10f
    }

    // 1. Esquina Inferior Izquierda: Hora, Línea y Datos
    val margin = 40f
    val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = sdfTime.format(Date())

    // Dibujar Hora
    val timeBounds = Rect()
    textPaint.getTextBounds(timeStr, 0, timeStr.length, timeBounds)
    val xTime = margin
    val yTime = height - margin
    canvas.drawText(timeStr, xTime, yTime, textPaint)

    // Dibujar Línea Vertical Amarilla
    val xLine = xTime + timeBounds.width() + 30f
    val lineTop = yTime - timeBounds.height()
    canvas.drawLine(xLine, lineTop, xLine, yTime, linePaint)

    //Address
    address?.let{
        canvas.drawText(it.uppercase(), xTime + 20f, lineTop - smallTextPaint.textSize ,smallTextPaint)
    }

    // Dibujar Fecha, Día y GPS al lado de la línea
    val xData = xLine + 30f
    val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val sdfDay = SimpleDateFormat("EEE", Locale.getDefault())

    canvas.drawText(sdfDate.format(Date()), xData, lineTop + (smallTextPaint.textSize), smallTextPaint)
    canvas.drawText(sdfDay.format(Date()).uppercase(), xData, lineTop + (timeBounds.height()/2) + (smallTextPaint.textSize/2), smallTextPaint)

    val latLon = "${"%.4f".format(location?.latitude ?: 0.0)}, ${"%.4f".format(location?.longitude ?: 0.0)}"
    canvas.drawText(latLon, xData, yTime, smallTextPaint)

    // 2. Esquina Superior Derecha: Logo Dinámico
    logoBase64?.let { base64 ->
        decodeBase64ToBitmap(base64)?.let { logoBitmap ->
            val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, (width * 0.15f).toInt(), (width * 0.15f).toInt(), true)
            canvas.drawBitmap(scaledLogo, width - scaledLogo.width - margin, margin, null)
        }
    }

    // 3. Esquina Inferior Derecha: Logo Rondy
    val rondyLogo = BitmapFactory.decodeResource(context.resources, R.drawable.logo)
    val scaledRondy = Bitmap.createScaledBitmap(rondyLogo, (width * 0.12f).toInt(), (width * 0.12f).toInt(), true)
    canvas.drawBitmap(scaledRondy, width - scaledRondy.width - margin, height - scaledRondy.height - margin, null)

    return mutableBitmap
}
private fun decodeBase64ToBitmap(base64: String): Bitmap? {
    return try {
        val imageBytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) { null }
}
fun getAddressFromLocation(context: Context, location: LatLng?): String {
    val geocoder = Geocoder(context, Locale.getDefault())
    return try {
        // En versiones recientes de Android (Tiramisu+), se recomienda usar la versión async.
        // Esta es la versión estándar compatible:
        val addresses: List<Address>? = geocoder.getFromLocation(location!!.latitude, location!!.longitude, 1)

        if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]
            // Construye la dirección (Calle número, Ciudad, País)
            address.getAddressLine(0)
        } else {
            "Dirección no encontrada"
        }
    } catch (e: Exception) {
        e.printStackTrace()
        "Error al obtener dirección: ${e.message}"
    }
}