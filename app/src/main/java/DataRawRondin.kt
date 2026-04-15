import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.TextView
import android.widget.Toast
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest
import com.google.api.services.sheets.v4.model.DimensionRange
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.ValueRange
import com.larangel.rondy.R
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.minutes
import android.util.Log
import com.larangel.rondy.utils.extraerColor
import com.larangel.rondy.utils.extraerMarcaAuto
import com.larangel.rondy.utils.extraerPlaca
import com.larangel.rondy.utils.extraerTAG
import java.security.Permission
import java.time.LocalTime
import java.time.format.DateTimeFormatterBuilder

enum class SheetTable(
    var sheetName: String,
    val cacheKey: String,
    val range: String = "A:Z"
) {
    PARKING_SLOTS("ParkingSlots", "parkingSlots", "A:C"), //Latitud, Longitud, ParkingKeySlot
    AUTOS_EVENTOS("AutosEventos", "autosEventos", "A:E"), // placa, date, time, localPhotoPath, ParkingSlotKey
    INCIDENCIAS("IncidenciaEventos", "incidenciaEventos", "A:G"), // calle, numero, date, time, Tipo, localPhotoPath, descripcion
    INCIDENCIAS_CONFIG("IncidenciaConfig", "incidenciaConfig", "A:D"),// key, textoButton, maxWarning, descLegal
    POR_REVISAR("PorRevisar", "porRevisar", "A:G"),
    MULTAS("MultasGeneradas", "MultaGenerada", "A:E"), // Fecha, Calle,	Numero,	Placa, PerkingSlot - (Fecha)
    DOMICILIO_WARNINGS("DomicilioWarnings", "DomicilioWarnings", "A:D"),
    VEHICULOS("AutosRegistrados", "VEHICLE", "A:C"),
    TAGS("AutosRegistrados", "TAGS", "A:H"),
    PERMISOS("", "PERMISOS", "A:N"),
    DIRECCIONES("Direcciones", "directions", "A:D"),  // calle, numero, latitud, longitud
    AUTOS_REGISTRADOS("AutosRegistrados", "autosRegistrados", "A:I"), //placa,calle,numero,marca,modelo,color,tag
    RESIDENTES_UNIDAD("ResidentesUnidad", "residentesUnidad", "A:Q"),
    ALARMAS_RONDIN("AlarmasRondin","alarmasRondin","A:B"); //userid,clave,calle,numero,tipo,nombre,telefono,email,celular,notas,ciudad,estado,fecha_updated_condovive,fecha_updated_app,es_nuevo,es_actualizado,es_eliminado


    // Generadores automáticos de llaves de caché
    val saveKey get() = "CACHE_forSave_$cacheKey"
    val updateKey get() = "CACHE_forUpdate_$cacheKey"
    val updateIdxKey get() = "CACHE_forUpdate_${cacheKey}Index"
    val deleteIdxKey get() = "CACHE_forDelete_${cacheKey}Index"
    val timestampKey get() = "${cacheKey}_CACHE_TIMESTAMP"

    companion object {
        fun initializeAll(settings: MySettings?) {
            if (settings == null) return

            // Mapeamos cada ENUM a su respectiva llave de configuración
            val configKeys = mapOf(
                PARKING_SLOTS to "WS_PARKING_SLOTS",
                AUTOS_EVENTOS to "WS_AUTOS_EVENTOS",
                INCIDENCIAS to "WS_INCIDENCIAS_EVENTOS",
                INCIDENCIAS_CONFIG to "WS_INCIDENCIAS_CONFIG",
                POR_REVISAR to "WS_POR_REVISAR",
                MULTAS to "WS_MULTAS_GENERADAS",
                DOMICILIO_WARNINGS to "WS_DOMICILIO_WARNINGS",
                VEHICULOS to "WS_AUTOS_REGISTRADOS",
                TAGS to "WS_AUTOS_REGISTRADOS",
                //PERMISOS to "WS_PERMISOS", --
                DIRECCIONES to "WS_DOMICILIOS_UBICACION",
                AUTOS_REGISTRADOS to "WS_AUTOS_REGISTRADOS",
                RESIDENTES_UNIDAD to "WS_RESIDENTES_UNIDAD",
                ALARMAS_RONDIN to "WS_ALARMAS_RONDIN",
            )

            // Recorremos el mapa y actualizamos cada sheetName
            configKeys.forEach { (entry, settingKey) ->
                entry.sheetName = settings.getString(settingKey, entry.sheetName)
            }
        }
    }
}

enum class Operation { APPEND, UPDATE, DELETE }



class DataRawRondin(private val context: Context, private val coroutineScopeObject: CoroutineScope ) {

    private val mySettings = MySettings(context)
    private lateinit var sheetsService: Sheets
    private val TAG = "package:com.larangel.rondy"
    private val CACHE_DURATION_MS = 60 * 60 * 1000 // 1 hora

//    private val flexibleDateFormatter = DateTimeFormatterBuilder()
//        .appendOptional(DateTimeFormatter.ofPattern("d/M/yyyy"))
//        .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
//        .appendOptional(DateTimeFormatter.ofPattern("d/M/yy"))
//        .appendOptional(DateTimeFormatter.ofPattern("d/M/yyyy"))
//        .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yy"))
//        .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
//        .appendOptional(DateTimeFormatter.ofPattern("M/dd/yyyy"))
//        .toFormatter()

//    private val flexibleDateTimeFormatter = DateTimeFormatterBuilder()
//        .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
//        .appendOptional(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
//        .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
//        .appendOptional(DateTimeFormatter.ofPattern("d/MM/yyyy H:mm:ss"))
//        .appendOptional(DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss"))
//        .appendOptional(DateTimeFormatter.ofPattern("d/M/yy HH:mm:ss"))
//        .appendOptional(DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss"))
//        .appendOptional(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
//        .toFormatter()

    private fun parseLenientDateTime(dateTimeString: String): LocalDateTime {
        val formats = listOf(
            "d/MM/yyyy H:mm:ss",
            "d/MM/yyyy HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd'T'HH:mm:ss",
            "dd-MM-yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss",
            "MM-dd-yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss",
            "yyyyMMdd HHmmss",
            "yyyyMMdd'T'HHmmss"
        ).map { DateTimeFormatter.ofPattern(it) }

        for (format in formats) {
            try {
                return LocalDateTime.parse(dateTimeString, format)
            } catch (e: Exception) {
                // Try the next format if parsing fails
            }
        }
        return LocalDateTime.MIN // Return null if no format matches
    }
    private fun parseLenientDate(dateTimeString: String): LocalDate {
        val formats = listOf(
            "d/MM/yyyy",
            "yyyy/MM/dd",
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "MM-dd-yyyy",
            "MM/dd/yyyy",
            "M/dd/yyyy",
            "yyyyMMdd"
        ).map { DateTimeFormatter.ofPattern(it) }

        for (format in formats) {
            try {
                return LocalDate.parse(dateTimeString, format)
            } catch (e: Exception) {
                // Try the next format if parsing fails
            }
        }
        return LocalDate.MIN // Return null if no format matches
    }


    // Estado en Memoria
    private val tableStates = mutableMapOf<SheetTable, TableState>().apply {
        SheetTable.values().forEach { put(it, TableState()) }
    }
    private val activeSyncJobs = mutableSetOf<String>()

    class TableState {
        var cache: List<List<Any>>? = null
        var timestamp: Long = 0
        val forSave = mutableListOf<List<Any>>()
        val forUpdate = mutableListOf<List<Any>>()
        val forUpdateIndexes = mutableListOf<Int>()
        val forDeleteIndexes = mutableListOf<Int>()
    }

    init {
        initializeGoogleServices()
        checarPendientesAlInicio()
    }

    private fun initializeGoogleServices() {
        val serviceAccountStream = context.resources.openRawResource(R.raw.json_google_service_account)
        val credential = GoogleCredential.fromStream(serviceAccountStream)
            .createScoped(listOf("https://www.googleapis.com/auth/drive", "https://www.googleapis.com/auth/spreadsheets"))
        sheetsService = Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("My First Project").build()

    }
    private fun checarPendientesAlInicio() {
        SheetTable.values().forEach { table ->
            val s = tableStates[table]!!
            // Cargar de disco a RAM y disparar sync si hay datos
            val saved = mySettings.getList(table.saveKey)
            if (saved.isNotEmpty()) {
                s.forSave.addAll(saved); sync(table, Operation.APPEND)
            }
            // UPDATE...
            val updated = mySettings.getList(table.updateKey)
            val updatedIndex = mySettings.getSimpleList(table.updateIdxKey).map { it.toInt() }
            if (updated.isNotEmpty()) {
                s.forUpdate.addAll(updated);
                s.forUpdateIndexes.addAll(updatedIndex)
                sync(table, Operation.UPDATE)
            }
            //DELETE
            val deletedIndex = mySettings.getSimpleList(table.deleteIdxKey).map { it.toInt() }
            if(deletedIndex.isNotEmpty()){
                s.forDeleteIndexes.addAll(deletedIndex);
                sync(table, Operation.DELETE)
            }
        }
    }
    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }else {
            // Para versiones de Android anteriores a Marshmallow (API 23)
            val networkInfo = cm.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }
    fun sonCadenasSimilares(str1: String, str2: String): Boolean {

        // 1. Limpiar: Solo letras y números, y todo a Mayúsculas
        val s1 = str1.filter { it.isLetterOrDigit() }.uppercase()
        val s2 = str2.filter { it.isLetterOrDigit() }.uppercase()

        // 2. Si son idénticas después de limpiar
        if (s1 == s2) return true

        // 3. Si la diferencia de longitud es mayor a 1, no pueden ser similares
        if (Math.abs(s1.length - s2.length) > 1) return false

        // 4. Comparación de diferencia por un solo caracter (Levenshtein simplificado)
        var i = 0
        var j = 0
        var errores = 0

        while (i < s1.length && j < s2.length) {
            if (s1[i] != s2[j]) {
                errores++
                if (errores > 1) return false

                // Si las longitudes son distintas, avanzamos solo el índice de la cadena más larga
                if (s1.length > s2.length) i++
                else if (s2.length > s1.length) j++
                else { // Misma longitud, avanzamos ambos (es una sustitución)
                    i++
                    j++
                }
            } else {
                i++
                j++
            }
        }

        // Considerar un caracter extra al final de la cadena más larga
        if (i < s1.length || j < s2.length) errores++

        return errores <= 1

    }

    // --- SECCIÓN 1: LECTURA Y CACHÉ INTELIGENTE ---
    suspend fun getSmartCache(table: SheetTable, forceLoad: Boolean = false,fetcher: suspend () -> List<List<Any>>): List<List<Any>> {
        val state = tableStates[table]!!
        val now = System.currentTimeMillis()
        val isConnected = isNetworkAvailable()

        // 1. RAM
        if (state.cache != null && (now - state.timestamp <= CACHE_DURATION_MS || !isConnected) && !forceLoad)
            return state.cache!!

        // 2. Disco
        val diskCache = mySettings.getList("${table.cacheKey}_CACHE") ?: emptyList()
        val diskTs = mySettings.getLong(table.timestampKey, 0L)
        if (diskCache.isNotEmpty() && (now - diskTs <= CACHE_DURATION_MS || !isConnected) && !forceLoad) {
            state.cache = diskCache; state.timestamp = diskTs
            return diskCache
        }

        // 3. Red
        return if (isConnected) {
            withContext(Dispatchers.IO) {
                try {
                    val freshData = fetcher()
                    mySettings.saveList("${table.cacheKey}_CACHE", freshData as List<List<String>>)
                    mySettings.saveLong(table.timestampKey, now)
                    state.cache = freshData; state.timestamp = now
                    freshData
                } catch (e: Exception) {
                    Log.e(TAG, "Error leyendo datos en red(${table.cacheKey}), error: ${e.message}")
                    diskCache
                }
            }
        } else diskCache
    }

    // --- SECCIÓN 2: ESCRITURA Y REINTENTOS ---
    fun sync(table: SheetTable, op: Operation, data: List<String>? = null, index: Int? = -1) {
        val state = tableStates[table]!!
        val jobId = "${table.name}_${op.name}"

        // Persistencia inmediata
        when (op) {
            Operation.APPEND -> data?.let {
                state.forSave.add(it)
                mySettings.saveList(table.saveKey, state.forSave as List<List<String>>)
            }
            Operation.UPDATE -> if (data != null && index != null && index >= 0) {
                state.forUpdate.add(data); state.forUpdateIndexes.add(index)
                mySettings.saveList(table.updateKey, state.forUpdate as List<List<String>>)
                mySettings.saveSingleList(table.updateIdxKey, state.forUpdateIndexes.map { it.toString() })
            }
            Operation.DELETE -> if (index != null && index >= 0) {
                state.forDeleteIndexes.add(index)
                mySettings.saveSingleList(table.deleteIdxKey, state.forDeleteIndexes.map { it.toString() })
            }
        }

        if (activeSyncJobs.contains(jobId)) return

        coroutineScopeObject.launch {
            activeSyncJobs.add(jobId)
            while (isActive) {
                val success = try {
                    when (op) {
                        Operation.APPEND -> executeAppend(table)
                        Operation.UPDATE -> executeUpdate(table)
                        Operation.DELETE -> executeDelete(table)
                    }
                } catch (e: Exception) { false }

                if (success) break
                delay(5.minutes)
            }
            activeSyncJobs.remove(jobId)
        }
    }

    // --- IMPLEMENTACIONES DE BAJO NIVEL (GOOGLE SHEETS) ---
    private suspend fun executeAppend(table: SheetTable): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) return@withContext false
        val state = tableStates[table]!!
        var spreadId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
        var rangeStr = "${table.sheetName}!${table.range}"
        if (table == SheetTable.PERMISOS) {
            rangeStr = table.range
            spreadId = mySettings.getSimpleList("PERMISOS_SPREADSHEET_ID")[0]
        }
        if (spreadId.isEmpty()) return@withContext false

        try {
            val body = ValueRange().setValues(state.forSave)
            sheetsService.spreadsheets().values().append(spreadId, rangeStr, body)
                .setValueInputOption("RAW").execute()
            state.forSave.clear()
            mySettings.saveList(table.saveKey, emptyList<List<String>>())
            true
        } catch (e: Exception) { false }
    }
    private suspend fun executeUpdate(table: SheetTable): Boolean = withContext(Dispatchers.IO) {
        val state = tableStates[table]!!
        var spreadId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
        val iterator = state.forUpdate.indices.reversed()
        var rangeStr = "${table.sheetName}!${table.range}"

        if (table == SheetTable.PERMISOS) {
            rangeStr = table.range
            spreadId = mySettings.getSimpleList("PERMISOS_SPREADSHEET_ID")[0]
        }
        if (spreadId.isEmpty()) return@withContext false

        for (i in iterator) {
            if (!isNetworkAvailable()) return@withContext false
            val row = state.forUpdate[i]
            val idx = state.forUpdateIndexes[i]
            val range = "${table.sheetName}!A$idx:Z$idx"

            try {
                sheetsService.spreadsheets().values().update(spreadId, range, ValueRange().setValues(listOf(row)))
                    .setValueInputOption("RAW").execute()
                state.forUpdate.removeAt(i); state.forUpdateIndexes.removeAt(i)
            } catch (e: Exception) { return@withContext false }
        }
        mySettings.saveList(table.updateKey, state.forUpdate as List<List<String>>)
        mySettings.saveSingleList(table.updateIdxKey, state.forUpdateIndexes.map { it.toString() })
        true
    }
    private suspend fun executeDelete(table: SheetTable): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) return@withContext false

        val state = tableStates[table] ?: return@withContext true
        if (state.forDeleteIndexes.isEmpty()) return@withContext true

        var spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "") ?: ""
        var rangeStr = "${table.sheetName}!${table.range}".toString()

        if (table == SheetTable.PERMISOS) {
            rangeStr = table.range.toString()
            spreadsheetId = mySettings.getSimpleList("PERMISOS_SPREADSHEET_ID")[0].toString()
        }

        if (spreadsheetId.isEmpty()) return@withContext false

        try {
            // 1. Obtener el ID numérico de la hoja (SheetId) si no lo tenemos
            val sheetId = getSheetIdByName(spreadsheetId, table.sheetName) ?: return@withContext false

            // 2. Crear una lista de peticiones (Requests).
            // IMPORTANTE: Para borrar múltiples filas, debemos ordenarlas de MAYOR a MENOR
            // para que el borrado de una fila no cambie la posición de las siguientes.
            val sortedIndexes = state.forDeleteIndexes.distinct().sortedDescending()

            val requests = sortedIndexes.map { index ->
                Request().setDeleteDimension(
                    DeleteDimensionRequest().setRange(
                        DimensionRange()
                            .setSheetId(sheetId)
                            .setDimension("ROWS")
                            .setStartIndex(index - 1) // Google Sheets es 0-indexed
                            .setEndIndex(index)
                    )
                )
            }

            // 3. Ejecutar el Batch Update
            val batchRequest = BatchUpdateSpreadsheetRequest().setRequests(requests)
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute()

            // 4. Limpiar datos locales tras éxito
            state.forDeleteIndexes.clear()
            mySettings.saveSingleList(table.deleteIdxKey, emptyList<String>())

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error en executeDelete para ${table.sheetName}: ${e.message}")
            false
        }
    }
    private fun getSheetIdByName(spreadsheetId: String, sheetName: String): Int? {
        return try {
            val spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute()
            if (sheetName.isNotEmpty()) {
                spreadsheet.sheets.find { it.properties.title == sheetName }?.properties?.sheetId
            }else{
                spreadsheet.sheets.first()?.properties?.sheetId
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo obtener el ID de la hoja $sheetName")
            null
        }
    }


    // VEHICULOS
    fun getCachedVehiclesData(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Llamamos a la función genérica usando el Enum de VEHICULOS
        getSmartCache(SheetTable.VEHICULOS,forceLoad) {
            val allRows = mutableListOf<List<Any>>()

            // 1. Obtener Vehículos de RESIDENTES
            val stateResidentes = tableStates[SheetTable.RESIDENTES_UNIDAD]

            // 2. Aseguramos que la RAM tenga datos (Carga desde RAM -> Disco -> Red)
            if (stateResidentes?.cache == null) runBlocking { getResidentes() }
            stateResidentes?.cache?.forEach { row ->
                //tipo == automovil
                if (row[4].toString().startsWith("auto",true)){
                    val placa: String? = row.firstNotNullOfOrNull { it.toString().extraerPlaca() }
                    if (placa != null ){
                        val calle = row[2].toString()
                        val numero= row[3].toString()
                        allRows.add(listOf(placa,calle,numero))
                    }
                }
            }

//            val residentsId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
//            val residentsSheet = SheetTable.VEHICULOS.sheetName //mySettings.getString("WS_AUTOS_REGISTRADOS", "AutosRegistrados")!!
//
//            if (residentsId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")
//
//            //try {
//                val response = sheetsService.spreadsheets().values()
//                    .get(residentsId, "$residentsSheet!A:C") // placa, calle, numero
//                    .execute()
//                response.getValues().drop(1)?.let { allRows.addAll(it) }
//            //} catch (e: Exception) {
//            //    Log.e(TAG, "Error leyendo Residentes: ${e.message}")
//            //}

            // 2. Obtener Vehículos VISITANTES (Hojas: ingreso y salida)
            val visitorsId = mySettings.getString("REGISTRO_CARROS_SPREADSHEET_ID", "")
            if (!visitorsId.isNullOrEmpty()) {
                val visitorSheets = listOf("ingreso", "salida")
                for (sheetName in visitorSheets) {
                    //try {
                        val response = sheetsService.spreadsheets().values()
                            .get(visitorsId, "$sheetName!C:E") // placa, calle, numero
                            .execute()
                        // drop(1) para omitir los encabezados de la tabla de visitantes
                        val rows = response.getValues()?.drop(1) ?: emptyList()
                        allRows.addAll(rows)
                    //} catch (e: Exception) {
                    //    Log.e(TAG, "Error leyendo Visitantes ($sheetName): ${e.message}")
                    //}
                }
            }

            // Retornamos la lista unificada al SmartCache para que la guarde en RAM y Disco
            allRows
        }
    }
    fun getTagsCache(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        getSmartCache(SheetTable.TAGS,forceLoad) {
            val allParsedTags = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
            val nameWS = SheetTable.TAGS.sheetName //mySettings.getString("WS_AUTOS_REGISTRADOS", "AutosRegistrados")!!
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // 1. Obtener Vehículos de RESIDENTES
            val stateResidentes = tableStates[SheetTable.RESIDENTES_UNIDAD]

            // 2. Aseguramos que la RAM tenga datos (Carga desde RAM -> Disco -> Red)
            if (stateResidentes?.cache == null) runBlocking { getResidentes() }
            stateResidentes?.cache?.forEach { row ->
                //tipo == automovil
                if (row[4].toString().startsWith("auto",true)){
                    val tagValue: String? = row.firstNotNullOfOrNull { it.toString().extraerTAG() }
                    if (tagValue != null ){
                        val calle = row[2].toString()
                        val numero= row[3].toString()
                        allParsedTags.add(listOf(tagValue,calle,numero))
                    }
                }
            }

//            //try {
//            // Obtenemos el rango A:H (Placas, Calle, Numero, Marca, Modelo, Color, Tag1, Tag2, userid)
//            val response = sheetsService.spreadsheets().values()
//                .get(spreadsheetId, "$nameWS!A:I")
//                .execute()
//
//            // Omitimos el encabezado
//            val rows = response.getValues()?.drop(1) ?: emptyList()
//
//            rows.forEach { row ->
//                // Índices: Calle(1), Numero(2), Tag1(6), Tag2(7), userid(8)
//                val calle = row.getOrNull(1)?.toString() ?: ""
//                val numero = row.getOrNull(2)?.toString() ?: ""
//
//                // Procesamos cada columna de Tag si existe y no está vacía
//                for (i in 6..7) {
//                    val tagValue = row.getOrNull(i)?.toString()
//                    if (!tagValue.isNullOrBlank()) {
//                        allParsedTags.add(listOf(tagValue, calle, numero))
//                    }
//                }
//            }
//            //} catch (e: Exception) {
//            //    Log.e(TAG, "Error procesando Tags desde Google Sheets: ${e.message}")
//            //}
//
//            // Retornamos la lista de [Tag, Calle, Numero]
            allParsedTags
        }
    }
    fun getAutoRegistrados(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Llamamos a la función genérica usando el Enum de VEHICULOS
        getSmartCache(SheetTable.AUTOS_REGISTRADOS,forceLoad) {
            val allRows = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // 1. Obtener Vehículos de RESIDENTES
            val stateResidentes = tableStates[SheetTable.RESIDENTES_UNIDAD]

            // 2. Aseguramos que la RAM tenga datos (Carga desde RAM -> Disco -> Red)
            if (stateResidentes?.cache == null) runBlocking { getResidentes() }
            stateResidentes?.cache?.forEach { row ->
                //tipo == automovil
                if (row[4].toString().startsWith("auto",true)){
                    val placa: String? = row.firstNotNullOfOrNull { it.toString().extraerPlaca() }
                    val tag: String? = row.firstNotNullOfOrNull { it.toString().extraerTAG() }
                    if (placa != null ){
                        val userid= row[0].toString()
                        val calle = row[2].toString()
                        val numero= row[3].toString()
                        val color = row.firstNotNullOfOrNull { it.toString().extraerColor() }
                        val marca = row.firstNotNullOfOrNull { it.toString().extraerMarcaAuto() }
                        allRows.add(listOf<Any>(
                            placa,
                            calle,
                            numero,
                            marca.toString(),
                            "",
                            color.toString(),
                            tag.toString(),
                            "",
                            userid))
                    }
                }
            }

            allRows
        }
    }
    private fun _get_strclave_unidad(calle: String, numero: String): String{
        val abreviations = mapOf(
            "casaclub" to "CA",
            "acantilado" to "AC",
            "cipres" to "CI",
            "ciruelo" to "CR",
            "durazno" to "DR",
            "encino" to "EN",
            "enramada" to "ER",
            "eucalipto" to "EC",
            "guadalupe" to "GP",
            "naranjo" to "NR",
            "manzano" to "MN",
            "mezquite" to "MZ",
            "olmo" to "OL",
            "primavera" to "PR",
            "roble" to "RB",
            "administracion" to "AD",
            "prueba" to "PB"
        )

        val calleNormalizada = calle.lowercase().trim()
        val abrev = abreviations[calleNormalizada] ?: calleNormalizada.take(3).uppercase()
        val numFormateado = numero.trim().padStart(3, '0')

        return "$abrev$numFormateado"
    }
    fun addAutoRegistrados(row: List<String>): Boolean{
        val table = SheetTable.AUTOS_REGISTRADOS
        val state = tableStates[table] ?: return false
        val userid = if(row[8].toIntOrNull() != null) row[8].toInt() else LocalTime.now().toSecondOfDay() * -1
        //SET UserID
        val _row = row.toMutableList()
        _row[8] = userid.toString()

        //### CACHE VIRTUAL DE AUTOS
        // 1. Aseguramos que la RAM tenga datos (si estaba nulo, lo cargamos)
        if (state.cache == null) {
            runBlocking { getAutoRegistrados() }
        }

        // 2. Actualizar RAM (Optimistic UI: el usuario ve el cambio al instante)
        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        currentCache.add(_row)
        state.cache = currentCache

        // 3. Persistir el cambio visual en el caché de disco (MySettings)
        mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
        mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

        // 4. Salvar async
        mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
        sync(table, Operation.APPEND, data = _row)
        //### FIN CACHE VIRTUAL DE AUTOS

        //###### GUARDAR EN RESIDENTES ######
        if (userid.toInt() != -987654321) {
            val strNowTime =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val rowResidente = listOf<Any>(
                userid.toString(),
                _get_strclave_unidad(row[1].toString(), row[2].toString()), //Clave
                row[1].toString(),                                          //calle
                row[2].toString(),                                          //numero
                "Automóvil",                                                //Tipo
                "${row[3]} ${row[4]} ${row[5]}",                            //nombre
                row[0],                                                     //telefono (placa)
                "",                                                         //email
                row[6],                                                     //celular (tag)
                "RondyApp[${strNowTime}]",                                  //notas
                "",                                                         //ciudad
                "",                                                         //estado
                "2000-01-01 00:00:00",                                      //fecha_updated_condovive
                strNowTime,                                                 //fecha_updated_app
                "1",                                                        //es_nuevo
                "0",                                                        //es_actualizado
                "0"                                                         //es_eliminado
            )
            updateResidentes(rowResidente as List<String>)
        }

        return true
    }
    fun updateAutoRegistrados(newData: List<String>): Boolean{
        val table = SheetTable.AUTOS_REGISTRADOS
        val state = tableStates[table] ?: return false

        // 1. Aseguramos que la RAM tenga datos (Carga desde RAM -> Disco -> Red)
        if (state.cache == null) runBlocking { getAutoRegistrados() }

        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        var indexFind = -1

        // 2. Búsqueda por userID
        currentCache.forEachIndexed { index, autoR ->
            if (autoR.size >= 4
                && autoR[8] == newData[8] ){
                indexFind = index
                return@forEachIndexed
            }
        }

        if (indexFind >= 0) {
            // CASO A: ACTUALIZAR EXISTENTE
            currentCache[indexFind] = newData
            state.cache = currentCache

            // Persistir en disco para acceso offline inmediato
            mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
            mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

            // Sincronizar Update (index + 2 por el encabezado de Google Sheets)
            sync(table, Operation.UPDATE, data = newData, index = indexFind + 2)

            //UPDATE RESIDENTE
            val strNowTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val rowResidente = listOf<Any>(
                newData[8],                                                 //userid
                _get_strclave_unidad(newData[1].toString(), newData[2].toString()), //Clave
                newData[1].toString(),                                      //calle
                newData[2].toString(),                                      //numero
                "Automóvil",                                                //Tipo
                "${newData[3]} ${newData[4]} ${newData[5]}",                //nombre
                newData[0],                                                 //telefono (placa)
                "",                                                         //email
                newData[6],                                                 //celular (tag)
                "RondyApp[${strNowTime}]",                                  //notas
                "",                                                         //ciudad
                "",                                                         //estado
                strNowTime,                                                 //fecha_updated_condovive
                strNowTime,                                                 //fecha_updated_app
                "0",                                                        //es_nuevo
                "1",                                                        //es_actualizado
                "0"                                                         //es_eliminado
            )
            updateResidentes(rowResidente as List<String>)

        } else {
            // CASO B: ES NUEVO (APPEND)
            addAutoRegistrados(newData)
        }

        return true
    }

    //RESIDENTES
    fun getResidentes(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        //userid,clave,calle,numero,tipo,nombre,telefono,email,celular,notas,ciudad,estado,fecha_updated_condovive,fecha_updated_app,es_nuevo,es_actualizado,es_eliminado
        // Usamos el Enum  el SmartCache genérico
        getSmartCache(SheetTable.RESIDENTES_UNIDAD,forceLoad) {
            val allRows = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            val nameWS = SheetTable.RESIDENTES_UNIDAD.sheetName

            // Leemos el rango configurado (ej. A:C para Calle, Número, ID)
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "$nameWS!${SheetTable.RESIDENTES_UNIDAD.range}")
                .execute()

            // Omitimos encabezados si es necesario con .drop(1)
            val rows = response.getValues().drop(1) ?: emptyList()
            allRows.addAll(rows)

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allRows
        }
    }
    fun updateResidentes( rowData:List<String>): Boolean{
        val table = SheetTable.RESIDENTES_UNIDAD
        val state = tableStates[table] ?: return false

        // 1. Aseguramos que la RAM tenga datos (Carga desde RAM -> Disco -> Red)
        if (state.cache == null) runBlocking { getResidentes() }

        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        var indexFind = -1

        // 2. Búsqueda por Calle(0), Número(1) y Tipo(3)
        currentCache.forEachIndexed { index, resUni ->
            if (resUni.size >= 4 &&
                resUni[0].toString().toInt() == rowData[0].toString().toInt()) { //Validando el userid
                indexFind = index
                return@forEachIndexed
            }
        }

        if (indexFind >= 0) {
            // CASO A: ACTUALIZAR EXISTENTE
            currentCache[indexFind] = rowData
            state.cache = currentCache

            // Persistir en disco para acceso offline inmediato
            mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
            mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

            // Sincronizar Update (index + 2 por el encabezado de Google Sheets)
            sync(table, Operation.UPDATE, data = rowData, index = indexFind + 2)
        } else {
            // CASO B: ES NUEVO (APPEND)
            currentCache.add(rowData)
            state.cache = currentCache

            mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
            sync(table, Operation.APPEND, data = rowData)
        }

        return true
    }

    // Permisos
    fun getPermisosCache(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Usamos el Enum de PERMISOS y el SmartCache genérico
        getSmartCache(SheetTable.PERMISOS,forceLoad) {
            val allPermisos = mutableListOf<List<Any>>()

            // Obtenemos la lista de IDs de Spreadsheets desde MySettings
            val spreadsheetIds = mySettings.getSimpleList("PERMISOS_SPREADSHEET_ID") ?: emptyList<String>()
            if (spreadsheetIds.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            for (id in spreadsheetIds) {
                //try {
                    // Consultamos el rango A:N (desde Marca temporal hasta Procesado por ROBOT)
                    val response = sheetsService.spreadsheets().values()
                        .get(id, "A:N")
                        .execute()

                    // Omitimos la primera fila (encabezados) de cada hoja
                    val rows = response.getValues()?.drop(1) ?: emptyList()
                    allPermisos.addAll(rows)

                //} catch (e: Exception) {
                //    Log.e(TAG, "Error leyendo permisos del ID: $id - ${e.message}")
                //}
            }

            // Retornamos la lista consolidada al SmartCache
            allPermisos
        }
    }
    fun getPermisosCache_DeHoy(forceLoad: Boolean = false): List<List<Any>> {
        val rows = getPermisosCache(forceLoad)
        val stringTrue = arrayOf("1", "Si", "si", "SI", "x", "X")
        if (rows.isEmpty()) return emptyList()

        val hoy = LocalDate.now()
        val esAdmin = mySettings?.getInt("ESADMIN",0)

        return rows.filter { row ->
            try {
                // Índices basados en tu Parte 6: Fecha Inicio(7), Fecha Fin(8)
                val fechaInicioStr = row.getOrNull(7)?.toString() ?: ""
                val fechaFinStr = row.getOrNull(8)?.toString() ?: ""
                val procesadoRobot = row.getOrNull(13)?.toString() ?: ""

                if ( !stringTrue.contains(procesadoRobot) && esAdmin == 1){ //Si es Admin regresar los no validados
                    //No ha sido procesado y es admin
                    true
                }
                else if (fechaInicioStr.isNotBlank() && fechaFinStr.isNotBlank() && stringTrue.contains(procesadoRobot)) {
                    //Esta dentro de las fechas y fue procesado
                    val inicio = parseLenientDate(fechaInicioStr)
                    val fin = parseLenientDate(fechaFinStr)
                    // Verificamos si hoy está dentro del rango (inclusive)
                    if(esAdmin == 1)
                        hoy <= fin
                    else
                        hoy >= inicio && hoy <= fin
                }
                else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al parsear fechas en fila: $row e:${e.message}")
                false
            }
        }.reversed() // Los más recientes primero
    }
    fun updatePermisoCache(row: List<String>): Boolean{
        val _row: MutableList<String> = row as MutableList<String>
        val table = SheetTable.PERMISOS
        val state = tableStates[table] ?: return false

        // 1. Aseguramos que la RAM tenga datos (Carga desde RAM -> Disco -> Red)
        if (state.cache == null) runBlocking { getPermisosCache() }

        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        var indexFind = -1

        // 2. Búsqueda por fecha de creacion
        val pCreadp = parseLenientDateTime(row[0].toString())
        currentCache.forEachIndexed { index, permiso ->
            val _fCreado = parseLenientDateTime(permiso[0].toString())
            if (permiso.size >= 4 &&
                pCreadp == _fCreado ) {
                indexFind = index
                //Perservar valores de fecha
                _row[0] = permiso[0].toString() //MarcaTemporal
                _row[7] = permiso[7].toString() //Fecha Inicio
                _row[8] = permiso[8].toString() //Fecha Fin
                return@forEachIndexed
            }
        }

        if (indexFind >= 0) {
            // CASO A: ACTUALIZAR EXISTENTE
            currentCache[indexFind] = row as List<String>
            state.cache = currentCache

            // Persistir en disco para acceso offline inmediato
            mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
            mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

            // Sincronizar Update (index + 2 por el encabezado de Google Sheets)
            sync(table, Operation.UPDATE, data = row, index = indexFind + 2)
        } else {
            return false
        }

        return true
    }
    fun eliminarPermisoCache(row: List<String>): Boolean{
        val state = tableStates[SheetTable.PERMISOS] ?: return false

        var indexFind = -1
        // 1. Aseguramos que la RAM tenga datos (Carga desde RAM -> Disco -> Red)
        if (state.cache == null) runBlocking { getPermisosCache() }
        val currentCache = state.cache?.toMutableList() ?: return false

        // 2. Búsqueda por fecha de creacion
        val pCreadp = parseLenientDateTime(row[0].toString())
        currentCache.forEachIndexed { index, permiso ->
            val _fCreado = parseLenientDateTime(permiso[0].toString())
            if (permiso.size >= 4 &&
                pCreadp == _fCreado ) {
                indexFind = index
                return@forEachIndexed
            }
        }

        if (indexFind >= 0) {
            // 2. Eliminar de la RAM inmediatamente para que el usuario ya no lo vea
            currentCache.removeAt(indexFind)
            state.cache = currentCache

            // 3. Actualizar el caché de disco (MySettings) para persistir el cambio visual
            mySettings.saveList("${SheetTable.PERMISOS.cacheKey}_CACHE", currentCache as List<List<String>>)
            mySettings.saveLong(SheetTable.PERMISOS.timestampKey, System.currentTimeMillis())

            /**
             * 4. Disparar el borrado en la Nube.
             * Usamos indexFind + 1 (si no hay encabezado) o + 2 (si hay encabezado).
             */
            sync(SheetTable.PERMISOS, Operation.DELETE, index = indexFind + 2)

            return true
        }

        return false // No se encontró el registro
    }

    //Direcciones de casas y ubicacion
    fun getDomiciliosUbicacion(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.DIRECCIONES,forceLoad) {
            val allDirections = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // Intentamos obtener el nombre de la hoja desde settings o usamos el default
            val nameWS = SheetTable.DIRECCIONES.sheetName

            //try {
                // Leemos el rango configurado (ej. A:C para Calle, Número, ID)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.DIRECCIONES.range}")
                    .execute()

                // Omitimos encabezados si es necesario con .drop(1)
                val rows = response.getValues().drop(1) ?: emptyList()
                allDirections.addAll(rows)

//            } catch (e: Exception) {
//                Log.e(TAG, "Error leyendo catálogo de direcciones: ${e.message}")
//            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allDirections
        }
    }
    fun getDomiciliosSimilares(calle: String, numero: String): List<List<Any>>{
        val _calle=calle.filter { it.isLetterOrDigit() }.uppercase()
        val _numero=numero.filter { it.isLetterOrDigit() }.uppercase()
        val rows = getDomiciliosUbicacion()
        val result = mutableListOf<List<Any>>()
        run loop@{
            rows.forEach { row ->
                val _rcalle = row[0].toString().uppercase()
                val _rnumer = row[1].toString().uppercase()
                if (_rcalle == _calle && _rnumer == _numero) {
                    //Concidencia exacta
                    result.clear()
                    result.add(row)
                    return@loop
                }
                if (sonCadenasSimilares("${_rcalle}:${_rnumer}", "${_calle}:${_numero}")) {
                    result.add(row)
                }
            }
        }
        return result as List<List<Any>>
    }

    //Por Revisar registros
    fun getPorRevisar(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum POR_REVISAR
        getSmartCache(SheetTable.POR_REVISAR,forceLoad) {
            val allPending = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // Intentamos obtener el nombre de la hoja (o usamos el default "PorRevisar")
            val nameWS = SheetTable.POR_REVISAR.sheetName

            //try {
                // Leemos el rango A:G (basado en tu Parte 2: calle, número, tiempo, slotkey, veridicado, lat, lon)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.POR_REVISAR.range}")
                    .execute()

                // Obtenemos los valores. Omitimos encabezados con .drop(1) si tu hoja los tiene
                val rows = response.getValues().drop(1) ?: emptyList()

                // Agregamos a la lista maestra
                allPending.addAll(rows)

//            } catch (e: Exception) {
//                Log.e(TAG, "Error leyendo lista de Por Revisar: ${e.message}")
//            }

            // Retornamos para que SmartCache lo guarde en RAM y Disco
            allPending
        }
    }
    fun getPorRevisar_20horas(forceLoad: Boolean = false): MutableList<List<Any>>?{
        val rows = getPorRevisar(forceLoad)
        val date20HoursAgo = LocalDateTime.now().minusHours(20)
        val porRevisar = rows?.filter { parseLenientDateTime(it[2].toString()) >= date20HoursAgo }
            ?.reversed()
        if (porRevisar!=null && porRevisar.isNotEmpty())
            return porRevisar as MutableList<List<Any>>?
        return mutableListOf<List<Any>>()
    }
    fun eliminarPorRevisar(calle: String, numero: String, slotKey: String): Boolean {
        val state = tableStates[SheetTable.POR_REVISAR] ?: return false

        if (state.cache == null) runBlocking { getPorRevisar() }
        val currentCache = state.cache?.toMutableList() ?: return false

        var indexFind = -1

        // 1. Buscar el índice en la lista actual (RAM)
        currentCache.forEachIndexed { index, row ->
            // Basado en tu estructura: Calle(0), Número(1), SlotKey(3)
            if (row.size >= 4 &&
                row[0].toString() == calle &&
                row[1].toString() == numero &&
                row[3].toString() == slotKey) {
                indexFind = index
                return@forEachIndexed
            }
        }

        if (indexFind >= 0) {
            // 2. Eliminar de la RAM inmediatamente para que el usuario ya no lo vea
            currentCache.removeAt(indexFind)
            state.cache = currentCache

            // 3. Actualizar el caché de disco (MySettings) para persistir el cambio visual
            mySettings.saveList("${SheetTable.POR_REVISAR.cacheKey}_CACHE", currentCache as List<List<String>>)
            mySettings.saveLong(SheetTable.POR_REVISAR.timestampKey, System.currentTimeMillis())

            /**
             * 4. Disparar el borrado en la Nube.
             * Usamos indexFind + 1 (si no hay encabezado) o + 2 (si hay encabezado).
             */
            sync(SheetTable.POR_REVISAR, Operation.DELETE, index = indexFind + 2)

            return true
        }

        return false // No se encontró el registro
    }
    fun _addPorRevisarCache(row: List<String>): Boolean {
        val table = SheetTable.POR_REVISAR
        val state = tableStates[table] ?: return false

        // 1. Aseguramos que la RAM tenga datos (si estaba nulo, lo cargamos)
        if (state.cache == null) {
            runBlocking { getPorRevisar() }
        }

        // 2. Actualizar RAM (Optimistic UI: el usuario ve el cambio al instante)
        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        currentCache.add(row)
        state.cache = currentCache

        // 3. Persistir el cambio visual en el caché de disco (MySettings)
        mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
        mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

        /**
         * 4. Sincronización con Google Sheets.
         * Llamamos a sync() con Operation.APPEND.
         * Internamente, esto gestiona la cola de reintentos y la red.
         */
        sync(table, Operation.APPEND, data = row)

        return true
    }

    //Lugares de VISITAS
    fun getParkingSlots(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.PARKING_SLOTS,forceLoad) {
            val allSlots = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // Buscamos el nombre de la hoja en settings o usamos el default "ParkingSlots"
            val nameWS = SheetTable.PARKING_SLOTS.sheetName

            //try {
                // Consultamos el rango A:E de la hoja de Google Sheets
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.PARKING_SLOTS.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allSlots.addAll(rows)

//            } catch (e: Exception) {
//                Log.e(TAG, "Error leyendo Parking Slots desde la red: ${e.message}")
//            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco local
            allSlots
        }
    }
    fun _addPartkingSlotCache(row: List<String>): Boolean{
        val table = SheetTable.PARKING_SLOTS
        val state = tableStates[table] ?: return false
        // 1. Load
        if (state.cache == null) { runBlocking { getParkingSlots() } }

        // 2. Actualizar RAM (Optimistic UI: el usuario ve el cambio al instante)
        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        currentCache.add(row)
        state.cache = currentCache

        // 3. Persistir el cambio visual en el caché de disco (MySettings)
        mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
        mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

        //4. Sincronización con Google Sheets.
        sync(table, Operation.APPEND, data = row)

        return true
    }

    //AutoEventos
    fun getAutosEventos(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.AUTOS_EVENTOS,forceLoad) {
            val allEvents = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // Buscamos el nombre de la hoja en settings o usamos el default
            val nameWS = SheetTable.AUTOS_EVENTOS.sheetName

            //try {
                // Consultamos el rango A:E (o el que tengas configurado)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.AUTOS_EVENTOS.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allEvents.addAll(rows)

//            } catch (e: Exception) {
//                Log.e(TAG, "Error leyendo Autos Eventos desde la red: ${e.message}")
//            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allEvents
        }
    }
    fun getAutosEventos_6horas(forceLoad: Boolean = false ): List<List<Any>>{
        val rows = getAutosEventos(forceLoad)
        val date6HoursAgo = LocalDateTime.now().minusHours(6)
        val plateEvents = rows.filter {
            parseLenientDateTime(it[2].toString()) >= date6HoursAgo
        }
            .reversed()
        return plateEvents ?: emptyList()
    }
    fun _addAutosEventCache(row: List<String>): Boolean{

        val table = SheetTable.AUTOS_EVENTOS
        val state = tableStates[table] ?: return false
        // 1. Load
        if (state.cache == null) { runBlocking { getAutosEventos()} }

        // 2. Actualizar RAM (Optimistic UI: el usuario ve el cambio al instante)
        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        currentCache.add(row)
        state.cache = currentCache

        // 3. Persistir el cambio visual en el caché de disco (MySettings)
        mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
        mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

        //4. Sincronización con Google Sheets.
        sync(table, Operation.APPEND, data = row)

        return true
    }
    fun eliminarAutosEventoCache(row: List<String>): Boolean{
        val state = tableStates[SheetTable.AUTOS_EVENTOS] ?: return false

        var indexFind = -1
        // 1. Aseguramos que la RAM tenga datos (Carga desde RAM -> Disco -> Red)
        if (state.cache == null) runBlocking { getAutosEventos() }
        val currentCache = state.cache?.toMutableList() ?: return false

        // 2. Búsqueda por fecha de creacion
        val pCreadp = parseLenientDateTime(row[2].toString())
        currentCache.forEachIndexed { index, evento ->
            val _fCreado = parseLenientDateTime(evento[2].toString())
            if (evento.size >= 4 &&
                pCreadp == _fCreado ) {
                indexFind = index
                return@forEachIndexed
            }
        }

        if (indexFind >= 0) {
            // 2. Eliminar de la RAM inmediatamente para que el usuario ya no lo vea
            currentCache.removeAt(indexFind)
            state.cache = currentCache

            // 3. Actualizar el caché de disco (MySettings) para persistir el cambio visual
            mySettings.saveList("${SheetTable.AUTOS_EVENTOS.cacheKey}_CACHE", currentCache as List<List<String>>)
            mySettings.saveLong(SheetTable.AUTOS_EVENTOS.timestampKey, System.currentTimeMillis())

            /**
             * 4. Disparar el borrado en la Nube.
             * Usamos indexFind + 1 (si no hay encabezado) o + 2 (si hay encabezado).
             */
            sync(SheetTable.AUTOS_EVENTOS, Operation.DELETE, index = indexFind + 2)

            return true
        }

        return false // No se encontró el registro
    }

    //IncidenciasEventos
    fun getIncidenciasConfig(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.INCIDENCIAS_CONFIG,forceLoad) {
            val allRows = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // Buscamos el nombre de la hoja en settings o usamos el default
            val nameWS = SheetTable.INCIDENCIAS_CONFIG.sheetName

            //try {
                // Consultamos el rango A:E (o el que tengas configurado)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.INCIDENCIAS_CONFIG.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allRows.addAll(rows)

//            } catch (e: Exception) {
//                Log.e(TAG, "Error leyendo ${nameWS} config desde la red: ${e.message}")
//            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allRows
        }
    }
    fun getIncidenciasEventos(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.INCIDENCIAS,forceLoad) {
            val allRows = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // Buscamos el nombre de la hoja en settings o usamos el default
            val nameWS = SheetTable.INCIDENCIAS.sheetName

            //try {
                // Consultamos el rango A:E (o el que tengas configurado)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.INCIDENCIAS.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allRows.addAll(rows)

//            } catch (e: Exception) {
//                Log.e(TAG, "Error leyendo ${nameWS} config desde la red: ${e.message}")
//            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allRows
        }
    }
    fun getIncidenciasEventosDesde(fechaDay: LocalDate = LocalDate.now()): List<List<Any>>{
        val rows = getIncidenciasEventos()
        return rows.filter{ parseLenientDate(it[2].toString()) >= fechaDay}
    }
    fun getIncidenciasEventosTipo(Tipo: String, fechaDay: LocalDate = LocalDate.now()): List<List<Any>>{
        val rows = getIncidenciasEventos()
        //val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val IncidenciaEvents = rows.filter {
            parseLenientDate(it[2].toString()) == fechaDay
                    && it[4].toString().uppercase() == Tipo.uppercase() }
        return IncidenciaEvents ?: emptyList()

    }
    fun addIncidenciaEvento(row: List<String>): Boolean{

        val table = SheetTable.INCIDENCIAS
        val state = tableStates[table] ?: return false
        // 1. Load
        if (state.cache == null) { runBlocking { getIncidenciasEventos()} }

        // 2. Actualizar RAM (Optimistic UI: el usuario ve el cambio al instante)
        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        currentCache.add(row)
        state.cache = currentCache

        // 3. Persistir el cambio visual en el caché de disco (MySettings)
        mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
        mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

        //4. Sincronización con Google Sheets.
        sync(table, Operation.APPEND, data = row)

        return true
    }

    //DomicilioWarnings
    fun getDomicilioWarnings(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.DOMICILIO_WARNINGS,forceLoad) {
            val allRows = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // Buscamos el nombre de la hoja en settings o usamos el default
            val nameWS = SheetTable.DOMICILIO_WARNINGS.sheetName

            //try {
                // Consultamos el rango A:E (o el que tengas configurado)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.DOMICILIO_WARNINGS.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allRows.addAll(rows)

//            } catch (e: Exception) {
//                Log.e(TAG, "Error leyendo ${nameWS} config desde la red: ${e.message}")
//            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allRows
        }
    }
    fun addDomicilioWarning(row: List<String>): Boolean{

        val table = SheetTable.DOMICILIO_WARNINGS
        val state = tableStates[table] ?: return false
        // 1. Load
        if (state.cache == null) { runBlocking { getDomicilioWarnings()} }

        // 2. Actualizar RAM (Optimistic UI: el usuario ve el cambio al instante)
        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        currentCache.add(row)
        state.cache = currentCache

        // 3. Persistir el cambio visual en el caché de disco (MySettings)
        mySettings.saveList(table.cacheKey, currentCache as List<List<String>>)
        mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

        //4. Sincronización con Google Sheets.
        sync(table, Operation.APPEND, data = row)

        return true
    }
    fun updateDomicilioWarning(row: List<String>): Int {
        val table = SheetTable.DOMICILIO_WARNINGS
        val state = tableStates[table] ?: return 0

        // 1. Aseguramos que la RAM tenga datos (Carga desde RAM -> Disco -> Red)
        if (state.cache == null) runBlocking { getDomicilioWarnings() }

        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        var indexFind = -1
        var currentCount = 0

        // 2. Búsqueda por Calle(0), Número(1) y Tipo(3)
        currentCache.forEachIndexed { index, domWarn ->
            if (domWarn.size >= 4 &&
                domWarn[0].toString() == row[0].toString() &&
                domWarn[1].toString() == row[1].toString() &&
                domWarn[3].toString() == row[3].toString()) {
                indexFind = index
                currentCount = domWarn[2].toString().toIntOrNull() ?: 0
                return@forEachIndexed
            }
        }

        val newCount = currentCount + 1
        val newData = listOf(
            row[0].toString(),            // Calle
            row[1].toString(),            // Numero
            newCount.toString(),          // Nuevo Contador
            row[3].toString()             // Tipo (ej. Ruido, Obstrucción)
        )

        if (indexFind >= 0) {
            // CASO A: ACTUALIZAR EXISTENTE
            currentCache[indexFind] = newData
            state.cache = currentCache

            // Persistir en disco para acceso offline inmediato
            mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
            mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

            // Sincronizar Update (index + 2 por el encabezado de Google Sheets)
            sync(table, Operation.UPDATE, data = newData, index = indexFind + 2)
        } else {
            // CASO B: ES NUEVO (APPEND)
            currentCache.add(newData)
            state.cache = currentCache

            mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
            sync(table, Operation.APPEND, data = newData)
        }

        return newCount
    }

    //Multas
    fun getMultas(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.MULTAS,forceLoad) {
            val allRows = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // Buscamos el nombre de la hoja en settings o usamos el default
            val nameWS = SheetTable.MULTAS.sheetName

            //try {
                // Consultamos el rango A:E (o el que tengas configurado)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.MULTAS.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allRows.addAll(rows)

//            } catch (e: Exception) {
//                Log.e(TAG, "Error leyendo ${nameWS} config desde la red: ${e.message}")
//            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allRows
        }
    }
    fun addMulta(row: List<String>): Boolean{

        val table = SheetTable.MULTAS
        val state = tableStates[table] ?: return false
        // 1. Load
        if (state.cache == null) { runBlocking { getIncidenciasEventos()} }

        // 2. Actualizar RAM (Optimistic UI: el usuario ve el cambio al instante)
        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        currentCache.add(row)
        state.cache = currentCache

        // 3. Persistir el cambio visual en el caché de disco (MySettings)
        mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
        mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

        //4. Sincronización con Google Sheets.
        sync(table, Operation.APPEND, data = row)

        return true
    }
    fun updateMulta(row: List<String>): Boolean {
        val table = SheetTable.MULTAS
        val state = tableStates[table] ?: return false

        // 1. Aseguramos que la RAM tenga datos cargados
        if (state.cache == null) runBlocking { getMultas()}

        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        var indexFind = -1
        var currentCount = 0

        // 2. Lógica de búsqueda por Calle (índice 0) y Número (índice 1)
        currentCache.forEachIndexed { index, multa ->
            if (multa.size >= 2 &&
                multa[0].toString() == row[0].toString() &&
                multa[1].toString() == row[1].toString()) {
                indexFind = index
                // Obtenemos el contador actual (asumiendo que está en la columna 2)
                currentCount = multa.getOrNull(2)?.toString()?.toIntOrNull() ?: 0
                return@forEachIndexed
            }
        }

        // 3. Preparamos los nuevos datos (Calle, Número, Contador + 1)
        val newData = listOf(
            row[0].toString(),
            row[1].toString(),
            (currentCount + 1).toString()
        )

        if (indexFind >= 0) {
            // CASO A: ACTUALIZAR EXISTENTE
            currentCache[indexFind] = newData
            state.cache = currentCache

            // Persistimos cambio visual en disco
            mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
            mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

            // Sincronizamos con Google Sheets (indexFind + 2 por el encabezado)
            sync(table, Operation.UPDATE, data = newData, index = indexFind + 2)
            return true
        } else {
            // CASO B: AGREGAR NUEVA (Si no se encontró)
            return addMulta(row)
        }
    }

    //Alarmas
    fun getAlarmas(forceLoad: Boolean = false): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.ALARMAS_RONDIN,forceLoad) {
            val allSlots = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")
            if (spreadsheetId.isEmpty()) throw IllegalArgumentException("No hay Sheet configurado")

            // Buscamos el nombre de la hoja en settings o usamos el default "ParkingSlots"
            val nameWS = SheetTable.ALARMAS_RONDIN.sheetName

            // Consultamos el rango A:E de la hoja de Google Sheets
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "$nameWS!${SheetTable.ALARMAS_RONDIN.range}")
                .execute()

            val rows = response.getValues().drop(1) ?: emptyList()
            allSlots.addAll(rows)

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco local
            allSlots
        }
    }
    fun updateAlarma(oldData: List<String>, newData: List<String>): Boolean{
        val table = SheetTable.ALARMAS_RONDIN
        val state = tableStates[table] ?: return false

        // 1. Aseguramos que la RAM tenga datos (Carga desde RAM -> Disco -> Red)
        if (state.cache == null) runBlocking { getAlarmas() }

        val currentCache = state.cache?.toMutableList() ?: mutableListOf()
        var indexFind = -1

        // 2. Búsqueda por Calle(0), Número(1) y Tipo(3)
        currentCache.forEachIndexed { index, row ->
            if (row.size == 2 &&
                row[0].toString() == oldData[0].toString() &&
                row[1].toString() == oldData[1].toString()) {
                indexFind = index
                return@forEachIndexed
            }
        }

        if (indexFind >= 0) {
            // CASO A: ACTUALIZAR EXISTENTE
            currentCache[indexFind] = newData
            state.cache = currentCache

            // Persistir en disco para acceso offline inmediato
            mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
            mySettings.saveLong(table.timestampKey, System.currentTimeMillis())

            // Sincronizar Update (index + 2 por el encabezado de Google Sheets)
            sync(table, Operation.UPDATE, data = newData, index = indexFind + 2)
        } else {
            // CASO B: ES NUEVO (APPEND)
            currentCache.add(newData)
            state.cache = currentCache

            mySettings.saveList("${table.cacheKey}_CACHE", currentCache as List<List<String>>)
            sync(table, Operation.APPEND, data = newData)
        }

        return true
    }
    fun eliminarAlarma(hora: String, nombre: String): Boolean{
        val state = tableStates[SheetTable.ALARMAS_RONDIN] ?: return false

        if (state.cache == null) runBlocking { getPorRevisar() }
        val currentCache = state.cache?.toMutableList() ?: return false

        var indexFind = -1

        // 1. Buscar el índice en la lista actual (RAM)
        currentCache.forEachIndexed { index, row ->
            // Basado en tu estructura: Hora(0), Nomrbe(1)
            if (row.size == 2 &&
                row[0].toString() == hora &&
                row[1].toString() == nombre ) {
                indexFind = index
                return@forEachIndexed
            }
        }

        if (indexFind >= 0) {
            // 2. Eliminar de la RAM inmediatamente para que el usuario ya no lo vea
            currentCache.removeAt(indexFind)
            state.cache = currentCache

            // 3. Actualizar el caché de disco (MySettings) para persistir el cambio visual
            mySettings.saveList("${SheetTable.ALARMAS_RONDIN.cacheKey}_CACHE", currentCache as List<List<String>>)
            mySettings.saveLong(SheetTable.ALARMAS_RONDIN.timestampKey, System.currentTimeMillis())

            /**
             * 4. Disparar el borrado en la Nube.
             * Usamos indexFind + 1 (si no hay encabezado) o + 2 (si hay encabezado).
             */
            sync(SheetTable.ALARMAS_RONDIN, Operation.DELETE, index = indexFind + 2)

            return true
        }

        return false // No se encontró el registro
    }
}