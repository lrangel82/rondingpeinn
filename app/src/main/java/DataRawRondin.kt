import MySettings
import android.content.Context
import kotlinx.coroutines.*
import android.content.Context.CONNECTIVITY_SERVICE
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.larangel.rondingpeinn.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DataRawRondin(context: Context) {
    private var mySettings: MySettings? = null

    // Variables de cache y tiempo
    private var platesCache: List<List<Any>>? = null
    private var parkingSlotsCache: List<List<Any>>? = null
    private var autosEventosCache: MutableList<List<Any>>? = null
    private var platesCacheTimestamp: Long = 0
    private var autosEventosCacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 60 * 60 * 1000 // 1 hora

    init {
        mySettings = MySettings(context)
    }

    // Función nueva: cargar y actualizar datos si es necesario
    fun getCachedVehiclesData(sheetsService: Sheets, isNetworkAvailable: Boolean): List<List<Any>> {
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
                    val rows = response.getValues() ?: emptyList()
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

    //Lugares de VISITAS
    fun getParkingSlots(sheetsService: Sheets): List<List<Any>>{
        if (parkingSlotsCache == null) { //Si no hay cache buscar en los settings
            // 1. Si RAM no, revisa persisted cache (MySettings)
            val cacheList = mySettings?.getList("PARKINGSLOTS_CACHE")!!.toMutableList()
            if (cacheList?.isEmpty() == false)
                parkingSlotsCache = cacheList

            // 2. Sin mySettings no, descargar de red
            if (parkingSlotsCache == null) {
                val yourEventsSpreadSheetID =  mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                val response = sheetsService.spreadsheets().values()
                    .get(
                        yourEventsSpreadSheetID,
                        "ParkingSlots!A:C"
                    ) // Latitude, Longitude, Key
                    .execute()
                val allRows = response.getValues() ?: emptyList()
                mySettings?.saveList("PARKINGSLOTS_CACHE", allRows as List<List<String>>)
                parkingSlotsCache = allRows
            }
        }
        return parkingSlotsCache ?: emptyList()
    }

    //AutoEventos
    fun getAutosEventos(sheetsService: Sheets, isNetworkAvailable: Boolean): List<List<Any>>{
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
                    val rows = response.getValues() ?: emptyList()
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

    fun getAutosEventos_6horas(sheetsService: Sheets, isNetworkAvailable: Boolean): List<List<Any>>{
        val rows = getAutosEventos(sheetsService,isNetworkAvailable)
        val date6HoursAgo = LocalDateTime.now().minusHours(6)
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val plateEvents = rows.filter { LocalDateTime.parse(it[2].toString(),timeFormatter) >= date6HoursAgo }
            .reversed()
        return plateEvents ?: emptyList()
    }

    fun _addEventCache(row: List<String>){
        autosEventosCache?.add(row)
        mySettings?.saveList("EVENTOS_CACHE", autosEventosCache as List<List<String>>)
        mySettings?.saveLong("EVENTOS_CACHE_TIMESTAMP", System.currentTimeMillis())
    }

}