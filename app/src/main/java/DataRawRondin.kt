import MySettings
import android.content.Context
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes
import android.content.Context.CONNECTIVITY_SERVICE
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.larangel.rondingpeinn.R
import com.larangel.rondingpeinn.VehicleSearchActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DataRawRondin(context: Context, coroutineScopeObject: CoroutineScope) {
    private var mySettings: MySettings? = null
    private var applicationContext: Context? = null
    private var coroutineScope: CoroutineScope? = null

    private lateinit var sheetsService: Sheets

    // Variables de cache y tiempo
    private var platesCache: List<List<Any>>? = null
    private var parkingSlotsCache: List<List<Any>>? = null
    private var autosEventosCache: MutableList<List<Any>>? = null
    private var directionsCache: List<List<Any>>? = null
    private var porRevisarCache: MutableList<List<Any>>? = null
    private var platesCacheTimestamp: Long = 0
    private var parkingSlotsCacheTimestamp: Long = 0
    private var autosEventosCacheTimestamp: Long = 0
    private var directionsCacheTimestamp: Long = 0
    private var porRevisarCacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 60 * 60 * 1000 // 1 hora

    //Variables temporales para salvarl
    private var forSave_autosEventos: MutableList<List<Any>>? = mutableListOf<List<Any>>()
    private var isRunningSave_autosEventos: Boolean = false
    private var forSave_porRevisar: MutableList<List<Any>>? = mutableListOf<List<Any>>()
    private var isRunningSave_porRevisar: Boolean = false

    init {
        applicationContext = context
        mySettings = MySettings(context)
        coroutineScope = coroutineScopeObject

        initializeGoogleServices()
    }

    private fun initializeGoogleServices() {
        try {
            val serviceAccountStream =  applicationContext?.resources?.openRawResource(R.raw.json_google_service_account)
            val credential = GoogleCredential.fromStream(serviceAccountStream)
                .createScoped(listOf("https://www.googleapis.com/auth/drive", "https://www.googleapis.com/auth/spreadsheets"))
            sheetsService = Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("My First Project")
                .build()
            println("LARANGEL sheetsService:${sheetsService}")

        // Get sheet ID Parking SHEET
        //val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!


//            val spreadsheet = sheetsService.spreadsheets().get(yourEventsSpreadSheetID).execute()
//            for (sheet in spreadsheet.sheets) {
//                when (sheet.properties.title) {
//                    "PorRevisar" -> porRevisarSheetId = sheet.properties.sheetId
//                    "DomicilioWarnings" -> domicilioWarningsSheetId = sheet.properties.sheetId
//                }
//            }
        } catch (e: Exception) {
            val customMessage = "Error al inicializar sheet ID de PARKING: ${e.message}"
            throw Exception(customMessage, e) // e is the original cause
        }

    }

    // Metodo externo para guardar datos en Google Sheets (implementa lo necesario aquí)
    private suspend fun saveToGoogleSheets(range:String,data: List<List<String>>): Boolean {
        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        val bodyEventos = ValueRange().setValues(data)
        try {
            // Save to PARKING_SPREADSHEET_ID
            sheetsService.spreadsheets().values()
                .append(yourEventsSpreadSheetID, range, bodyEventos)
                .setValueInputOption("RAW")
                .execute()
            return true
        } catch (e: Exception) {
            throw e
            return false
        }
    }

//    // Metodo público para iniciar el intento automático
//    fun saveDataWithRetry(data: List<List<String>>) {
//        coroutineScope?.launch {
//            while (isActive) {
//                try {
//                    val success = saveToGoogleSheets(data)
//                    if (success) break // Éxito, no necesita reintentar
//                } catch (e: Exception) {
//                    // Ocurrió un error (por ejemplo, sin conexión)
//                    val customMessage = "Error al salvar registros a : ${e.message}"
//                    throw Exception(customMessage, e) // e is the original cause
//                }
//                delay(5.minutes)
//                // Reintenta
//            }
//        }
//    }

    fun saveAutosEventosToSheetWithRetry(dataRow:List<String>){
        forSave_autosEventos?.add(dataRow)
        if (isRunningSave_autosEventos) return //Solo debe correr uno a la ves
        coroutineScope?.launch {
            isRunningSave_autosEventos=true
            while (isActive) {
                try {
                    val success = saveToGoogleSheets("AutosEventos!A:E",forSave_autosEventos as List<List<String>>)
                    if (success){ // Éxito, no necesita reintentar
                        forSave_autosEventos!!.clear()
                        isRunningSave_autosEventos=false
                        break
                    }
                } catch (e: Exception) {
                    mySettings?.saveLong("EVENTOS_CACHE_TIMESTAMP", System.currentTimeMillis()) //Actualiza el timer del cache para no recargar
                    val customMessage = "Error al salvar registros a EVENTOS AUTOS sheet REINTENTAR en 5 Min: ${e.message}"
                    throw Exception(customMessage, e) // e is the original cause
                }
                delay(5.minutes)
                // Reintenta
            }
        }
    }
    fun savePorRevisarToSheetWithRetry(dataRow:List<String>){
        forSave_porRevisar?.add(dataRow)
        if (isRunningSave_porRevisar) return //Solo debe correr uno a la ves
        coroutineScope?.launch {
            isRunningSave_porRevisar=true
            while (isActive) {
                try {
                    // street, number, time, parkingSlotKey, validation, lat, lon
                    val success = saveToGoogleSheets("PorRevisar!A:G",forSave_porRevisar as List<List<String>>)
                    if (success){ // Éxito, no necesita reintentar
                        forSave_porRevisar!!.clear()
                        isRunningSave_porRevisar=false
                        break
                    }
                } catch (e: Exception) {
                    mySettings?.saveLong("PORREVISAR_CACHE_TIMESTAMP", System.currentTimeMillis()) //Actualiza el timer del cache para no recargar
                    val customMessage = "Error al salvar registros a EVENTOS AUTOS sheet REINTENTAR en 5 Min: ${e.message}"
                    throw Exception(customMessage, e) // e is the original cause
                }
                delay(5.minutes)
                // Reintenta
            }
        }
    }

    // Función nueva: cargar y actualizar datos si es necesario
    fun getCachedVehiclesData( isNetworkAvailable: Boolean): List<List<Any>> {
        val now = System.currentTimeMillis()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = platesCache != null && now - platesCacheTimestamp <= CACHE_DURATION_MS
        if (isMemoryFresh) return platesCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("VEHICLE_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("VEHICLE_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && now - cacheTimestamp <= CACHE_DURATION_MS

        if (isDiskFresh) {
            platesCache = cacheList
            platesCacheTimestamp = cacheTimestamp
            return platesCache!!
        }

        // 3. Si hace falta actualizar y hay Internet
        if (isNetworkAvailable) {
            try {
                //Vehiculos VISITANTES
                val yourSpreadSheetID = mySettings?.getString("REGISTRO_CARROS_SPREADSHEET_ID", "")
                val sheets = listOf("ingreso", "salida")
                val allRows = mutableListOf<List<Any>>()
                for (sheet in sheets) {
                    val response = sheetsService.spreadsheets().values()
                        .get(yourSpreadSheetID, "$sheet!C:E") //fechaCreado fechaIngreso placa calle numero tipo conductor
                        .execute()
                    val rows = response.getValues().drop(1) ?: emptyList()
                    allRows.addAll(rows)
                }

                //Vehiculos RESIDENTES
                val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                val response = sheetsService.spreadsheets().values()
                    .get(yourEventsSpreadSheetID, "AutosRegistrados!A:C") // placa, calle, numero, marca, modelo, color
                    .execute()
                val rows = response.getValues() ?: emptyList()
                allRows.addAll(rows)


                // Cachear y timestamp
                mySettings?.saveList("VEHICLE_CACHE", allRows as List<List<String>>)
                mySettings?.saveLong("VEHICLE_CACHE_TIMESTAMP", now)
                platesCache = allRows
                platesCacheTimestamp = now
                return allRows
            } catch (e: Exception) {
                // Si falla recarga, pero hay cache local reciente, úsala
                if (platesCache != null) return platesCache!!
                throw e
            }
        } else {
            // Sin red, usar cache vieja si existe
            if (platesCache != null) return platesCache!!
            // Si no hay nada, regresa vacío
            return emptyList()
        }
    }

    //Direcciones de casas y ubicacion
    fun getDomiciliosUbicacion( isNetworkAvailable: Boolean): List<List<Any>> {
        val now = System.currentTimeMillis()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = directionsCache != null && now - directionsCacheTimestamp <= CACHE_DURATION_MS
        if (isMemoryFresh) return directionsCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("DIRECTIONS_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("DIRECTIONS_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && now - cacheTimestamp <= CACHE_DURATION_MS
        if (isDiskFresh) {
            directionsCache = cacheList //as MutableList<List<Any>>
            directionsCacheTimestamp = cacheTimestamp
            return directionsCache!!
        }
        // 3. Si hace falta actualizar y hay Internet
        if (isNetworkAvailable) {
            try {
                //PARKING SLOTS
                GlobalScope.launch(Dispatchers.IO) {
                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                    val response = sheetsService.spreadsheets().values()
                        .get(yourEventsSpreadSheetID, "DomicilioUbicacion!A:D") // calle, numero, latitud, longitud
                        .execute()
                    val allRows = response.getValues().drop(1) ?: emptyList()
                    withContext(Dispatchers.Main) {
                        // Cachear y timestamp
                        mySettings?.saveList("DIRECTIONS_CACHE", allRows as List<List<String>>)
                        mySettings?.saveLong("DIRECTIONS_CACHE_TIMESTAMP", now)
                        directionsCache = allRows
                        directionsCacheTimestamp = now

                    }
                }

                return directionsCache ?: emptyList()
            } catch (e: Exception) {
                // Si falla recarga, pero hay cache local reciente, úsala
                if (directionsCache != null) return directionsCache!!
                throw e
            }
        }else{
            // Sin red, usar cache vieja si existe
            if (directionsCache != null) return directionsCache!!
            // Si no hay nada, regresa vacío
            return emptyList()
        }
    }

    //Por Revisar registros
    fun getPorRevisar( isNetworkAvailable: Boolean, force: Boolean = false): MutableList<List<Any>>{
        val now = System.currentTimeMillis()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = porRevisarCache != null && now - porRevisarCacheTimestamp <= CACHE_DURATION_MS
        if (isMemoryFresh and !force) return porRevisarCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("PORREVISAR_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("PORREVISAR_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && now - cacheTimestamp <= CACHE_DURATION_MS
        if (isDiskFresh and !force) {
            porRevisarCache = cacheList as MutableList<List<Any>>
            porRevisarCacheTimestamp = cacheTimestamp
            return porRevisarCache!!
        }
        // 3. Si hace falta actualizar y hay Internet
        if (isNetworkAvailable) {
            try {
                //PARKING SLOTS
                val allRows = mutableListOf<List<Any>>()
                GlobalScope.launch(Dispatchers.IO) {
                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                    val response = sheetsService.spreadsheets().values()
                        .get(yourEventsSpreadSheetID, "PorRevisar!A:G") // street, number, time, parkingSlotKey, validation, lat, lon
                        .execute()
                    val rows = response.getValues().drop(1) ?: emptyList()
                    withContext(Dispatchers.Main) {
                        allRows.addAll(rows)
                        // Cachear y timestamp
                        mySettings?.saveList("PORREVISAR_CACHE", allRows as List<List<String>>)
                        mySettings?.saveLong("PORREVISAR_CACHE_TIMESTAMP", now)
                        porRevisarCache = allRows
                        porRevisarCacheTimestamp = now

                    }
                }

                return porRevisarCache ?: mutableListOf<List<Any>>()
            } catch (e: Exception) {
                // Si falla recarga, pero hay cache local reciente, úsala
                if (porRevisarCache != null) return porRevisarCache!!
                throw e
            }
        }else{
            // Sin red, usar cache vieja si existe
            if (porRevisarCache != null) return porRevisarCache!!
            // Si no hay nada, regresa vacío
            return mutableListOf<List<Any>>()
        }
    }
    fun _addPorRevisarCache(row: List<String>){
        // street, number, time, parkingSlotKey, validation, lat, lon
        porRevisarCache?.add(row)
        savePorRevisarToSheetWithRetry(row)
        mySettings?.saveList("PORREVISAR_CACHE", porRevisarCache as List<List<String>>)
        mySettings?.saveLong("PORREVISAR_CACHE_TIMESTAMP", System.currentTimeMillis())
    }

    //Lugares de VISITAS
    fun getParkingSlots( isNetworkAvailable: Boolean): List<List<Any>>{
        val now = System.currentTimeMillis()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = parkingSlotsCache != null && now - parkingSlotsCacheTimestamp <= CACHE_DURATION_MS
        if (isMemoryFresh) return parkingSlotsCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("PARKINGSLOTS_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("PARKINGSLOTS_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && now - cacheTimestamp <= CACHE_DURATION_MS
        if (isDiskFresh) {
            parkingSlotsCache = cacheList //as MutableList<List<Any>>
            parkingSlotsCacheTimestamp = cacheTimestamp
            return parkingSlotsCache!!
        }
        // 3. Si hace falta actualizar y hay Internet
        if (isNetworkAvailable) {
            try {
                //PARKING SLOTS
                GlobalScope.launch(Dispatchers.IO) {
                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                    val response = sheetsService.spreadsheets().values()
                        .get(yourEventsSpreadSheetID, "ParkingSlots!A:C") // Latitude, Longitude, Key
                        .execute()
                    val allRows = response.getValues() ?: emptyList()
                    withContext(Dispatchers.Main) {
                        // Cachear y timestamp
                        mySettings?.saveList("PARKINGSLOTS_CACHE", allRows as List<List<String>>)
                        mySettings?.saveLong("PARKINGSLOTS_CACHE_TIMESTAMP", now)
                        parkingSlotsCache = allRows
                        parkingSlotsCacheTimestamp = now

                    }
                }

                return parkingSlotsCache ?: emptyList()
            } catch (e: Exception) {
                // Si falla recarga, pero hay cache local reciente, úsala
                if (parkingSlotsCache != null) return parkingSlotsCache!!
                throw e
            }
        }else{
            // Sin red, usar cache vieja si existe
            if (parkingSlotsCache != null) return parkingSlotsCache!!
            // Si no hay nada, regresa vacío
            return emptyList()
        }

//        if (parkingSlotsCache == null) { //Si no hay cache buscar en los settings
//            // 1. Si RAM no, revisa persisted cache (MySettings)
//            val cacheList = mySettings?.getList("PARKINGSLOTS_CACHE")!!.toMutableList()
//            val cacheTimestamp = mySettings?.getLong("PARKINGSLOTS_CACHE_TIMESTAMP", 0L) ?: 0L
//            if (cacheList?.isEmpty() == false)
//                parkingSlotsCache = cacheList
//
//            // 2. Sin mySettings no, descargar de red
//            if (parkingSlotsCache == null) {
//                val yourEventsSpreadSheetID =  mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
//                val response = sheetsService.spreadsheets().values()
//                    .get(
//                        yourEventsSpreadSheetID,
//                        "ParkingSlots!A:C"
//                    ) // Latitude, Longitude, Key
//                    .execute()
//                val allRows = response.getValues() ?: emptyList()
//                mySettings?.saveList("PARKINGSLOTS_CACHE", allRows as List<List<String>>)
//                parkingSlotsCache = allRows
//            }
//        }
//        return parkingSlotsCache ?: emptyList()
    }

    //AutoEventos
    fun getAutosEventos( isNetworkAvailable: Boolean): List<List<Any>>{
        val now = System.currentTimeMillis()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = autosEventosCache != null && now - autosEventosCacheTimestamp <= CACHE_DURATION_MS
        if (isMemoryFresh) return autosEventosCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("EVENTOS_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("EVENTOS_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && now - cacheTimestamp <= CACHE_DURATION_MS

        if (isDiskFresh) {
            autosEventosCache = cacheList as MutableList<List<Any>>
            autosEventosCacheTimestamp = cacheTimestamp
            return autosEventosCache!!
        }

        // 3. Si hace falta actualizar y hay Internet
        if (isNetworkAvailable) {

            try {
                //AUTOS EVENTOS
                val allRows = mutableListOf<List<Any>>()
                val date15daysAgo = LocalDate.now().minusDays(15)
                GlobalScope.launch(Dispatchers.IO) {

                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                    val response = sheetsService.spreadsheets().values()
                        .get(yourEventsSpreadSheetID, "AutosEventos!A:E") // placa, date, time, localPhotoPath, ParkingSlotKey
                        .execute()
                    val rows = response.getValues().drop(1) ?: emptyList()
                    withContext(Dispatchers.Main) {
                        // Update Data
                        val solo15dias= rows.filter { it.size >= 5 && it[1].toString().length==10 && LocalDate.parse(it[1].toString()) >= date15daysAgo }.reversed()
                        allRows.addAll(solo15dias)
                        // Cachear y timestamp
                        mySettings?.saveList("EVENTOS_CACHE", allRows as List<List<String>>)
                        mySettings?.saveLong("EVENTOS_CACHE_TIMESTAMP", now)
                        autosEventosCache = allRows
                        autosEventosCacheTimestamp = now

                    }
                }

                return autosEventosCache ?: emptyList()
            } catch (e: Exception) {
                // Si falla recarga, pero hay cache local reciente, úsala
                if (autosEventosCache != null) return autosEventosCache!!
                throw e
            }
        } else {
            // Sin red, usar cache vieja si existe
            if (autosEventosCache != null) return autosEventosCache!!
            // Si no hay nada, regresa vacío
            return emptyList()
        }
    }

    fun getAutosEventos_6horas( isNetworkAvailable: Boolean): List<List<Any>>{
        val rows = getAutosEventos(isNetworkAvailable)
        val date6HoursAgo = LocalDateTime.now().minusHours(6)
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val plateEvents = rows.filter { LocalDateTime.parse(it[2].toString(),timeFormatter) >= date6HoursAgo }
            .reversed()
        return plateEvents ?: emptyList()
    }

    fun _addAutosEventCache(row: List<String>){
        autosEventosCache?.add(row)
        saveAutosEventosToSheetWithRetry(row)
        mySettings?.saveList("EVENTOS_CACHE", autosEventosCache as List<List<String>>)
        mySettings?.saveLong("EVENTOS_CACHE_TIMESTAMP", System.currentTimeMillis())
    }

}