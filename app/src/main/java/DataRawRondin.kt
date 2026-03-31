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
import com.larangel.rondingpeinn.R
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.minutes
import android.util.Log
import java.security.Permission
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
    DIRECCIONES("Direcciones", "directions", "A:D");  // calle, numero, latitud, longitud


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
                DIRECCIONES to "WS_DOMICILIOS_UBICACION"
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
    private val TAG = "package:com.larangel.rondingpeinn"
    private val CACHE_DURATION_MS = 60000//60 * 60 * 1000 // 1 hora

    private val flexibleDateFormatter = DateTimeFormatterBuilder()
        .appendOptional(DateTimeFormatter.ofPattern("d/M/yyyy"))
        .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        .appendOptional(DateTimeFormatter.ofPattern("d/M/yy"))
        .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yy"))
        .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .toFormatter()

    private val flexibleDateTimeFormatter = DateTimeFormatterBuilder()
        .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        .appendOptional(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
        .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
        .appendOptional(DateTimeFormatter.ofPattern("d/MM/yyyy H:mm:ss"))
        .appendOptional(DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss"))
        .appendOptional(DateTimeFormatter.ofPattern("d/M/yy HH:mm:ss"))
        .appendOptional(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        .toFormatter()


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

    // --- SECCIÓN 1: LECTURA Y CACHÉ INTELIGENTE ---
    suspend fun getSmartCache(table: SheetTable, fetcher: suspend () -> List<List<Any>>): List<List<Any>> {
        val state = tableStates[table]!!
        val now = System.currentTimeMillis()
        val isConnected = isNetworkAvailable()

        // 1. RAM
        if (state.cache != null && (now - state.timestamp <= CACHE_DURATION_MS || !isConnected)) return state.cache!!

        // 2. Disco
        val diskCache = mySettings.getList("${table.cacheKey}_CACHE") ?: emptyList()
        val diskTs = mySettings.getLong(table.timestampKey, 0L)
        if (diskCache.isNotEmpty() && (now - diskTs <= CACHE_DURATION_MS || !isConnected)) {
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
        var rangeStr = "${table.sheetName}!${table.range}"

        if (table == SheetTable.PERMISOS) {
            rangeStr = table.range
            spreadsheetId = mySettings.getSimpleList("PERMISOS_SPREADSHEET_ID")[0]
        }

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
            spreadsheet.sheets.find { it.properties.title == sheetName }?.properties?.sheetId
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo obtener el ID de la hoja $sheetName")
            null
        }
    }


    // VEHICULOS
    fun getCachedVehiclesData(): List<List<Any>> = runBlocking {
        // Llamamos a la función genérica usando el Enum de VEHICULOS
        getSmartCache(SheetTable.VEHICULOS) {
            val allRows = mutableListOf<List<Any>>()

            // 1. Obtener Vehículos RESIDENTES
            val residentsId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
            val residentsSheet = SheetTable.VEHICULOS.sheetName //mySettings.getString("WS_AUTOS_REGISTRADOS", "AutosRegistrados")!!

            try {
                val response = sheetsService.spreadsheets().values()
                    .get(residentsId, "$residentsSheet!A:C") // placa, calle, numero
                    .execute()
                response.getValues().drop(1)?.let { allRows.addAll(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo Residentes: ${e.message}")
            }

            // 2. Obtener Vehículos VISITANTES (Hojas: ingreso y salida)
            val visitorsId = mySettings.getString("REGISTRO_CARROS_SPREADSHEET_ID", "")
            if (!visitorsId.isNullOrEmpty()) {
                val visitorSheets = listOf("ingreso", "salida")
                for (sheetName in visitorSheets) {
                    try {
                        val response = sheetsService.spreadsheets().values()
                            .get(visitorsId, "$sheetName!C:E") // placa, calle, numero
                            .execute()
                        // drop(1) para omitir los encabezados de la tabla de visitantes
                        val rows = response.getValues()?.drop(1) ?: emptyList()
                        allRows.addAll(rows)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error leyendo Visitantes ($sheetName): ${e.message}")
                    }
                }
            }

            // Retornamos la lista unificada al SmartCache para que la guarde en RAM y Disco
            allRows
        }
    }
    fun getTagsCache(): List<List<Any>> = runBlocking {
        getSmartCache(SheetTable.TAGS) {
            val allParsedTags = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!
            val nameWS = SheetTable.TAGS.sheetName //mySettings.getString("WS_AUTOS_REGISTRADOS", "AutosRegistrados")!!

            try {
                // Obtenemos el rango A:H (Placas, Calle, Numero, Marca, Modelo, Color, Tag1, Tag2, Tag3)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!A:I")
                    .execute()

                // Omitimos el encabezado
                val rows = response.getValues()?.drop(1) ?: emptyList()

                rows.forEach { row ->
                    // Índices: Calle(1), Numero(2), Tag1(6), Tag2(7), Tag3(8)
                    val calle = row.getOrNull(1)?.toString() ?: ""
                    val numero = row.getOrNull(2)?.toString() ?: ""

                    // Procesamos cada columna de Tag si existe y no está vacía
                    for (i in 6..8) {
                        val tagValue = row.getOrNull(i)?.toString()
                        if (!tagValue.isNullOrBlank()) {
                            allParsedTags.add(listOf(tagValue, calle, numero))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando Tags desde Google Sheets: ${e.message}")
            }

            // Retornamos la lista de [Tag, Calle, Numero]
            allParsedTags
        }
    }

    // Permisos
    fun getPermisosCache(): List<List<Any>> = runBlocking {
        // Usamos el Enum de PERMISOS y el SmartCache genérico
        getSmartCache(SheetTable.PERMISOS) {
            val allPermisos = mutableListOf<List<Any>>()

            // Obtenemos la lista de IDs de Spreadsheets desde MySettings
            val spreadsheetIds = mySettings.getSimpleList("PERMISOS_SPREADSHEET_ID") ?: emptyList<String>()

            for (id in spreadsheetIds) {
                try {
                    // Consultamos el rango A:N (desde Marca temporal hasta Procesado por ROBOT)
                    val response = sheetsService.spreadsheets().values()
                        .get(id, "A:N")
                        .execute()

                    // Omitimos la primera fila (encabezados) de cada hoja
                    val rows = response.getValues()?.drop(1) ?: emptyList()
                    allPermisos.addAll(rows)

                } catch (e: Exception) {
                    Log.e(TAG, "Error leyendo permisos del ID: $id - ${e.message}")
                }
            }

            // Retornamos la lista consolidada al SmartCache
            allPermisos
        }
    }
    fun getPermisosCache_DeHoy(): List<List<Any>> {
        val rows = getPermisosCache()
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
                    true
                }else if (fechaInicioStr.isNotBlank() && fechaFinStr.isNotBlank() && stringTrue.contains(procesadoRobot)) {
                    val inicio = LocalDate.parse(fechaInicioStr, flexibleDateFormatter)
                    val fin = LocalDate.parse(fechaFinStr, flexibleDateFormatter)
                    // Verificamos si hoy está dentro del rango (inclusive)
                    hoy >= inicio && hoy <= fin
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al parsear fechas en fila: $row")
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
        val pCreadp = LocalDateTime.parse(row[0].toString(), flexibleDateTimeFormatter)
        currentCache.forEachIndexed { index, permiso ->
            val _fCreado = LocalDateTime.parse(permiso[0].toString(), flexibleDateTimeFormatter)
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

    //Direcciones de casas y ubicacion
    fun getDomiciliosUbicacion(): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.DIRECCIONES) {
            val allDirections = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!

            // Intentamos obtener el nombre de la hoja desde settings o usamos el default
            val nameWS = SheetTable.DIRECCIONES.sheetName

            try {
                // Leemos el rango configurado (ej. A:C para Calle, Número, ID)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.DIRECCIONES.range}")
                    .execute()

                // Omitimos encabezados si es necesario con .drop(1)
                val rows = response.getValues().drop(1) ?: emptyList()
                allDirections.addAll(rows)

            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo catálogo de direcciones: ${e.message}")
            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allDirections
        }
    }

    //Por Revisar registros
    fun getPorRevisar(): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum POR_REVISAR
        getSmartCache(SheetTable.POR_REVISAR) {
            val allPending = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!

            // Intentamos obtener el nombre de la hoja (o usamos el default "PorRevisar")
            val nameWS = SheetTable.POR_REVISAR.sheetName

            try {
                // Leemos el rango A:G (basado en tu Parte 2: calle, número, tiempo, slotkey, veridicado, lat, lon)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.POR_REVISAR.range}")
                    .execute()

                // Obtenemos los valores. Omitimos encabezados con .drop(1) si tu hoja los tiene
                val rows = response.getValues().drop(1) ?: emptyList()

                // Agregamos a la lista maestra
                allPending.addAll(rows)

            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo lista de Por Revisar: ${e.message}")
            }

            // Retornamos para que SmartCache lo guarde en RAM y Disco
            allPending
        }
    }
    fun getPorRevisar_20horas(): MutableList<List<Any>>?{
        val rows = getPorRevisar()
        val date20HoursAgo = LocalDateTime.now().minusHours(20)
        val porRevisar = rows?.filter { LocalDateTime.parse(it[2].toString(),flexibleDateTimeFormatter) >= date20HoursAgo }
            ?.reversed()
        if (porRevisar!=null && porRevisar.isNotEmpty())
            return porRevisar as MutableList<List<Any>>?
        return mutableListOf<List<Any>>()
    }
    fun eliminarPorRevisar(calle: String, numero: String, slotKey: String): Boolean {
        val state = tableStates[SheetTable.POR_REVISAR] ?: return false
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
    fun getParkingSlots(): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.PARKING_SLOTS) {
            val allSlots = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")

            // Buscamos el nombre de la hoja en settings o usamos el default "ParkingSlots"
            val nameWS = SheetTable.PARKING_SLOTS.sheetName

            try {
                // Consultamos el rango A:E de la hoja de Google Sheets
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.PARKING_SLOTS.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allSlots.addAll(rows)

            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo Parking Slots desde la red: ${e.message}")
            }

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
    fun getAutosEventos(): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.AUTOS_EVENTOS) {
            val allEvents = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")!!

            // Buscamos el nombre de la hoja en settings o usamos el default
            val nameWS = SheetTable.AUTOS_EVENTOS.sheetName

            try {
                // Consultamos el rango A:E (o el que tengas configurado)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.AUTOS_EVENTOS.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allEvents.addAll(rows)

            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo Autos Eventos desde la red: ${e.message}")
            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allEvents
        }
    }
    fun getAutosEventos_6horas( ): List<List<Any>>{
        val rows = getAutosEventos()
        val date6HoursAgo = LocalDateTime.now().minusHours(6)
        val plateEvents = rows.filter {
            LocalDateTime.parse(it[2].toString(),flexibleDateTimeFormatter) >= date6HoursAgo
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

    //IncidenciasEventos
    fun getIncidenciasConfig(): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.INCIDENCIAS_CONFIG) {
            val allRows = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")

            // Buscamos el nombre de la hoja en settings o usamos el default
            val nameWS = SheetTable.INCIDENCIAS_CONFIG.sheetName

            try {
                // Consultamos el rango A:E (o el que tengas configurado)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.INCIDENCIAS_CONFIG.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allRows.addAll(rows)

            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo ${nameWS} config desde la red: ${e.message}")
            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allRows
        }
    }
    fun getIncidenciasEventos(): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.INCIDENCIAS) {
            val allRows = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")

            // Buscamos el nombre de la hoja en settings o usamos el default
            val nameWS = SheetTable.INCIDENCIAS.sheetName

            try {
                // Consultamos el rango A:E (o el que tengas configurado)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.INCIDENCIAS.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allRows.addAll(rows)

            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo ${nameWS} config desde la red: ${e.message}")
            }

            // Retornamos la lista para que SmartCache la guarde en RAM y Disco
            allRows
        }
    }
    fun getIncidenciasEventosTipo(Tipo: String, fechaDay: LocalDate = LocalDate.now()): List<List<Any>>{
        val rows = getIncidenciasEventos()
        //val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val IncidenciaEvents = rows.filter { LocalDate.parse(it[2].toString(),flexibleDateTimeFormatter) == fechaDay && it[4].toString().uppercase() == Tipo.uppercase() }
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
    fun getDomicilioWarnings(): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.DOMICILIO_WARNINGS) {
            val allRows = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")

            // Buscamos el nombre de la hoja en settings o usamos el default
            val nameWS = SheetTable.DOMICILIO_WARNINGS.sheetName

            try {
                // Consultamos el rango A:E (o el que tengas configurado)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.DOMICILIO_WARNINGS.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allRows.addAll(rows)

            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo ${nameWS} config desde la red: ${e.message}")
            }

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
    fun getMultas(): List<List<Any>> = runBlocking {
        // Usamos el SmartCache genérico con el Enum correspondiente
        getSmartCache(SheetTable.MULTAS) {
            val allRows = mutableListOf<List<Any>>()
            val spreadsheetId = mySettings.getString("PARKING_SPREADSHEET_ID", "")

            // Buscamos el nombre de la hoja en settings o usamos el default
            val nameWS = SheetTable.MULTAS.sheetName

            try {
                // Consultamos el rango A:E (o el que tengas configurado)
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "$nameWS!${SheetTable.MULTAS.range}")
                    .execute()

                val rows = response.getValues().drop(1) ?: emptyList()
                allRows.addAll(rows)

            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo ${nameWS} config desde la red: ${e.message}")
            }

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


//    private var mySettings: MySettings? = null
//    private var applicationContext: Context? = null
//    private var coroutineScope: CoroutineScope? = null
//    private lateinit var resultText: TextView
//
//    private lateinit var sheetsService: Sheets
//    private val TAG ="package:com.larangel.rondingpeinn"

//    // Variables de cache y tiempo
//    companion object {
//        private var platesCache: List<List<Any>>? = null
//        private var tagsCache: List<List<Any>>? = null
//        private var parkingSlotsCache: MutableList<List<Any>>? = null
//        private var autosEventosCache: MutableList<List<Any>>? = null
//        private var incidenciaEventosCache: MutableList<List<Any>>? = null
//        private var directionsCache: List<List<Any>>? = null
//        private var porRevisarCache: MutableList<List<Any>>? = null
//        private var multasCache: MutableList<List<Any>>? = null
//        private var incidenciaConfigCache: List<List<Any>>? = null
//        private var domicilioWarningsCache: MutableList<List<Any>>? = null
//        private var permisosCache: List<List<Any>>? = null
//
//        private var platesCacheTimestamp: Long = 0
//        private var tagsCacheTimestamp: Long = 0
//        private var parkingSlotsCacheTimestamp: Long = 0
//        private var autosEventosCacheTimestamp: Long = 0
//        private var incidenciaEventosCacheTimestamp: Long = 0
//        private var incidenciaConfigCacheTimestamp: Long = 0
//        private var directionsCacheTimestamp: Long = 0
//        private var porRevisarCacheTimestamp: Long = 0
//        private var multasCacheTimestamp: Long = 0
//        private var domicilioWarningsCacheTimestamp: Long = 0
//        private var permisosCacheTimestamp: Long = 0
//    }
//
//
//    private val CACHE_DURATION_MS = 60 * 60 * 1000 // 1 hora
//
//    //Sheets name index
//    private var porRevisarSheetId: Int = 0
//
//    //Variables temporales para salvar
//    private var forSave_parkingSlots: MutableList<List<Any>>? = mutableListOf<List<Any>>()
//    private var isRunningSave_parkingSlots: Boolean = false
//    private var forSave_autosEventos: MutableList<List<Any>>? = mutableListOf<List<Any>>()
//    private var isRunningSave_autosEventos: Boolean = false
//    private var forSave_incidenciaEventos: MutableList<List<Any>>? = mutableListOf<List<Any>>()
//    private var isRunningSave_incidenciaEventos: Boolean = false
//    private var forSave_porRevisar: MutableList<List<Any>>? = mutableListOf<List<Any>>()
//    private var forDelete_porRevisarIndex: ArrayList<String>? = ArrayList<String>()
//    private var isRunningSave_porRevisar: Boolean = false
//    private var isRunningDelete_porRevisar: Boolean = false
//
//    //Multas
//    private var forSave_MultaGenerada: MutableList<List<Any>>? = mutableListOf<List<Any>>()
//    private var forUpdate_MultaGenerada: MutableList<List<Any>>? = mutableListOf<List<Any>>()
//    private var forUpdate_MultaGeneradaIndex: ArrayList<String>? = ArrayList<String>()
//    private var isRunningSave_MultaGenerada: Boolean = false
//    private var isRunningUpdate_MultaGenerada: Boolean = false
//
//    //Domicilio Warnings
//    private var forSave_DomicilioWarnings: MutableList<List<Any>>? = mutableListOf<List<Any>>()
//    private var forUpdate_DomicilioWarnings: MutableList<List<Any>>? = mutableListOf<List<Any>>()
//    private var forUpdate_DomicilioWarningsIndex: ArrayList<String>? = ArrayList<String>()
//    private var isRunningSave_DomicilioWarnings: Boolean = false
//    private var isRunningUpdate_DomicilioWarnings: Boolean = false
//
//    init {
//        applicationContext = context
//        mySettings = MySettings(context)
//        coroutineScope = coroutineScopeObject
//
//        initializeGoogleServices()
//        //Pendientes por guardar?
//        //checarPendientePorSalvarEnCACHE()
//    }
//
//    private fun initializeGoogleServices() {
//        try {
//            val serviceAccountStream =  applicationContext?.resources?.openRawResource(R.raw.json_google_service_account)
//            val credential = GoogleCredential.fromStream(serviceAccountStream)
//                .createScoped(listOf("https://www.googleapis.com/auth/drive", "https://www.googleapis.com/auth/spreadsheets"))
//            sheetsService = Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
//                .setApplicationName("My First Project")
//                .build()
//            println("LARANGEL sheetsService:${sheetsService}")
//
//        } catch (e: Exception) {
//            val customMessage = "Error al inicializar sheet ID de PARKING: ${e.message}"
//            throw Exception(customMessage, e) // e is the original cause
//        }
//
//    }
//
//    //Pendientes por guardar en cache? ejecuta el ciclo
//    fun checarPendientePorSalvarEnCACHE(){
//        //forSave_parkingSlots
//        val cacheList0 = mySettings?.getList("CACHE_forSave_parkingSlots")!!.toMutableList()
//        if (forSave_parkingSlots?.isEmpty() == true && cacheList0.isNotEmpty()){
//            forSave_parkingSlots= cacheList0 as MutableList<List<Any>>?
//            saveParkingSlotsToSheetWithRetry(listOf())
//        }
//
//        //forSave_autosEventos
//        val cacheList1 = mySettings?.getList("CACHE_forSave_autosEventos")!!.toMutableList()
//        if (forSave_autosEventos?.isEmpty() == true && cacheList1.isNotEmpty()){
//            forSave_autosEventos= cacheList1 as MutableList<List<Any>>?
//            saveAutosEventosToSheetWithRetry(listOf())
//        }
//
//        //forSave_porRevisar
//        val cacheList2 = mySettings?.getList("CACHE_forSave_porRevisar")!!.toMutableList()
//        if (forSave_porRevisar?.isEmpty() == true && cacheList2.isNotEmpty()){
//            forSave_porRevisar= cacheList2 as MutableList<List<Any>>?
//            savePorRevisarToSheetWithRetry(listOf())
//        }
//
//        //forDelete_porRevisarIndex
//        val cacheList3 = mySettings?.getSimpleList("CACHE_forDelete_porRevisarIndex")!!.toMutableList()
//        if (forDelete_porRevisarIndex?.isEmpty() == true && cacheList3.isNotEmpty()){
//            forDelete_porRevisarIndex= cacheList3 as ArrayList<String>
//            deletePorRevisarSheetWithRetry(-1)
//        }
//
//        //forSave_MultaGenerada
//        val cacheList4 = mySettings?.getList("CACHE_forSave_MultaGenerada")!!.toMutableList()
//        if (forSave_MultaGenerada?.isEmpty() == true && cacheList4.isNotEmpty()){
//            forSave_MultaGenerada= cacheList4 as MutableList<List<Any>>?
//            saveMultaGeneradaToSheetWithRetry(listOf())
//        }
//
//        //forUpdate_MultaGenerada    y    forUpdate_MultaGeneradaIndex
//        val cacheList5 = mySettings?.getList("CACHE_forUpdate_MultaGenerada")!!.toMutableList()
//        val cacheList5_1 = mySettings?.getSimpleList("CACHE_forUpdate_MultaGeneradaIndex")!!.toMutableList()
//        if (forUpdate_MultaGenerada?.isEmpty() == true && cacheList5.isNotEmpty() && cacheList5_1.isNotEmpty()){
//            forUpdate_MultaGenerada= cacheList5 as MutableList<List<Any>>?
//            forUpdate_MultaGeneradaIndex = cacheList5_1 as ArrayList<String>
//            updateMultaGeneradaToSheetWithRetry(listOf(),-1)
//        }
//
//        //forSave_DomicilioWarnings
//        val cacheList6 = mySettings?.getList("CACHE_forSave_DomicilioWarnings")!!.toMutableList()
//        if (forSave_DomicilioWarnings?.isEmpty() == true && cacheList6.isNotEmpty()){
//            forSave_DomicilioWarnings= cacheList6 as MutableList<List<Any>>?
//            saveDomicilioWarningToSheetWithReatry(listOf())
//        }
//
//        //forUpdate_DomicilioWarnings   y  forUpdate_DomicilioWarningsIndex
//        val cacheList7 = mySettings?.getList("CACHE_forUpdate_DomicilioWarnings")!!.toMutableList()
//        val cacheList7_1 = mySettings?.getSimpleList("CACHE_forUpdate_DomicilioWarningsIndex")!!.toMutableList()
//        if (forUpdate_DomicilioWarnings?.isEmpty() == true && cacheList7.isNotEmpty() && cacheList7_1.isNotEmpty()){
//            forUpdate_DomicilioWarnings= cacheList7 as MutableList<List<Any>>?
//            forUpdate_DomicilioWarningsIndex = cacheList7_1 as ArrayList<String>
//            updateDomicilioWarningToSheetWithReatry(listOf(),-1)
//        }
//
//        //forSave_incidenciaEventos
//        val cacheList8 = mySettings?.getList("CACHE_forSave_incidenciaEventos")!!.toMutableList()
//        if (forSave_incidenciaEventos?.isEmpty() == true && cacheList8.isNotEmpty()){
//            forSave_incidenciaEventos= cacheList8 as MutableList<List<Any>>?
//            saveIncidenciaEventosToSheetWithRetry(listOf())
//        }
//    }
//
//
//    // Utilidad simple para detectar red
//    fun isNetworkAvailable(): Boolean {
////        if (System.currentTimeMillis() > 1764992520000)
////            return false
//        val connectivityManager = applicationContext?.getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            val network = connectivityManager.activeNetwork ?: return false
//            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
//            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
//                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
//        } else {
//            // Para versiones de Android anteriores a Marshmallow (API 23)
//            val networkInfo = connectivityManager.activeNetworkInfo
//            return networkInfo != null && networkInfo.isConnected
//        }
//    }
//
//    // Metodo externo para guardar datos en Google Sheets (implementa lo necesario aquí)
//    private suspend fun saveToGoogleSheets(range:String,data: List<List<String>>): Boolean {
//        if (isNetworkAvailable() == false ) return false
//        if (data.isEmpty() == true) return true //Nada que salvar
//
//        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//        val bodyEventos = ValueRange().setValues(data)
//        try {
//            // Save to PARKING_SPREADSHEET_ID
//            sheetsService.spreadsheets().values()
//                .append(yourEventsSpreadSheetID, range, bodyEventos)
//                .setValueInputOption("RAW")
//                .execute()
//            return true
//        } catch (e: Exception) {
//            throw e
//            return false
//        }
//    }
//    private fun updateToGoogleSheets(range:String,data: List<List<String>>): Boolean {
//        if (isNetworkAvailable() == false ) return false
//        if (data.isEmpty() == true) return true //Nada que salvar
//
//        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//        val bodyEventos = ValueRange().setValues(data)
//        try {
//            // Save to PARKING_SPREADSHEET_ID
//            sheetsService.spreadsheets().values()
//                .update(yourEventsSpreadSheetID, range, bodyEventos)
//                .setValueInputOption("RAW")
//                .execute()
//            return true
//        } catch (e: Exception) {
//            throw e
//            return false
//        }
//    }
//    private fun deleteToGoogleSheets(sheetName:String,indexToDelete: Int): Boolean{
//        if (indexToDelete>0) {
//            try {
//                val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                if (porRevisarSheetId == 0) {
//                    val spreadsheet = sheetsService.spreadsheets().get(yourEventsSpreadSheetID).execute()
//                    for (sheet in spreadsheet.sheets) {
//                        if (sheetName == sheet.properties.title) {
//                            porRevisarSheetId = sheet.properties.sheetId
//                            break
//                        }
//                    }
//                }
//
//                //Crear el batch para eliminar
//                val dimensionRange = DimensionRange()
//                    .setSheetId(porRevisarSheetId)
//                    .setDimension("ROWS")
//                    .setStartIndex(indexToDelete - 1)
//                    .setEndIndex(indexToDelete)
//                val deleteRequest = DeleteDimensionRequest().setRange(dimensionRange)
//                val request = Request().setDeleteDimension(deleteRequest)
//                val batchRequest = BatchUpdateSpreadsheetRequest().setRequests(listOf(request))
//                sheetsService.spreadsheets().batchUpdate(yourEventsSpreadSheetID, batchRequest)
//                    .execute()
//                return true
//            } catch (e: Exception) {
//                throw e
//                return false
//            }
//        }
//        return false
//    }
//    fun saveParkingSlotsToSheetWithRetry(dataRow:List<String>): Boolean{
//        if (dataRow.isNotEmpty()) {
//            forSave_parkingSlots?.add(dataRow)
//            mySettings?.saveList("CACHE_forSave_parkingSlots", forSave_parkingSlots as List<List<String>>)
//        }
//        if (isRunningSave_parkingSlots) return false //Solo debe correr uno a la ves
//        coroutineScope?.launch {
//            isRunningSave_parkingSlots=true
//            while (isActive) {
//                try {
//                    val nameWS                  = mySettings?.getString("WS_PARKING_SLOTS", "ParkingSlots")!!
//                    val success = saveToGoogleSheets("$nameWS!A:E",forSave_parkingSlots as List<List<String>>)
//                    if (success){ // Éxito, no necesita reintentar
//                        forSave_parkingSlots!!.clear()
//                        mySettings?.saveList("CACHE_forSave_parkingSlots", forSave_parkingSlots as List<List<String>>)
//                        isRunningSave_parkingSlots=false
//                        break
//                    }
//                    mySettings?.saveList("CACHE_forSave_parkingSlots", forSave_parkingSlots as List<List<String>>)
//                } catch (e: Exception) {
//                    mySettings?.saveList("CACHE_forSave_parkingSlots", forSave_parkingSlots as List<List<String>>)
//                    val customMessage = "Error al salvar registros a PARKING SLOTS sheet REINTENTAR en 5 Min: ${e.message}"
//                    //throw Exception(customMessage, e) // e is the original cause
//                    Log.d(TAG,customMessage)
//                }
//                delay(5.minutes)
//                // Reintenta
//            }
//        }
//        return true
//    }
//    fun saveAutosEventosToSheetWithRetry(dataRow:List<String>): Boolean{
//        if (dataRow.isNotEmpty()) {
//            forSave_autosEventos?.add(dataRow)
//            mySettings?.saveList("CACHE_forSave_autosEventos", forSave_autosEventos as List<List<String>>)
//        }
//        if (isRunningSave_autosEventos) return false //Solo debe correr uno a la ves
//        coroutineScope?.launch {
//            isRunningSave_autosEventos=true
//            while (isActive) {
//                try {
//                    val nameWS                  = mySettings?.getString("WS_AUTOS_EVENTOS", "AutosEventos")!!
//                    val success = saveToGoogleSheets("$nameWS!A:E",forSave_autosEventos as List<List<String>>)
//                    if (success){ // Éxito, no necesita reintentar
//                        forSave_autosEventos!!.clear()
//                        mySettings?.saveList("CACHE_forSave_autosEventos", forSave_autosEventos as List<List<String>>)
//                        isRunningSave_autosEventos=false
//                        break
//                    }
//                    mySettings?.saveList("CACHE_forSave_autosEventos", forSave_autosEventos as List<List<String>>)
//                } catch (e: Exception) {
//                    mySettings?.saveList("CACHE_forSave_autosEventos", forSave_autosEventos as List<List<String>>)
//                    val customMessage = "Error al salvar registros a EVENTOS AUTOS sheet REINTENTAR en 5 Min: ${e.message}"
//                    //throw Exception(customMessage, e) // e is the original cause
//                    Log.d(TAG,customMessage)
//                }
//                delay(5.minutes)
//                // Reintenta
//            }
//        }
//        return true
//    }
//    fun saveIncidenciaEventosToSheetWithRetry(dataRow: List<String>): Boolean{
//        if (dataRow.isNotEmpty()) {
//            forSave_incidenciaEventos?.add(dataRow)
//            mySettings?.saveList("CACHE_forSave_incidenciaEventos", forSave_incidenciaEventos as List<List<String>>)
//        }
//        if (isRunningSave_incidenciaEventos) return false //Solo debe correr uno a la ves
//        coroutineScope?.launch {
//            isRunningSave_incidenciaEventos=true
//            while (isActive) {
//                try {
//                    val nameWS  = mySettings?.getString("WS_INCIDENCIAS_EVENTOS", "IncidenciaEventos")!!
//                    val success = saveToGoogleSheets("$nameWS!A:G",forSave_incidenciaEventos as List<List<String>>)
//                    if (success){ // Éxito, no necesita reintentar
//                        forSave_incidenciaEventos!!.clear()
//                        mySettings?.saveList("CACHE_forSave_incidenciaEventos", forSave_incidenciaEventos as List<List<String>>)
//                        isRunningSave_incidenciaEventos=false
//                        break
//                    }
//                    mySettings?.saveList("CACHE_forSave_incidenciaEventos", forSave_incidenciaEventos as List<List<String>>)
//                } catch (e: Exception) {
//                    mySettings?.saveList("CACHE_forSave_incidenciaEventos", forSave_incidenciaEventos as List<List<String>>)
//                    val customMessage = "Error al salvar registros a INCIDENCIA EVENTOS sheet REINTENTAR en 5 Min: ${e.message}"
//                    //throw Exception(customMessage, e) // e is the original cause
//                    Log.d(TAG,customMessage)
//                }
//                delay(5.minutes)
//                // Reintenta
//            }
//        }
//        return true
//    }
//    fun savePorRevisarToSheetWithRetry(dataRow:List<String>): Boolean{
//        if (dataRow.isNotEmpty()) {
//            forSave_porRevisar?.add(dataRow)
//            mySettings?.saveList("CACHE_forSave_porRevisar", forSave_porRevisar as List<List<String>>)
//        }
//        if (isRunningSave_porRevisar) return false //Solo debe correr uno a la ves
//        coroutineScope?.launch {
//            isRunningSave_porRevisar=true
//            while (isActive) {
//                try {
//                    // street, number, time, parkingSlotKey, validation, lat, lon
//                    val nameWS      = mySettings?.getString("WS_POR_REVISAR", "PorRevisar")!!
//                    val success = saveToGoogleSheets("$nameWS!A:G",forSave_porRevisar as List<List<String>>)
//                    if (success){ // Éxito, no necesita reintentar
//                        forSave_porRevisar!!.clear()
//                        mySettings?.saveList("CACHE_forSave_porRevisar", forSave_porRevisar as List<List<String>>)
//                        isRunningSave_porRevisar=false
//                        break
//                    }
//                    mySettings?.saveList("CACHE_forSave_porRevisar", forSave_porRevisar as List<List<String>>)
//                } catch (e: Exception) {
//                    mySettings?.saveList("CACHE_forSave_porRevisar", forSave_porRevisar as List<List<String>>)
//                    val customMessage = "Error al salvar registros a EVENTOS AUTOS sheet REINTENTAR en 5 Min: ${e.message}"
//                    //throw Exception(customMessage, e) // e is the original cause
//                    Log.d(TAG,customMessage)
//                }
//                delay(5.minutes)
//                // Reintenta
//            }
//        }
//        return true
//    }
//    fun deletePorRevisarSheetWithRetry(indexToDelete: Int) : Boolean {
//        if (indexToDelete>=0) {
//            forDelete_porRevisarIndex?.add(indexToDelete.toString())
//            mySettings?.saveSingleList("CACHE_forDelete_porRevisarIndex", forDelete_porRevisarIndex as List<String>)
//        }
//        if (isRunningDelete_porRevisar) return false //Solo debe correr uno a la ves
//        coroutineScope?.launch {
//            isRunningDelete_porRevisar=true
//            while (isActive) {
//                try {
//                    // Fecha, calle, numero, placa, parkingSlot - (Fecha)
//                    val nameWS      = mySettings?.getString("WS_POR_REVISAR", "PorRevisar")!!
//                    forDelete_porRevisarIndex?.forEachIndexed { i, whatIndex ->
//                        if ( deleteToGoogleSheets(nameWS,whatIndex.toInt())){
//                            //Salvado remover del listado
//                            forDelete_porRevisarIndex?.removeAt(i)
//                        }else{
//                            return@forEachIndexed
//                        }
//                    }
//                    mySettings?.saveSingleList("CACHE_forDelete_porRevisarIndex", forDelete_porRevisarIndex as List<String>)
//                    if (forDelete_porRevisarIndex?.size == 0) {
//                        isRunningDelete_porRevisar = false
//                        break
//                    }
//                } catch (e: Exception) {
//                    mySettings?.saveSingleList("CACHE_forDelete_porRevisarIndex", forDelete_porRevisarIndex as List<String>)
//                    val customMessage = "Error al hacer ELIMINAR a PorRevisar sheet REINTENTAR en 5 Min: ${e.message}"
//                    //throw Exception(customMessage, e) // e is the original cause
//                    println(customMessage)
//                }
//                delay(5.minutes)
//                // Reintenta
//            }
//        }
//        return true
//    }
//    fun saveMultaGeneradaToSheetWithRetry(dataRow:List<String>): Boolean{
//        if (dataRow.isNotEmpty()) {
//            forSave_MultaGenerada?.add(dataRow)
//            mySettings?.saveList("CACHE_forSave_MultaGenerada", forSave_MultaGenerada as List<List<String>>)
//        }
//        if (isRunningSave_MultaGenerada) return false //Solo debe correr uno a la ves
//        coroutineScope?.launch {
//            isRunningSave_MultaGenerada=true
//            while (isActive) {
//                try {
//                    // Fecha, calle, numero, placa, parkingSlot - (Fecha)
//                    val nameWS  = mySettings?.getString("WS_MULTAS_GENERADAS", "MultasGeneradas")!!
//                    val success = saveToGoogleSheets("$nameWS!A:E",forSave_MultaGenerada as List<List<String>>)
//                    if (success){ // Éxito, no necesita reintentar
//                        forSave_MultaGenerada!!.clear()
//                        mySettings?.saveList("CACHE_forSave_MultaGenerada", forSave_MultaGenerada as List<List<String>>)
//                        isRunningSave_MultaGenerada=false
//                        break
//                    }
//                } catch (e: Exception) {
//                    mySettings?.saveList("CACHE_forSave_MultaGenerada", forSave_MultaGenerada as List<List<String>>)
//                    val customMessage = "Error al salvar registros a EVENTOS AUTOS sheet REINTENTAR en 5 Min: ${e.message}"
//                    //throw Exception(customMessage, e) // e is the original cause
//                    Log.d(TAG,customMessage)
//                }
//                delay(5.minutes)
//                // Reintenta
//            }
//        }
//        return true
//    }
//    fun updateMultaGeneradaToSheetWithRetry(dataRow:List<String>,indexToUpdate: Int) : Boolean{
//        if (dataRow.isNotEmpty()) {
//            forUpdate_MultaGenerada?.add(dataRow)
//            forUpdate_MultaGeneradaIndex?.add(indexToUpdate.toString())
//            mySettings?.saveList("CACHE_forUpdate_MultaGenerada", forUpdate_MultaGenerada as List<List<String>>)
//            mySettings?.saveSingleList("CACHE_forUpdate_MultaGeneradaIndex", forUpdate_MultaGeneradaIndex as List<String>)
//        }
//        if (isRunningUpdate_MultaGenerada) return false //Solo debe correr uno a la ves
//        coroutineScope?.launch {
//            isRunningUpdate_MultaGenerada=true
//            while (isActive) {
//                try {
//                    // Fecha, calle, numero, placa, parkingSlot - (Fecha)
//                    val nameWS  = mySettings?.getString("WS_MULTAS_GENERADAS", "MultasGeneradas")!!
//                    forUpdate_MultaGenerada?.forEachIndexed { i, multa ->
//                        val lMulta = listOf(multa)
//                        val whatIndex = forUpdate_DomicilioWarningsIndex?.get(i)
//                        if ( updateToGoogleSheets("$nameWS!A$whatIndex:E$whatIndex",
//                                lMulta as List<List<String>>
//                            )){
//                            //Salvado remover del listado
//                            forUpdate_MultaGenerada!!.removeAt(i)
//                            forUpdate_MultaGeneradaIndex?.removeAt(i)
//                        }else{
//                            return@forEachIndexed
//                        }
//                    }
//                    mySettings?.saveList("CACHE_forUpdate_MultaGenerada", forUpdate_MultaGenerada as List<List<String>>)
//                    mySettings?.saveSingleList("CACHE_forUpdate_MultaGeneradaIndex", forUpdate_MultaGeneradaIndex as List<String>)
//                    if (forUpdate_MultaGenerada?.size == 0) {
//                        isRunningUpdate_MultaGenerada = false
//                        break
//                    }
//                } catch (e: Exception) {
//                    mySettings?.saveList("CACHE_forUpdate_MultaGenerada", forUpdate_MultaGenerada as List<List<String>>)
//                    mySettings?.saveSingleList("CACHE_forUpdate_MultaGeneradaIndex", forUpdate_MultaGeneradaIndex as List<String>)
//                    val customMessage = "Error al salvar registros a EVENTOS AUTOS sheet REINTENTAR en 5 Min: ${e.message}"
//                    //throw Exception(customMessage, e) // e is the original cause
//                    Log.d(TAG,customMessage)
//                }
//                delay(5.minutes)
//                // Reintenta
//            }
//        }
//        return true
//    }
//    fun saveDomicilioWarningToSheetWithReatry(dataRow:List<String>): Boolean{
//        if (dataRow.isNotEmpty()) {
//            forSave_DomicilioWarnings?.add(dataRow)
//            mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forSave_DomicilioWarnings as List<List<String>>)
//        }
//        if (isRunningSave_DomicilioWarnings) return false //Solo debe correr uno a la ves
//        coroutineScope?.launch {
//            isRunningSave_DomicilioWarnings=true
//            while (isActive) {
//                try {
//                    // Fecha, calle, numero, placa, parkingSlot - (Fecha)
//                    val nameWS  = mySettings?.getString("WS_DOMICILIO_WARNINGS", "DomicilioWarnings")!!
//                    val success = saveToGoogleSheets("$nameWS!A:C",forSave_DomicilioWarnings as List<List<String>>)
//                    if (success){ // Éxito, no necesita reintentar
//                        forSave_DomicilioWarnings!!.clear()
//                        mySettings?.saveList("CACHE_forSave_DomicilioWarnings", listOf(listOf()) )
//                        isRunningSave_DomicilioWarnings=false
//                        break
//                    }
//                    mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forSave_DomicilioWarnings as List<List<String>>)
//                } catch (e: Exception) {
//                    mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forSave_DomicilioWarnings as List<List<String>>)
//                    val customMessage = "Error al salvar registros a DOMICILIO WARNINGS sheet REINTENTAR en 5 Min: ${e.message}"
//                    //throw Exception(customMessage, e) // e is the original cause
//                    Log.d(TAG, customMessage)
//                }
//                delay(5.minutes)
//                // Reintenta
//            }
//        }
//        return true
//    }
//    fun updateDomicilioWarningToSheetWithReatry(dataRow:List<String>,indexToUpdate: Int) : Boolean{
//        if (dataRow.isNotEmpty() && indexToUpdate >= 0) {
//            forUpdate_DomicilioWarnings?.add(dataRow)
//            forUpdate_DomicilioWarningsIndex?.add(indexToUpdate.toString())
//            mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forUpdate_DomicilioWarnings as List<List<String>>)
//            mySettings?.saveSingleList("CACHE_forUpdate_DomicilioWarningsIndex", forUpdate_DomicilioWarningsIndex as List<String>)
//        }
//        if (isRunningUpdate_DomicilioWarnings) return false //Solo debe correr uno a la ves
//        coroutineScope?.launch {
//            isRunningUpdate_DomicilioWarnings=true
//            while (isActive) {
//                try {
//                    // Fecha, calle, numero, placa, parkingSlot - (Fecha)
//                    forUpdate_DomicilioWarnings?.forEachIndexed { i, multa ->
//                        val lMulta = listOf(multa)
//                        val whatIndex = forUpdate_DomicilioWarningsIndex?.get(i)
//                        val nameWS  = mySettings?.getString("WS_DOMICILIO_WARNINGS", "DomicilioWarnings")!!
//                        if ( updateToGoogleSheets("$nameWS!A$whatIndex:D$whatIndex",
//                                lMulta as List<List<String>>
//                            )){
//                            //Salvado remover del listado
//                            forUpdate_DomicilioWarnings!!.removeAt(i)
//                            forUpdate_DomicilioWarningsIndex?.removeAt(i)
//                        }else{
//                            return@forEachIndexed
//                        }
//                    }
//                    mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forUpdate_DomicilioWarnings as List<List<String>>)
//                    mySettings?.saveSingleList("CACHE_forUpdate_DomicilioWarningsIndex", forUpdate_DomicilioWarningsIndex as List<String>)
//                    if (forUpdate_DomicilioWarnings?.size == 0) {
//                        isRunningUpdate_DomicilioWarnings = false
//                        break
//                    }
//                } catch (e: Exception) {
//                    mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forUpdate_DomicilioWarnings as List<List<String>>)
//                    mySettings?.saveSingleList("CACHE_forUpdate_DomicilioWarningsIndex", forUpdate_DomicilioWarningsIndex as List<String>)
//                    val customMessage = "Error al hacer UPDATE a DomicilioWarnings sheet REINTENTAR en 5 Min: ${e.message}"
//                    //throw Exception(customMessage, e) // e is the original cause
//                    Log.d(TAG,customMessage)
//                }
//                delay(5.minutes)
//                // Reintenta
//            }
//        }
//        return true
//    }
//
//    // Función nueva: cargar y actualizar datos si es necesario AQUI
//    fun getCachedVehiclesData( ): List<List<Any>> {
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM
//        val isMemoryFresh = platesCache != null && (now - platesCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isMemoryFresh) return platesCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("VEHICLE_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("VEHICLE_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//
//        if (isDiskFresh) {
//            platesCache = cacheList
//            platesCacheTimestamp = cacheTimestamp
//            return platesCache!!
//        }
//
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection == true) {
//            try {
//                GlobalScope.launch(Dispatchers.IO) {
//                    val allRows = mutableListOf<List<Any>>()
//                    //Vehiculos RESIDENTES
//                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                    val nameWS                  = mySettings?.getString("WS_AUTOS_REGISTRADOS", "AutosRegistrados")!!
//                    try {
//                        val response = sheetsService.spreadsheets().values()
//                            .get(
//                                yourEventsSpreadSheetID,
//                                "$nameWS!A:C"
//                            ) // placa, calle, numero, marca, modelo, color
//                            .execute()
//                        val rows = response.getValues() ?: emptyList()
//                        allRows.addAll(rows)
//                    } catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet $nameWS e:${e}")
//                    }
//
//                    //Vehiculos VISITANTES
//                    val yourSpreadSheetID =
//                        mySettings?.getString("REGISTRO_CARROS_SPREADSHEET_ID", "")
//                    val sheets = listOf("ingreso", "salida")
//                    for (sheet in sheets) {
//                        try{
//                            val response = sheetsService.spreadsheets().values()
//                                .get(
//                                    yourSpreadSheetID,
//                                    "$sheet!C:E"
//                                ) //placa calle numero tipo conductor
//                                .execute()
//                            val rows = response.getValues().drop(1) ?: emptyList()
//                            allRows.addAll(rows)
//                        } catch (e: Exception) {
//                            Log.d(TAG,"Error al leer el sheet $sheet e:${e}")
//                        }
//                    }
//
//                    withContext(Dispatchers.Main) {
//                        // Cachear y timestamp
//                        mySettings?.saveList("VEHICLE_CACHE", allRows as List<List<String>>)
//                        mySettings?.saveLong("VEHICLE_CACHE_TIMESTAMP", now)
//                        platesCache = allRows
//                        platesCacheTimestamp = now
//                    }
//                }
//                if (platesCache != null) return platesCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer el sheet e:${e}")
//                e.printStackTrace()
//                if (platesCache != null) return platesCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            }
//        } else {
//            // Sin red, usar cache vieja si existe
//            if (platesCache != null) return platesCache!!
//            if (cacheList.isNotEmpty()) return cacheList
//            // Si no hay nada, regresa vacío
//            return emptyList()
//        }
//        return emptyList()
//    }
//    fun getTagsCache(): List<List<Any>>{
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM
//        val isMemoryFresh = tagsCache != null && (now - tagsCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isMemoryFresh) return tagsCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("TAGS_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("TAGS_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//
//        if (isDiskFresh) {
//            tagsCache = cacheList
//            tagsCacheTimestamp = cacheTimestamp
//            return tagsCache!!
//        }
//
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection == true ) {
//            try {
//                //PARKING SLOTS
//                GlobalScope.launch(Dispatchers.IO) {
//
//                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                    val nameWS                  = mySettings?.getString("WS_AUTOS_REGISTRADOS", "AutosRegistrados")!!
//                    try{
//                        val response = sheetsService.spreadsheets().values()
//                            .get(yourEventsSpreadSheetID, "$nameWS!A:H") // Placas	Calle	Numero	Marca	Modelo	Color	Tag	Tag2	Tag3
//                            .execute()
//                        val rows = response.getValues().drop(1) ?: emptyList()
//                        var allRows: MutableList<List<Any>>? = mutableListOf<List<Any>>()
//                        rows.forEach { r ->
//                            try{
//                                if (r.size>=7 && r[6].toString().isNotEmpty()) {
//                                    val tmp: List<Any> = listOf(
//                                        r[6].toString(),
//                                        r[1].toString(),
//                                        r[2].toString()
//                                    )
//                                    allRows?.add(tmp)
//                                }
//                                if (r.size>=8 && r[7].toString().isNotEmpty()) {
//                                    val tmp: List<Any> = listOf(
//                                        r[7].toString(),
//                                        r[1].toString(),
//                                        r[2].toString()
//                                    )
//                                    allRows?.add(tmp)
//                                }
//                            } catch (e: Exception) {
//                                // Error al interpretar row en tags
//                                println("Error al interpretar el row ${r} error:${e}")
//                            }
//                        }
//
//                        withContext(Dispatchers.Main) {
//                            // Cachear y timestamp
//                            mySettings?.saveList("TAGS_CACHE", allRows as List<List<String>>)
//                            mySettings?.saveLong("TAGS_CACHE_TIMESTAMP", now)
//                            tagsCache = allRows
//                            tagsCacheTimestamp = now
//                        }
//                    }catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet $nameWS e:${e}")
//                    }
//                }
//
//                if (tagsCache != null) return tagsCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer el sheet e:${e}")
//                e.printStackTrace()
//                if (tagsCache != null) return tagsCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            }
//        }else{
//            // Sin red, usar cache vieja si existe
//            if (tagsCache != null) return tagsCache!!
//            if (cacheList.isNotEmpty()) return cacheList
//            // Si no hay nada, regresa vacío
//            return emptyList()
//        }
//        return emptyList()
//    }
//
//    // Permisos
//    fun getPermisosCache(): List<List<Any>>{
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM
//        val isMemoryFresh = permisosCache != null && (now - permisosCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isMemoryFresh) return permisosCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("PERMISOS_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("PERMISOS_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//
//        if (isDiskFresh) {
//            permisosCache = cacheList
//            permisosCacheTimestamp = cacheTimestamp
//            return permisosCache!!
//        }
//
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection == true ) {
//            try {
//                //PARKING SLOTS
//                GlobalScope.launch(Dispatchers.IO) {
//                    try{
//                        val yourEventsSpreadSheetID = mySettings?.getSimpleList("PERMISOS_SPREADSHEET_ID")!!
//                        val allRows = mutableListOf<List<Any>>()
//                        for (sheetIDpermission in yourEventsSpreadSheetID) {
//                            try {
//                                val response = sheetsService.spreadsheets().values()
//                                    .get(
//                                        sheetIDpermission.toString(),
//                                        "A:N"
//                                    ) // Marca temporal,	Calle,	Numero de casa,	Nombre de quien solicita el permiso,	Correo electrónico, 	Permiso para:,	Tipo Permiso (nota: si es renta o venta del inmueble indique en la descripcion el telefono a comunicarse),	Fecha Inicio del permiso,	Fecha Fin del permiso,	Descripción y/o trabajos a realizar,	Nombre de la(s) persona(s) a Ingresar,	Aprobado,	Motivo Denegado,	Procesado por ROBOT
//                                    .execute()
//                                val rows = response.getValues().drop(1) ?: emptyList()
//                                allRows.addAll(rows)
//                            }catch (e: Exception) {
//                                Log.d(TAG,"Error al leer el sheet $sheetIDpermission Respuestas de formulario 1 e:${e}")
//                            }
//                        }
//
//                        withContext(Dispatchers.Main) {
//                            // Cachear y timestamp
//                            mySettings?.saveList("PERMISOS_CACHE", allRows as List<List<String>>)
//                            mySettings?.saveLong("PERMISOS_CACHE_TIMESTAMP", now)
//                            permisosCache = allRows
//                            permisosCacheTimestamp = now
//                        }
//                    }catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet Respuestas de formulario 1 e:${e}")
//                    }
//                }
//
//                if (permisosCache != null) return permisosCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer el sheet e:${e}")
//                if (permisosCache != null) return permisosCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            }
//        }else{
//            // Sin red, usar cache vieja si existe
//            if (permisosCache != null) return permisosCache!!
//            if (cacheList.isNotEmpty()) return cacheList
//            // Si no hay nada, regresa vacío
//            return emptyList()
//        }
//        return emptyList()
//    }
//    fun getPermisosCache_DeHoy(): List<List<Any>>? {
//        val rows = getPermisosCache()
//        val timeFormatter = DateTimeFormatter.ofPattern("d/MM/yyyy")
//        val permisosHoy =
//            rows.filter {
//                LocalDate.now() >= LocalDate.parse(it[7].toString(),timeFormatter)
//                        &&  LocalDate.now() <= LocalDate.parse(it[8].toString(),timeFormatter)}
//                .reversed()
//        if (permisosHoy.isNotEmpty())
//            return permisosHoy as List<List<Any>>?
//        return emptyList()
//    }
//
//

//    fun getDomiciliosUbicacion( ): List<List<Any>> {
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM
//        val isMemoryFresh = directionsCache != null && (now - directionsCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isMemoryFresh) return directionsCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("DIRECTIONS_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("DIRECTIONS_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isDiskFresh) {
//            directionsCache = cacheList //as MutableList<List<Any>>
//            directionsCacheTimestamp = cacheTimestamp
//            return directionsCache!!
//        }
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection == true ) {
//            try {
//                //PARKING SLOTS
//                GlobalScope.launch(Dispatchers.IO) {
//                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                    val nameWS                  = mySettings?.getString("WS_DOMICILIOS_UBICACION", "DomicilioUbicacion")!!
//                    try{
//                        val response = sheetsService.spreadsheets().values()
//                            .get(yourEventsSpreadSheetID, "$nameWS!A:D") // calle, numero, latitud, longitud
//                            .execute()
//                        val allRows = response.getValues().drop(1) ?: emptyList()
//                        withContext(Dispatchers.Main) {
//                            // Cachear y timestamp
//                            mySettings?.saveList("DIRECTIONS_CACHE", allRows as List<List<String>>)
//                            mySettings?.saveLong("DIRECTIONS_CACHE_TIMESTAMP", now)
//                            directionsCache = allRows
//                            directionsCacheTimestamp = now
//
//                        }
//                    }catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet $nameWS e:${e}")
//                    }
//                }
//
//                if (directionsCache != null) return directionsCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer el sheet e:${e}")
//                if (directionsCache != null) return directionsCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            }
//        }else{
//            // Sin red, usar cache vieja si existe
//            if (directionsCache != null) return directionsCache!!
//            if (cacheList.isNotEmpty()) return cacheList
//            // Si no hay nada, regresa vacío
//            return emptyList()
//        }
//    }
//

//    fun getPorRevisar( force: Boolean = false): MutableList<List<Any>>? {
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM
//        val isMemoryFresh = porRevisarCache != null && (now - porRevisarCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isMemoryFresh and !force) return porRevisarCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("PORREVISAR_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("PORREVISAR_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isDiskFresh and !force) {
//            porRevisarCache = cacheList as MutableList<List<Any>>
//            porRevisarCacheTimestamp = cacheTimestamp
//            return porRevisarCache!!
//        }
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection==true) {
//            try {
//                //PARKING SLOTS
//                val allRows = mutableListOf<List<Any>>()
//
//                GlobalScope.launch(Dispatchers.IO) {
//                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                    val nameWS                  = mySettings?.getString("WS_POR_REVISAR", "PorRevisar")!!
//                    try{
//                        val response = sheetsService.spreadsheets().values()
//                            .get(yourEventsSpreadSheetID, "$nameWS!A:G") // street, number, time, parkingSlotKey, validation, lat, lon
//                            .execute()
//                        val rows = response.getValues().drop(1) ?: emptyList()
//
//                        withContext(Dispatchers.Main) {
//                            allRows.addAll(rows)
//                            // Cachear y timestamp
//                            mySettings?.saveList("PORREVISAR_CACHE", allRows as List<List<String>>)
//                            mySettings?.saveLong("PORREVISAR_CACHE_TIMESTAMP", now)
//                            porRevisarCache = allRows
//                            porRevisarCacheTimestamp = now
//
//                        }
//                    }catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet $nameWS e:${e}")
//                    }
//                }
//
//                if (porRevisarCache != null) return porRevisarCache!!
//                if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
//                return mutableListOf<List<Any>>()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer el sheet e:${e}")
//                if (porRevisarCache != null) return porRevisarCache!!
//                if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
//                return mutableListOf<List<Any>>()
//            }
//        }else{
//            // Sin red, usar cache vieja si existe
//            if (porRevisarCache != null) return porRevisarCache!!
//            if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
//            // Si no hay nada, regresa vacío
//            return mutableListOf<List<Any>>()
//        }
//    }
//    fun getPorRevisar_20horas(): MutableList<List<Any>>?{
//        val rows = getPorRevisar()
//        val date20HoursAgo = LocalDateTime.now().minusHours(20)
//        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//        val porRevisar = rows?.filter { LocalDateTime.parse(it[2].toString(),timeFormatter) >= date20HoursAgo }
//            ?.reversed()
//        if (porRevisar!=null && porRevisar.isNotEmpty())
//            return porRevisar as MutableList<List<Any>>?
//        return mutableListOf<List<Any>>()
//    }
//    fun _addPorRevisarCache(row: List<String>): Boolean{
//        // street, number, time, parkingSlotKey, validation, lat, lon
//        if (porRevisarCache == null) getPorRevisar()
//        porRevisarCache?.add(row)
//        mySettings?.saveList("PORREVISAR_CACHE", porRevisarCache as List<List<String>>)
//        mySettings?.saveLong("PORREVISAR_CACHE_TIMESTAMP", System.currentTimeMillis())
//        return savePorRevisarToSheetWithRetry(row)
//    }
//    fun eliminarPorRevisar(calle: String, numero: String, slotKey: String): Boolean{
//        var indexFind = -1
//        porRevisarCache?.forEachIndexed { index, row ->
//            if (row.size >= 2 && row[0].toString() == calle && row[1].toString() == numero && row[3].toString() == slotKey) {
//                indexFind = index
//                return@forEachIndexed
//            }
//        }
//        if (indexFind>=0) {
//            //Se encontro, entonces eliminar con retry
//            porRevisarCache?.removeAt(indexFind)
//            mySettings?.saveList("PORREVISAR_CACHE", porRevisarCache as List<List<String>>)
//            mySettings?.saveLong("PORREVISAR_CACHE_TIMESTAMP", System.currentTimeMillis())
//            return deletePorRevisarSheetWithRetry(indexFind + 2)
//        }
//        return true
//    }
//

//    fun getParkingSlots( ): List<List<Any>>{
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM
//        val isMemoryFresh = parkingSlotsCache != null && (now - parkingSlotsCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isMemoryFresh) return parkingSlotsCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("PARKINGSLOTS_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("PARKINGSLOTS_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isDiskFresh) {
//            parkingSlotsCache = cacheList as MutableList<List<Any>>
//            parkingSlotsCacheTimestamp = cacheTimestamp
//            return parkingSlotsCache!!
//        }
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection==true) {
//            try {
//                //PARKING SLOTS
//                GlobalScope.launch(Dispatchers.IO) {
//                    val allRows = mutableListOf<List<Any>>()
//                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                    val nameWS                  = mySettings?.getString("WS_PARKING_SLOTS", "ParkingSlots")!!
//                    try{
//                        val response = sheetsService.spreadsheets().values()
//                            .get(yourEventsSpreadSheetID, "$nameWS!A:C") // Latitude, Longitude, Key
//                            .execute()
//                        val rows = response.getValues().drop(1) ?: emptyList()
//                        allRows.addAll(rows)
//                        withContext(Dispatchers.Main) {
//                            // Cachear y timestamp
//                            mySettings?.saveList("PARKINGSLOTS_CACHE", allRows as List<List<String>>)
//                            mySettings?.saveLong("PARKINGSLOTS_CACHE_TIMESTAMP", now)
//                            parkingSlotsCache = allRows
//                            parkingSlotsCacheTimestamp = now
//
//                        }
//                    }catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet $nameWS e:${e}")
//                    }
//                }
//
//                if (parkingSlotsCache != null) return parkingSlotsCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer sheet e:${e}")
//                if (parkingSlotsCache != null) return parkingSlotsCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            }
//        }else{
//            // Sin red, usar cache vieja si existe
//            if (parkingSlotsCache != null) return parkingSlotsCache!!
//            if (cacheList.isNotEmpty()) return cacheList
//            // Si no hay nada, regresa vacío
//            return emptyList()
//        }
//    }
//    fun _addPartkingSlotCache(row: List<String>): Boolean{
//        if (parkingSlotsCache == null) getParkingSlots()
//        parkingSlotsCache?.add(row)
//        mySettings?.saveList("PARKINGSLOTS_CACHE", parkingSlotsCache as List<List<String>>)
//        mySettings?.saveLong("PARKINGSLOTS_CACHE_TIMESTAMP", System.currentTimeMillis())
//        return saveParkingSlotsToSheetWithRetry(row)
//    }
//

//    fun getAutosEventos( ): List<List<Any>>{
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM
//        val isMemoryFresh = autosEventosCache != null && (now - autosEventosCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isMemoryFresh) return autosEventosCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("EVENTOS_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("EVENTOS_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//
//        if (isDiskFresh) {
//            autosEventosCache = cacheList as MutableList<List<Any>>
//            autosEventosCacheTimestamp = cacheTimestamp
//            return autosEventosCache!!
//        }
//
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection == true) {
//
//            try {
//                //AUTOS EVENTOS
//                val allRows = mutableListOf<List<Any>>()
//                val date15daysAgo = LocalDate.now().minusDays(15)
//                val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                val nameWS = mySettings?.getString("WS_AUTOS_EVENTOS", "AutosEventos")!!
//                GlobalScope.launch(Dispatchers.IO) {
//                    try {
//                        val response = sheetsService.spreadsheets().values()
//                            .get(
//                                yourEventsSpreadSheetID,
//                                "$nameWS!A:E"
//                            ) // placa, date, time, localPhotoPath, ParkingSlotKey
//                            .execute()
//                        val rows = response.getValues().drop(1) ?: emptyList()
//
//                        withContext(Dispatchers.Main) {
//                            // Update Data
//                            val solo15dias = rows.filter {
//                                it.size >= 5 && it[1].toString().length == 10 && LocalDate.parse(it[1].toString()) >= date15daysAgo
//                            }.reversed()
//                            allRows.addAll(solo15dias)
//                            // Cachear y timestamp
//                            mySettings?.saveList("EVENTOS_CACHE", allRows as List<List<String>>)
//                            mySettings?.saveLong("EVENTOS_CACHE_TIMESTAMP", now)
//                            autosEventosCache = allRows
//                            autosEventosCacheTimestamp = now
//                        }
//                    }catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet $nameWS e:${e}")
//                    }
//                }
//
//                if (autosEventosCache != null) return autosEventosCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer el sheet e:${e}")
//                e.printStackTrace()
//                if (autosEventosCache != null) return autosEventosCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            }
//        } else {
//            // Sin red, usar cache vieja si existe
//            if (autosEventosCache != null) return autosEventosCache!!
//            if (cacheList.isNotEmpty()) return cacheList
//            // Si no hay nada, regresa vacío
//            return emptyList()
//        }
//    }
//    fun getAutosEventos_6horas( ): List<List<Any>>{
//        val rows = getAutosEventos()
//        val date6HoursAgo = LocalDateTime.now().minusHours(6)
//        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//        val plateEvents = rows.filter { LocalDateTime.parse(it[2].toString(),timeFormatter) >= date6HoursAgo }
//            .reversed()
//        return plateEvents ?: emptyList()
//    }
//    fun _addAutosEventCache(row: List<String>): Boolean{
//        if (autosEventosCache == null) getAutosEventos()
//        autosEventosCache?.add(row)
//        mySettings?.saveList("EVENTOS_CACHE", autosEventosCache as List<List<String>>)
//        mySettings?.saveLong("EVENTOS_CACHE_TIMESTAMP", System.currentTimeMillis())
//        return saveAutosEventosToSheetWithRetry(row)
//    }
//

//    fun getIncidenciasConfig(): List<List<Any>>{
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM 24Hrs para refresh
//        val isMemoryFresh = incidenciaConfigCache != null && (now - incidenciaConfigCacheTimestamp <= (CACHE_DURATION_MS * 24) || thereConection==false)
//        if (isMemoryFresh) return incidenciaConfigCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("INCIDENCIACONFIG_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("INCIDENCIACONFIG_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= (CACHE_DURATION_MS * 24) || thereConection==false)
//        if (isDiskFresh) {
//            incidenciaConfigCache = cacheList //as MutableList<List<Any>>
//            incidenciaConfigCacheTimestamp = cacheTimestamp
//            return incidenciaConfigCache!!
//        }
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection == true) {
//            try {
//                GlobalScope.launch(Dispatchers.IO) {
//                    //PARKING SLOTS
//                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                    val nameWS                  = mySettings?.getString("WS_INCIDENCIAS_CONFIG", "IncidenciaConfig")!!
//                    try {
//                        val response = sheetsService.spreadsheets().values()
//                            .get(
//                                yourEventsSpreadSheetID,
//                                "$nameWS!A:D"
//                            ) // key, textoButton, maxWarning, descLegal
//                            .execute()
//                        val allRows = response.getValues().drop(1) ?: emptyList()
//
//                        withContext(Dispatchers.Main) {
//                            mySettings?.saveList(
//                                "INCIDENCIACONFIG_CACHE",
//                                allRows as List<List<String>>
//                            )
//                            mySettings?.saveLong("INCIDENCIACONFIG_CACHE_TIMESTAMP", now)
//                            incidenciaConfigCache = allRows
//                            incidenciaConfigCacheTimestamp = now
//                        }
//                    }catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet $nameWS e:${e}")
//                    }
//                }
//
//                if (incidenciaConfigCache != null) return incidenciaConfigCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer el sheet e:${e}")
//                if (incidenciaConfigCache != null) return incidenciaConfigCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            }
//        }else{
//            // Sin red, usar cache vieja si existe
//            if (incidenciaConfigCache != null) return incidenciaConfigCache!!
//            if (cacheList.isNotEmpty()) return cacheList
//            // Si no hay nada, regresa vacío
//            return emptyList()
//        }
//    }
//    fun getIncidenciasEventos(): List<List<Any>>{
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM
//        val isMemoryFresh = incidenciaEventosCache != null && (now - incidenciaEventosCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isMemoryFresh) return incidenciaEventosCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("INCIDENCIAEVENTOS_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("INCIDENCIAEVENTOS_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//
//        if (isDiskFresh) {
//            incidenciaEventosCache = cacheList as MutableList<List<Any>>
//            incidenciaEventosCacheTimestamp = cacheTimestamp
//            return incidenciaEventosCache!!
//        }
//
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection == true) {
//
//            try {
//                //AUTOS EVENTOS
//                val allRows = mutableListOf<List<Any>>()
//                val date15daysAgo = LocalDate.now().minusDays(15)
//                GlobalScope.launch(Dispatchers.IO) {
//
//                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                    val nameWS                  = mySettings?.getString("WS_INCIDENCIAS_EVENTOS", "IncidenciaEventos")!!
//                    try{
//                        val response = sheetsService.spreadsheets().values()
//                            .get(yourEventsSpreadSheetID, "$nameWS!A:G") // calle, numero, date, time, Tipo, localPhotoPath, descripcion
//                            .execute()
//                        val rows = response.getValues().drop(1) ?: emptyList()
//                        withContext(Dispatchers.Main) {
//                            // Update Data
//                            val solo15dias= rows.filter { it.size >= 5 && it[2].toString().length==10 && LocalDate.parse(it[2].toString()) >= date15daysAgo }.reversed()
//                            allRows.addAll(solo15dias)
//                            // Cachear y timestamp
//                            mySettings?.saveList("INCIDENCIAEVENTOS_CACHE", allRows as List<List<String>>)
//                            mySettings?.saveLong("INCIDENCIAEVENTOS_CACHE_TIMESTAMP", now)
//                            incidenciaEventosCache = allRows
//                            incidenciaEventosCacheTimestamp = now
//
//                        }
//                    }catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet $nameWS e:${e}")
//                    }
//                }
//
//                if (incidenciaEventosCache != null) return incidenciaEventosCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer el sheet e:${e}")
//                if (incidenciaEventosCache != null) return incidenciaEventosCache!!
//                if (cacheList.isNotEmpty()) return cacheList
//                return emptyList()
//            }
//        } else {
//            // Sin red, usar cache vieja si existe
//            if (incidenciaEventosCache != null) return incidenciaEventosCache!!
//            if (cacheList.isNotEmpty()) return cacheList
//            // Si no hay nada, regresa vacío
//            return emptyList()
//        }
//    }
//    fun getIncidenciasEventosTipo(Tipo: String, fechaDay: LocalDate = LocalDate.now()): List<List<Any>>{
//        val rows = getIncidenciasEventos()
//        //val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//        val IncidenciaEvents = rows.filter { LocalDate.parse(it[2].toString()) == fechaDay && it[4].toString().uppercase() == Tipo.uppercase() }
//        return IncidenciaEvents ?: emptyList()
//
//    }
//    fun addIncidenciaEvento(row: List<String>): Boolean {
//        if (incidenciaEventosCache == null) getIncidenciasEventos()
//        incidenciaEventosCache?.add(row)
//        mySettings?.saveList("INCIDENCIAEVENTOS_CACHE", incidenciaEventosCache as List<List<String>>)
//        mySettings?.saveLong("INCIDENCIAEVENTOS_CACHE_TIMESTAMP", System.currentTimeMillis())
//        return saveIncidenciaEventosToSheetWithRetry(row)
//    }
//
//

//    fun getMultas():MutableList<List<Any>>?{
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM
//        val isMemoryFresh = multasCache != null && (now - multasCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isMemoryFresh) return multasCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("MULTAS_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("MULTAS_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//
//        if (isDiskFresh) {
//            multasCache = cacheList as MutableList<List<Any>>
//            multasCacheTimestamp = cacheTimestamp
//            return multasCache!!
//        }
//
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection == true ) {
//
//            try {
//                //AUTOS EVENTOS
//                val allRows = mutableListOf<List<Any>>()
//                val date15daysAgo = LocalDate.now().minusDays(15)
//                GlobalScope.launch(Dispatchers.IO) {
//
//                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                    val nameWS                  = mySettings?.getString("WS_MULTAS_GENERADAS", "MultasGeneradas")!!
//                    try{
//                        val response = sheetsService.spreadsheets().values()
//                            .get(yourEventsSpreadSheetID, "$nameWS!A:E") // Fecha,	Calle,	Numero,	Placa,	PerkingSlot - (Fecha)
//                            .execute()
//                        val rows = response.getValues().drop(1) ?: emptyList()
//                        withContext(Dispatchers.Main) {
//                            allRows.addAll(rows)
//                            // Cachear y timestamp
//                            mySettings?.saveList("MULTAS_CACHE", allRows as List<List<String>>)
//                            mySettings?.saveLong("MULTAS_CACHE_TIMESTAMP", now)
//                            multasCache = allRows
//                            multasCacheTimestamp = now
//
//                        }
//                    }catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet $nameWS e:${e}")
//                    }
//                }
//
//                if (multasCache != null) return multasCache!!
//                if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
//                return mutableListOf<List<Any>>()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer el sheet e:${e}")
//                if (multasCache != null) return multasCache!!
//                if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
//                return mutableListOf<List<Any>>()
//            }
//        } else {
//            // Sin red, usar cache vieja si existe
//            if (multasCache != null) return multasCache!!
//            if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
//            // Si no hay nada, regresa vacío
//            return mutableListOf<List<Any>>()
//        }
//    }
//    fun addMulta(row: List<String>): Boolean{
//        if (multasCache == null) getMultas()
//        multasCache?.add(row)
//        mySettings?.saveList("MULTAS_CACHE", multasCache as List<List<String>>)
//        mySettings?.saveLong("MULTAS_CACHE_TIMESTAMP", System.currentTimeMillis())
//        return saveMultaGeneradaToSheetWithRetry(row)
//    }
//    fun updateMulta(row:List<String>): Boolean{
//        var indexFind = -1
//        var currentCount = 0
//        multasCache?.forEachIndexed { index, multa ->
//            if (multa.size >= 2 && multa[0].toString() == row[0].toString() && multa[1].toString() == row[1].toString()) {
//                indexFind = index
//                currentCount = multa[2].toString().toIntOrNull() ?: 0
//                return@forEachIndexed
//            }
//        }
//        val newData = listOf(
//            row[0].toString(),
//            row[1].toString(),
//            currentCount + 1
//        )
//        if (indexFind>=0){
//            multasCache?.set(indexFind,newData)
//            mySettings?.saveList("MULTAS_CACHE", multasCache as List<List<String>>)
//            mySettings?.saveLong("MULTAS_CACHE_TIMESTAMP", System.currentTimeMillis())
//            return updateMultaGeneradaToSheetWithRetry(newData as List<String>,indexFind+2)
//        }else{
//            //Es nuevo entonces agregarlo
//            return addMulta(row)
//        }
//        return false
//    }
//

//    fun getDomicilioWarnings():MutableList<List<Any>>?{
//        val now = System.currentTimeMillis()
//        val thereConection = isNetworkAvailable()
//        // 1. Revisa caché de memoria RAM
//        val isMemoryFresh = domicilioWarningsCache != null && (now - domicilioWarningsCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//        if (isMemoryFresh) return domicilioWarningsCache!!
//
//        // 2. Si RAM no, revisa persisted cache (MySettings)
//        val cacheList = mySettings?.getList("DOMICILIOWARNINGS_CACHE")!!.toMutableList()
//        val cacheTimestamp = mySettings?.getLong("DOMICILIOWARNINGS_CACHE_TIMESTAMP", 0L) ?: 0L
//        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
//
//        if (isDiskFresh) {
//            domicilioWarningsCache = cacheList as MutableList<List<Any>>
//            domicilioWarningsCacheTimestamp = cacheTimestamp
//            return domicilioWarningsCache!!
//        }
//
//        // 3. Si hace falta actualizar y hay Internet
//        if (thereConection == true) {
//
//            try {
//                //AUTOS EVENTOS
//                val allRows = mutableListOf<List<Any>>()
//                //val date15daysAgo = LocalDate.now().minusDays(15)
//                GlobalScope.launch(Dispatchers.IO) {
//
//                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                    val nameWS                  = mySettings?.getString("WS_DOMICILIO_WARNINGS", "DomicilioWarnings")!!
//                    try{
//                        val response = sheetsService.spreadsheets().values()
//                            .get(yourEventsSpreadSheetID, "$nameWS!A:D") // Calle, Numero, ContadorWarning, Tipo
//                            .execute()
//                        val rows = response.getValues().drop(1) ?: emptyList()
//
//                        withContext(Dispatchers.Main) {
//                            allRows.addAll(rows)
//                            // Cachear y timestamp
//                            mySettings?.saveList("DOMICILIOWARNINGS_CACHE", allRows as List<List<String>>)
//                            mySettings?.saveLong("DOMICILIOWARNINGS_CACHE_TIMESTAMP", now)
//                            domicilioWarningsCache = allRows
//                            domicilioWarningsCacheTimestamp = now
//
//                        }
//                    }catch (e: Exception) {
//                        Log.d(TAG,"Error al leer el sheet $nameWS e:${e}")
//                    }
//                }
//
//                if (domicilioWarningsCache != null) return domicilioWarningsCache!!
//                if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
//                return mutableListOf<List<Any>>()
//            } catch (e: Exception) {
//                // Si falla recarga, pero hay cache local reciente, úsala
//                Log.d(TAG,"Error al leer el sheet e:${e}")
//                if (domicilioWarningsCache != null) return domicilioWarningsCache!!
//                if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
//                return mutableListOf<List<Any>>()
//            }
//        } else {
//            // Sin red, usar cache vieja si existe
//            if (domicilioWarningsCache != null) return domicilioWarningsCache!!
//            if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
//            // Si no hay nada, regresa vacío
//            return mutableListOf<List<Any>>()
//        }
//    }
//    fun addDomicilioWarning(row: List<String>): Boolean{
//        if (domicilioWarningsCache == null) getDomicilioWarnings()
//        domicilioWarningsCache?.add(row)
//        mySettings?.saveList("DOMICILIOWARNINGS_CACHE", domicilioWarningsCache as List<List<String>>)
//        mySettings?.saveLong("DOMICILIOWARNINGS_CACHE_TIMESTAMP", System.currentTimeMillis())
//        return saveDomicilioWarningToSheetWithReatry(row)
//    }
//    fun updateDomicilioWarning(row: List<String>): Int{
//        var indexFind = -1
//        var currentCount = 0
//        if (domicilioWarningsCache == null) getDomicilioWarnings()
//        domicilioWarningsCache?.forEachIndexed { index, domWarn ->
//            if (domWarn.size >= 2 &&
//                domWarn[0].toString() == row[0].toString() &&  //calle
//                domWarn[1].toString() == row[1].toString() &&  //numero
//                domWarn[3].toString() == row[3].toString()     //tipo
//                ) {
//                    indexFind = index
//                    currentCount = domWarn[2].toString().toIntOrNull() ?: 0
//                    return@forEachIndexed
//            }
//        }
//        val newData = listOf(
//            row[0].toString(),
//            row[1].toString(),
//            (currentCount + 1).toString(),
//            row[3].toString()
//        )
//        if (indexFind>=0){
//            domicilioWarningsCache?.set(indexFind,newData)
//            mySettings?.saveList("DOMICILIOWARNINGS_CACHE", domicilioWarningsCache as List<List<String>>)
//            mySettings?.saveLong("DOMICILIOWARNINGS_CACHE_TIMESTAMP", System.currentTimeMillis())
//            if (updateDomicilioWarningToSheetWithReatry(newData as List<String>,indexFind+2) == true)
//                return currentCount + 1
//        }else{
//            //Es nuevo entonces agregarlo
//            if (addDomicilioWarning(newData as List<String>) == true)
//                return currentCount + 1
//        }
//        return currentCount + 1
//    }
}