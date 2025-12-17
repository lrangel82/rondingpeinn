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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.minutes

class DataRawRondin(context: Context, coroutineScopeObject: CoroutineScope ) {
    private var mySettings: MySettings? = null
    private var applicationContext: Context? = null
    private var coroutineScope: CoroutineScope? = null
    private lateinit var resultText: TextView

    private lateinit var sheetsService: Sheets

    // Variables de cache y tiempo
    private var platesCache: List<List<Any>>? = null
    private var parkingSlotsCache: List<List<Any>>? = null
    private var autosEventosCache: MutableList<List<Any>>? = null
    private var incidenciaEventosCache: MutableList<List<Any>>? = null
    private var directionsCache: List<List<Any>>? = null
    private var porRevisarCache: MutableList<List<Any>>? = null
    private var multasCache: MutableList<List<Any>>? = null
    private var platesCacheTimestamp: Long = 0
    private var parkingSlotsCacheTimestamp: Long = 0
    private var autosEventosCacheTimestamp: Long = 0
    private var incidenciaEventosCacheTimestamp: Long = 0
    private var directionsCacheTimestamp: Long = 0
    private var porRevisarCacheTimestamp: Long = 0
    private var multasCacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 60 * 60 * 1000 // 1 hora

    //Sheets name index
    private var porRevisarSheetId: Int = 0

    //Variables temporales para salvar
    private var forSave_autosEventos: MutableList<List<Any>>? = mutableListOf<List<Any>>()
    private var isRunningSave_autosEventos: Boolean = false
    private var forSave_incidenciaEventos: MutableList<List<Any>>? = mutableListOf<List<Any>>()
    private var isRunningSave_incidenciaEventos: Boolean = false
    private var forSave_porRevisar: MutableList<List<Any>>? = mutableListOf<List<Any>>()
    private var forDelete_porRevisarIndex: ArrayList<String>? = ArrayList<String>()
    private var isRunningSave_porRevisar: Boolean = false
    private var isRunningDelete_porRevisar: Boolean = false

    //Multas
    private var forSave_MultaGenerada: MutableList<List<Any>>? = mutableListOf<List<Any>>()
    private var forUpdate_MultaGenerada: MutableList<List<Any>>? = mutableListOf<List<Any>>()
    private var forUpdate_MultaGeneradaIndex: ArrayList<String>? = ArrayList<String>()
    private var isRunningSave_MultaGenerada: Boolean = false
    private var isRunningUpdate_MultaGenerada: Boolean = false

    //Domicilio Warnings
    private var domicilioWarningsCache: MutableList<List<Any>>? = null
    private var domicilioWarningsCacheTimestamp: Long = 0
    private var forSave_DomicilioWarnings: MutableList<List<Any>>? = mutableListOf<List<Any>>()
    private var forUpdate_DomicilioWarnings: MutableList<List<Any>>? = mutableListOf<List<Any>>()
    private var forUpdate_DomicilioWarningsIndex: ArrayList<String>? = ArrayList<String>()
    private var isRunningSave_DomicilioWarnings: Boolean = false
    private var isRunningUpdate_DomicilioWarnings: Boolean = false

    init {
        applicationContext = context
        mySettings = MySettings(context)
        coroutineScope = coroutineScopeObject

        initializeGoogleServices()
        //Pendientes por guardar?
        checarPendientePorSalvarEnCACHE()
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

        } catch (e: Exception) {
            val customMessage = "Error al inicializar sheet ID de PARKING: ${e.message}"
            throw Exception(customMessage, e) // e is the original cause
        }

    }

    //Pendientes por guardar en cache? ejecuta el ciclo
    private fun checarPendientePorSalvarEnCACHE(){
        //forSave_autosEventos
        val cacheList1 = mySettings?.getList("CACHE_forSave_autosEventos")!!.toMutableList()
        if (forSave_autosEventos?.isEmpty() == true && cacheList1.isNotEmpty()){
            forSave_autosEventos= cacheList1 as MutableList<List<Any>>?
            saveAutosEventosToSheetWithRetry(listOf())
        }

        //forSave_porRevisar
        val cacheList2 = mySettings?.getList("CACHE_forSave_porRevisar")!!.toMutableList()
        if (forSave_porRevisar?.isEmpty() == true && cacheList2.isNotEmpty()){
            forSave_porRevisar= cacheList2 as MutableList<List<Any>>?
            savePorRevisarToSheetWithRetry(listOf())
        }

        //forDelete_porRevisarIndex
        val cacheList3 = mySettings?.getSimpleList("CACHE_forDelete_porRevisarIndex")!!.toMutableList()
        if (forDelete_porRevisarIndex?.isEmpty() == true && cacheList3.isNotEmpty()){
            forDelete_porRevisarIndex= cacheList3 as ArrayList<String>
            deletePorRevisarSheetWithRetry(-1)
        }

        //forSave_MultaGenerada
        val cacheList4 = mySettings?.getList("CACHE_forSave_MultaGenerada")!!.toMutableList()
        if (forSave_MultaGenerada?.isEmpty() == true && cacheList4.isNotEmpty()){
            forSave_MultaGenerada= cacheList4 as MutableList<List<Any>>?
            saveMultaGeneradaToSheetWithRetry(listOf())
        }

        //forUpdate_MultaGenerada    y    forUpdate_MultaGeneradaIndex
        val cacheList5 = mySettings?.getList("CACHE_forUpdate_MultaGenerada")!!.toMutableList()
        val cacheList5_1 = mySettings?.getSimpleList("CACHE_forUpdate_MultaGeneradaIndex")!!.toMutableList()
        if (forUpdate_MultaGenerada?.isEmpty() == true && cacheList5.isNotEmpty() && cacheList5_1.isNotEmpty()){
            forUpdate_MultaGenerada= cacheList5 as MutableList<List<Any>>?
            forUpdate_MultaGeneradaIndex = cacheList5_1 as ArrayList<String>
            updateMultaGeneradaToSheetWithRetry(listOf(),-1)
        }

        //forSave_DomicilioWarnings
        val cacheList6 = mySettings?.getList("CACHE_forSave_DomicilioWarnings")!!.toMutableList()
        if (forSave_DomicilioWarnings?.isEmpty() == true && cacheList6.isNotEmpty()){
            forSave_DomicilioWarnings= cacheList6 as MutableList<List<Any>>?
            saveDomicilioWarningToSheetWithReatry(listOf())
        }

        //forUpdate_DomicilioWarnings   y  forUpdate_DomicilioWarningsIndex
        val cacheList7 = mySettings?.getList("CACHE_forUpdate_DomicilioWarnings")!!.toMutableList()
        val cacheList7_1 = mySettings?.getSimpleList("CACHE_forUpdate_DomicilioWarningsIndex")!!.toMutableList()
        if (forUpdate_DomicilioWarnings?.isEmpty() == true && cacheList7.isNotEmpty() && cacheList7_1.isNotEmpty()){
            forUpdate_DomicilioWarnings= cacheList7 as MutableList<List<Any>>?
            forUpdate_DomicilioWarningsIndex = cacheList7_1 as ArrayList<String>
            updateDomicilioWarningToSheetWithReatry(listOf(),-1)
        }

        //forSave_incidenciaEventos
        val cacheList8 = mySettings?.getList("CACHE_forSave_incidenciaEventos")!!.toMutableList()
        if (forSave_incidenciaEventos?.isEmpty() == true && cacheList8.isNotEmpty()){
            forSave_incidenciaEventos= cacheList8 as MutableList<List<Any>>?
            saveIncidenciaEventosToSheetWithRetry(listOf())
        }
    }


    // Utilidad simple para detectar red
    fun isNetworkAvailable(): Boolean {
//        if (System.currentTimeMillis() > 1764992520000)
//            return false
        val connectivityManager = applicationContext?.getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            // Para versiones de Android anteriores a Marshmallow (API 23)
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    // Metodo externo para guardar datos en Google Sheets (implementa lo necesario aquí)
    private suspend fun saveToGoogleSheets(range:String,data: List<List<String>>): Boolean {
        if (isNetworkAvailable() == false ) return false
        if (data.isEmpty() == true) return true //Nada que salvar

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
    private fun updateToGoogleSheets(range:String,data: List<List<String>>): Boolean {
        if (isNetworkAvailable() == false ) return false
        if (data.isEmpty() == true) return true //Nada que salvar

        val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
        val bodyEventos = ValueRange().setValues(data)
        try {
            // Save to PARKING_SPREADSHEET_ID
            sheetsService.spreadsheets().values()
                .update(yourEventsSpreadSheetID, range, bodyEventos)
                .setValueInputOption("RAW")
                .execute()
            return true
        } catch (e: Exception) {
            throw e
            return false
        }
    }
    private fun deleteToGoogleSheets(sheetName:String,indexToDelete: Int): Boolean{
        if (indexToDelete>0) {
            try {
                val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                if (porRevisarSheetId == 0) {
                    val spreadsheet = sheetsService.spreadsheets().get(yourEventsSpreadSheetID).execute()
                    for (sheet in spreadsheet.sheets) {
                        if (sheetName == sheet.properties.title) {
                            porRevisarSheetId = sheet.properties.sheetId
                            break
                        }
                    }
                }

                //Crear el batch para eliminar
                val dimensionRange = DimensionRange()
                    .setSheetId(porRevisarSheetId)
                    .setDimension("ROWS")
                    .setStartIndex(indexToDelete - 1)
                    .setEndIndex(indexToDelete)
                val deleteRequest = DeleteDimensionRequest().setRange(dimensionRange)
                val request = Request().setDeleteDimension(deleteRequest)
                val batchRequest = BatchUpdateSpreadsheetRequest().setRequests(listOf(request))
                sheetsService.spreadsheets().batchUpdate(yourEventsSpreadSheetID, batchRequest)
                    .execute()
                return true
            } catch (e: Exception) {
                throw e
                return false
            }
        }
        return false
    }
    fun saveAutosEventosToSheetWithRetry(dataRow:List<String>): Boolean{
        if (dataRow.isNotEmpty()) {
            forSave_autosEventos?.add(dataRow)
            mySettings?.saveList("CACHE_forSave_autosEventos", forSave_autosEventos as List<List<String>>)
        }
        if (isRunningSave_autosEventos) return false //Solo debe correr uno a la ves
        coroutineScope?.launch {
            isRunningSave_autosEventos=true
            while (isActive) {
                try {
                    val success = saveToGoogleSheets("AutosEventos!A:E",forSave_autosEventos as List<List<String>>)
                    if (success){ // Éxito, no necesita reintentar
                        forSave_autosEventos!!.clear()
                        mySettings?.saveList("CACHE_forSave_autosEventos", forSave_autosEventos as List<List<String>>)
                        isRunningSave_autosEventos=false
                        break
                    }
                    mySettings?.saveList("CACHE_forSave_autosEventos", forSave_autosEventos as List<List<String>>)
                } catch (e: Exception) {
                    mySettings?.saveList("CACHE_forSave_autosEventos", forSave_autosEventos as List<List<String>>)
                    val customMessage = "Error al salvar registros a EVENTOS AUTOS sheet REINTENTAR en 5 Min: ${e.message}"
                    throw Exception(customMessage, e) // e is the original cause
                }
                delay(5.minutes)
                // Reintenta
            }
        }
        return true
    }
    fun saveIncidenciaEventosToSheetWithRetry(dataRow: List<String>): Boolean{
        if (dataRow.isNotEmpty()) {
            forSave_incidenciaEventos?.add(dataRow)
            mySettings?.saveList("CACHE_forSave_incidenciaEventos", forSave_incidenciaEventos as List<List<String>>)
        }
        if (isRunningSave_incidenciaEventos) return false //Solo debe correr uno a la ves
        coroutineScope?.launch {
            isRunningSave_incidenciaEventos=true
            while (isActive) {
                try {
                    val success = saveToGoogleSheets("IncidenciaEventos!A:G",forSave_incidenciaEventos as List<List<String>>)
                    if (success){ // Éxito, no necesita reintentar
                        forSave_incidenciaEventos!!.clear()
                        mySettings?.saveList("CACHE_forSave_incidenciaEventos", forSave_incidenciaEventos as List<List<String>>)
                        isRunningSave_incidenciaEventos=false
                        break
                    }
                    mySettings?.saveList("CACHE_forSave_incidenciaEventos", forSave_incidenciaEventos as List<List<String>>)
                } catch (e: Exception) {
                    mySettings?.saveList("CACHE_forSave_incidenciaEventos", forSave_incidenciaEventos as List<List<String>>)
                    val customMessage = "Error al salvar registros a INCIDENCIA EVENTOS sheet REINTENTAR en 5 Min: ${e.message}"
                    throw Exception(customMessage, e) // e is the original cause
                }
                delay(5.minutes)
                // Reintenta
            }
        }
        return true
    }
    fun savePorRevisarToSheetWithRetry(dataRow:List<String>): Boolean{
        if (dataRow.isNotEmpty()) {
            forSave_porRevisar?.add(dataRow)
            mySettings?.saveList("CACHE_forSave_porRevisar", forSave_porRevisar as List<List<String>>)
        }
        if (isRunningSave_porRevisar) return false //Solo debe correr uno a la ves
        coroutineScope?.launch {
            isRunningSave_porRevisar=true
            while (isActive) {
                try {
                    // street, number, time, parkingSlotKey, validation, lat, lon
                    val success = saveToGoogleSheets("PorRevisar!A:G",forSave_porRevisar as List<List<String>>)
                    if (success){ // Éxito, no necesita reintentar
                        forSave_porRevisar!!.clear()
                        mySettings?.saveList("CACHE_forSave_porRevisar", forSave_porRevisar as List<List<String>>)
                        isRunningSave_porRevisar=false
                        break
                    }
                    mySettings?.saveList("CACHE_forSave_porRevisar", forSave_porRevisar as List<List<String>>)
                } catch (e: Exception) {
                    mySettings?.saveList("CACHE_forSave_porRevisar", forSave_porRevisar as List<List<String>>)
                    val customMessage = "Error al salvar registros a EVENTOS AUTOS sheet REINTENTAR en 5 Min: ${e.message}"
                    throw Exception(customMessage, e) // e is the original cause
                }
                delay(5.minutes)
                // Reintenta
            }
        }
        return true
    }
    fun deletePorRevisarSheetWithRetry(indexToDelete: Int) : Boolean {
        if (indexToDelete>=0) {
            forDelete_porRevisarIndex?.add(indexToDelete.toString())
            mySettings?.saveSingleList("CACHE_forDelete_porRevisarIndex", forDelete_porRevisarIndex as List<String>)
        }
        if (isRunningDelete_porRevisar) return false //Solo debe correr uno a la ves
        coroutineScope?.launch {
            isRunningDelete_porRevisar=true
            while (isActive) {
                try {
                    // Fecha, calle, numero, placa, parkingSlot - (Fecha)
                    forDelete_porRevisarIndex?.forEachIndexed { i, whatIndex ->
                        if ( deleteToGoogleSheets("PorRevisar",whatIndex.toInt())){
                            //Salvado remover del listado
                            forDelete_porRevisarIndex?.removeAt(i)
                        }else{
                            return@forEachIndexed
                        }
                    }
                    mySettings?.saveSingleList("CACHE_forDelete_porRevisarIndex", forDelete_porRevisarIndex as List<String>)
                    if (forDelete_porRevisarIndex?.size == 0) {
                        isRunningDelete_porRevisar = false
                        break
                    }
                } catch (e: Exception) {
                    mySettings?.saveSingleList("CACHE_forDelete_porRevisarIndex", forDelete_porRevisarIndex as List<String>)
                    val customMessage = "Error al hacer ELIMINAR a PorRevisar sheet REINTENTAR en 5 Min: ${e.message}"
                    //throw Exception(customMessage, e) // e is the original cause
                    println(customMessage)
                }
                delay(5.minutes)
                // Reintenta
            }
        }
        return true
    }
    fun saveMultaGeneradaToSheetWithRetry(dataRow:List<String>): Boolean{
        if (dataRow.isNotEmpty()) {
            forSave_MultaGenerada?.add(dataRow)
            mySettings?.saveList("CACHE_forSave_MultaGenerada", forSave_MultaGenerada as List<List<String>>)
        }
        if (isRunningSave_MultaGenerada) return false //Solo debe correr uno a la ves
        coroutineScope?.launch {
            isRunningSave_MultaGenerada=true
            while (isActive) {
                try {
                    // Fecha, calle, numero, placa, parkingSlot - (Fecha)
                    val success = saveToGoogleSheets("MultasGeneradas!A:E",forSave_MultaGenerada as List<List<String>>)
                    if (success){ // Éxito, no necesita reintentar
                        forSave_MultaGenerada!!.clear()
                        mySettings?.saveList("CACHE_forSave_MultaGenerada", forSave_MultaGenerada as List<List<String>>)
                        isRunningSave_MultaGenerada=false
                        break
                    }
                } catch (e: Exception) {
                    mySettings?.saveList("CACHE_forSave_MultaGenerada", forSave_MultaGenerada as List<List<String>>)
                    val customMessage = "Error al salvar registros a EVENTOS AUTOS sheet REINTENTAR en 5 Min: ${e.message}"
                    throw Exception(customMessage, e) // e is the original cause
                }
                delay(5.minutes)
                // Reintenta
            }
        }
        return true
    }
    fun updateMultaGeneradaToSheetWithRetry(dataRow:List<String>,indexToUpdate: Int) : Boolean{
        if (dataRow.isNotEmpty()) {
            forUpdate_MultaGenerada?.add(dataRow)
            forUpdate_MultaGeneradaIndex?.add(indexToUpdate.toString())
            mySettings?.saveList("CACHE_forUpdate_MultaGenerada", forUpdate_MultaGenerada as List<List<String>>)
            mySettings?.saveSingleList("CACHE_forUpdate_MultaGeneradaIndex", forUpdate_MultaGeneradaIndex as List<String>)
        }
        if (isRunningUpdate_MultaGenerada) return false //Solo debe correr uno a la ves
        coroutineScope?.launch {
            isRunningUpdate_MultaGenerada=true
            while (isActive) {
                try {
                    // Fecha, calle, numero, placa, parkingSlot - (Fecha)
                    forUpdate_MultaGenerada?.forEachIndexed { i, multa ->
                        val lMulta = listOf(multa)
                        val whatIndex = forUpdate_DomicilioWarningsIndex?.get(i)
                        if ( updateToGoogleSheets("MultasGeneradas!A$whatIndex:E$whatIndex",
                                lMulta as List<List<String>>
                            )){
                            //Salvado remover del listado
                            forUpdate_MultaGenerada!!.removeAt(i)
                            forUpdate_MultaGeneradaIndex?.removeAt(i)
                        }else{
                            return@forEachIndexed
                        }
                    }
                    mySettings?.saveList("CACHE_forUpdate_MultaGenerada", forUpdate_MultaGenerada as List<List<String>>)
                    mySettings?.saveSingleList("CACHE_forUpdate_MultaGeneradaIndex", forUpdate_MultaGeneradaIndex as List<String>)
                    if (forUpdate_MultaGenerada?.size == 0) {
                        isRunningUpdate_MultaGenerada = false
                        break
                    }
                } catch (e: Exception) {
                    mySettings?.saveList("CACHE_forUpdate_MultaGenerada", forUpdate_MultaGenerada as List<List<String>>)
                    mySettings?.saveSingleList("CACHE_forUpdate_MultaGeneradaIndex", forUpdate_MultaGeneradaIndex as List<String>)
                    val customMessage = "Error al salvar registros a EVENTOS AUTOS sheet REINTENTAR en 5 Min: ${e.message}"
                    throw Exception(customMessage, e) // e is the original cause
                }
                delay(5.minutes)
                // Reintenta
            }
        }
        return true
    }
    fun saveDomicilioWarningToSheetWithReatry(dataRow:List<String>): Boolean{
        if (dataRow.isNotEmpty()) {
            forSave_DomicilioWarnings?.add(dataRow)
            mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forSave_DomicilioWarnings as List<List<String>>)
        }
        if (isRunningSave_DomicilioWarnings) return false //Solo debe correr uno a la ves
        coroutineScope?.launch {
            isRunningSave_DomicilioWarnings=true
            while (isActive) {
                try {
                    // Fecha, calle, numero, placa, parkingSlot - (Fecha)
                    val success = saveToGoogleSheets("DomicilioWarnings!A:C",forSave_DomicilioWarnings as List<List<String>>)
                    if (success){ // Éxito, no necesita reintentar
                        forSave_DomicilioWarnings!!.clear()
                        mySettings?.saveList("CACHE_forSave_DomicilioWarnings", listOf(listOf()) )
                        isRunningSave_DomicilioWarnings=false
                        break
                    }
                    mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forSave_DomicilioWarnings as List<List<String>>)
                } catch (e: Exception) {
                    mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forSave_DomicilioWarnings as List<List<String>>)
                    val customMessage = "Error al salvar registros a DOMICILIO WARNINGS sheet REINTENTAR en 5 Min: ${e.message}"
                    throw Exception(customMessage, e) // e is the original cause
                }
                delay(5.minutes)
                // Reintenta
            }
        }
        return true
    }
    fun updateDomicilioWarningToSheetWithReatry(dataRow:List<String>,indexToUpdate: Int) : Boolean{
        if (dataRow.isNotEmpty() && indexToUpdate >= 0) {
            forUpdate_DomicilioWarnings?.add(dataRow)
            forUpdate_DomicilioWarningsIndex?.add(indexToUpdate.toString())
            mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forUpdate_DomicilioWarnings as List<List<String>>)
            mySettings?.saveSingleList("CACHE_forUpdate_DomicilioWarningsIndex", forUpdate_DomicilioWarningsIndex as List<String>)
        }
        if (isRunningUpdate_DomicilioWarnings) return false //Solo debe correr uno a la ves
        coroutineScope?.launch {
            isRunningUpdate_DomicilioWarnings=true
            while (isActive) {
                try {
                    // Fecha, calle, numero, placa, parkingSlot - (Fecha)
                    forUpdate_DomicilioWarnings?.forEachIndexed { i, multa ->
                        val lMulta = listOf(multa)
                        val whatIndex = forUpdate_DomicilioWarningsIndex?.get(i)
                        if ( updateToGoogleSheets("DomicilioWarnings!A$whatIndex:D$whatIndex",
                                lMulta as List<List<String>>
                            )){
                            //Salvado remover del listado
                            forUpdate_DomicilioWarnings!!.removeAt(i)
                            forUpdate_DomicilioWarningsIndex?.removeAt(i)
                        }else{
                            return@forEachIndexed
                        }
                    }
                    mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forUpdate_DomicilioWarnings as List<List<String>>)
                    mySettings?.saveSingleList("CACHE_forUpdate_DomicilioWarningsIndex", forUpdate_DomicilioWarningsIndex as List<String>)
                    if (forUpdate_DomicilioWarnings?.size == 0) {
                        isRunningUpdate_DomicilioWarnings = false
                        break
                    }
                } catch (e: Exception) {
                    mySettings?.saveList("CACHE_forSave_DomicilioWarnings", forUpdate_DomicilioWarnings as List<List<String>>)
                    mySettings?.saveSingleList("CACHE_forUpdate_DomicilioWarningsIndex", forUpdate_DomicilioWarningsIndex as List<String>)
                    val customMessage = "Error al hacer UPDATE a DomicilioWarnings sheet REINTENTAR en 5 Min: ${e.message}"
                    throw Exception(customMessage, e) // e is the original cause
                }
                delay(5.minutes)
                // Reintenta
            }
        }
        return true
    }

    // Función nueva: cargar y actualizar datos si es necesario
    fun getCachedVehiclesData( ): List<List<Any>> {
        val now = System.currentTimeMillis()
        val thereConection = isNetworkAvailable()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = platesCache != null && (now - platesCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isMemoryFresh) return platesCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("VEHICLE_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("VEHICLE_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)

        if (isDiskFresh) {
            platesCache = cacheList
            platesCacheTimestamp = cacheTimestamp
            return platesCache!!
        }

        // 3. Si hace falta actualizar y hay Internet
        if (thereConection) {
            try {
                //Vehiculos VISITANTES
                val yourSpreadSheetID = mySettings?.getString("REGISTRO_CARROS_SPREADSHEET_ID", "")
                val sheets = listOf("ingreso", "salida")
                val allRows = mutableListOf<List<Any>>()
                for (sheet in sheets) {
                    val response = sheetsService.spreadsheets().values()
                        .get(yourSpreadSheetID, "$sheet!C:E") //placa calle numero tipo conductor
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
                if (cacheList.isNotEmpty()) return cacheList
                //throw e
            }
        } else {
            // Sin red, usar cache vieja si existe
            if (platesCache != null) return platesCache!!
            if (cacheList.isNotEmpty()) return cacheList
            // Si no hay nada, regresa vacío
            return emptyList()
        }
        return emptyList()
    }


    //Direcciones de casas y ubicacion
    fun getDomiciliosUbicacion( ): List<List<Any>> {
        val now = System.currentTimeMillis()
        val thereConection = isNetworkAvailable()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = directionsCache != null && (now - directionsCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isMemoryFresh) return directionsCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("DIRECTIONS_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("DIRECTIONS_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isDiskFresh) {
            directionsCache = cacheList //as MutableList<List<Any>>
            directionsCacheTimestamp = cacheTimestamp
            return directionsCache!!
        }
        // 3. Si hace falta actualizar y hay Internet
        if (thereConection) {
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
                if (cacheList.isNotEmpty()) return cacheList
                return emptyList()
            }
        }else{
            // Sin red, usar cache vieja si existe
            if (directionsCache != null) return directionsCache!!
            if (cacheList.isNotEmpty()) return cacheList
            // Si no hay nada, regresa vacío
            return emptyList()
        }
    }

    //Por Revisar registros
    fun getPorRevisar( force: Boolean = false): MutableList<List<Any>>? {
        val now = System.currentTimeMillis()
        val thereConection = isNetworkAvailable()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = porRevisarCache != null && (now - porRevisarCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isMemoryFresh and !force) return porRevisarCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("PORREVISAR_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("PORREVISAR_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isDiskFresh and !force) {
            porRevisarCache = cacheList as MutableList<List<Any>>
            porRevisarCacheTimestamp = cacheTimestamp
            return porRevisarCache!!
        }
        // 3. Si hace falta actualizar y hay Internet
        if (thereConection) {
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
                if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
                return mutableListOf<List<Any>>()
            }
        }else{
            // Sin red, usar cache vieja si existe
            if (porRevisarCache != null) return porRevisarCache!!
            if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
            // Si no hay nada, regresa vacío
            return mutableListOf<List<Any>>()
        }
    }
    fun getPorRevisar_20horas(): MutableList<List<Any>>?{
        val rows = getPorRevisar()
        val date20HoursAgo = LocalDateTime.now().minusHours(20)
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val porRevisar = rows?.filter { LocalDateTime.parse(it[2].toString(),timeFormatter) >= date20HoursAgo }
            ?.reversed()
        if (porRevisar!=null && porRevisar.isNotEmpty())
            return porRevisar as MutableList<List<Any>>?
        return mutableListOf<List<Any>>()
    }
    fun _addPorRevisarCache(row: List<String>): Boolean{
        // street, number, time, parkingSlotKey, validation, lat, lon
        if (porRevisarCache == null) getPorRevisar()
        porRevisarCache?.add(row)
        mySettings?.saveList("PORREVISAR_CACHE", porRevisarCache as List<List<String>>)
        mySettings?.saveLong("PORREVISAR_CACHE_TIMESTAMP", System.currentTimeMillis())
        return savePorRevisarToSheetWithRetry(row)
    }
    fun eliminarPorRevisar(calle: String, numero: String, slotKey: String): Boolean{
        var indexFind = -1
        porRevisarCache?.forEachIndexed { index, row ->
            if (row.size >= 2 && row[0].toString() == calle && row[1].toString() == numero && row[3].toString() == slotKey) {
                indexFind = index
                return@forEachIndexed
            }
        }
        if (indexFind>=0) {
            //Se encontro, entonces eliminar con retry
            porRevisarCache?.removeAt(indexFind)
            mySettings?.saveList("PORREVISAR_CACHE", porRevisarCache as List<List<String>>)
            mySettings?.saveLong("PORREVISAR_CACHE_TIMESTAMP", System.currentTimeMillis())
            return deletePorRevisarSheetWithRetry(indexFind + 2)
        }
        return true
    }

    //Lugares de VISITAS
    fun getParkingSlots( ): List<List<Any>>{
        val now = System.currentTimeMillis()
        val thereConection = isNetworkAvailable()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = parkingSlotsCache != null && (now - parkingSlotsCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isMemoryFresh) return parkingSlotsCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("PARKINGSLOTS_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("PARKINGSLOTS_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isDiskFresh) {
            parkingSlotsCache = cacheList //as MutableList<List<Any>>
            parkingSlotsCacheTimestamp = cacheTimestamp
            return parkingSlotsCache!!
        }
        // 3. Si hace falta actualizar y hay Internet
        if (thereConection) {
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
                if (cacheList.isNotEmpty()) return cacheList
                return emptyList()
            }
        }else{
            // Sin red, usar cache vieja si existe
            if (parkingSlotsCache != null) return parkingSlotsCache!!
            if (cacheList.isNotEmpty()) return cacheList
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
    fun getAutosEventos( ): List<List<Any>>{
        val now = System.currentTimeMillis()
        val thereConection = isNetworkAvailable()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = autosEventosCache != null && (now - autosEventosCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isMemoryFresh) return autosEventosCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("EVENTOS_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("EVENTOS_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)

        if (isDiskFresh) {
            autosEventosCache = cacheList as MutableList<List<Any>>
            autosEventosCacheTimestamp = cacheTimestamp
            return autosEventosCache!!
        }

        // 3. Si hace falta actualizar y hay Internet
        if (thereConection) {

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
                if (cacheList.isNotEmpty()) return cacheList
                return emptyList()
            }
        } else {
            // Sin red, usar cache vieja si existe
            if (autosEventosCache != null) return autosEventosCache!!
            if (cacheList.isNotEmpty()) return cacheList
            // Si no hay nada, regresa vacío
            return emptyList()
        }
    }
    fun getAutosEventos_6horas( ): List<List<Any>>{
        val rows = getAutosEventos()
        val date6HoursAgo = LocalDateTime.now().minusHours(6)
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val plateEvents = rows.filter { LocalDateTime.parse(it[2].toString(),timeFormatter) >= date6HoursAgo }
            .reversed()
        return plateEvents ?: emptyList()
    }
    fun _addAutosEventCache(row: List<String>): Boolean{
        if (autosEventosCache == null) getAutosEventos()
        autosEventosCache?.add(row)
        mySettings?.saveList("EVENTOS_CACHE", autosEventosCache as List<List<String>>)
        mySettings?.saveLong("EVENTOS_CACHE_TIMESTAMP", System.currentTimeMillis())
        return saveAutosEventosToSheetWithRetry(row)
    }

    //IncidenciasEventos
    fun getIncidenciasEventos(): List<List<Any>>{
        val now = System.currentTimeMillis()
        val thereConection = isNetworkAvailable()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = incidenciaEventosCache != null && (now - incidenciaEventosCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isMemoryFresh) return incidenciaEventosCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("INCIDENCIAEVENTOS_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("INCIDENCIAEVENTOS_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)

        if (isDiskFresh) {
            incidenciaEventosCache = cacheList as MutableList<List<Any>>
            incidenciaEventosCacheTimestamp = cacheTimestamp
            return incidenciaEventosCache!!
        }

        // 3. Si hace falta actualizar y hay Internet
        if (thereConection) {

            try {
                //AUTOS EVENTOS
                val allRows = mutableListOf<List<Any>>()
                val date15daysAgo = LocalDate.now().minusDays(15)
                GlobalScope.launch(Dispatchers.IO) {

                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                    val response = sheetsService.spreadsheets().values()
                        .get(yourEventsSpreadSheetID, "IncidenciaEventos!A:G") // calle, numero, date, time, Tipo, localPhotoPath, descripcion
                        .execute()
                    val rows = response.getValues().drop(1) ?: emptyList()
                    withContext(Dispatchers.Main) {
                        // Update Data
                        val solo15dias= rows.filter { it.size >= 5 && it[2].toString().length==10 && LocalDate.parse(it[2].toString()) >= date15daysAgo }.reversed()
                        allRows.addAll(solo15dias)
                        // Cachear y timestamp
                        mySettings?.saveList("INCIDENCIAEVENTOS_CACHE", allRows as List<List<String>>)
                        mySettings?.saveLong("INCIDENCIAEVENTOS_CACHE_TIMESTAMP", now)
                        incidenciaEventosCache = allRows
                        incidenciaEventosCacheTimestamp = now

                    }
                }

                return incidenciaEventosCache ?: emptyList()
            } catch (e: Exception) {
                // Si falla recarga, pero hay cache local reciente, úsala
                if (incidenciaEventosCache != null) return incidenciaEventosCache!!
                if (cacheList.isNotEmpty()) return cacheList
                return emptyList()
            }
        } else {
            // Sin red, usar cache vieja si existe
            if (incidenciaEventosCache != null) return incidenciaEventosCache!!
            if (cacheList.isNotEmpty()) return cacheList
            // Si no hay nada, regresa vacío
            return emptyList()
        }
    }
    fun getIncidenciasEventosTipo(Tipo: String, fechaDay: LocalDate = LocalDate.now()): List<List<Any>>{
        val rows = getIncidenciasEventos()
        //val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val IncidenciaEvents = rows.filter { LocalDate.parse(it[2].toString()) == fechaDay && it[4].toString().uppercase() == Tipo.uppercase() }
        return IncidenciaEvents ?: emptyList()

    }
    fun addIncidenciaEvento(row: List<String>): Boolean {
        if (incidenciaEventosCache == null) getIncidenciasEventos()
        incidenciaEventosCache?.add(row)
        mySettings?.saveList("INCIDENCIAEVENTOS_CACHE", incidenciaEventosCache as List<List<String>>)
        mySettings?.saveLong("INCIDENCIAEVENTOS_CACHE_TIMESTAMP", System.currentTimeMillis())
        return saveIncidenciaEventosToSheetWithRetry(row)
    }


    //Multas
    fun getMultas():MutableList<List<Any>>?{
        val now = System.currentTimeMillis()
        val thereConection = isNetworkAvailable()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = multasCache != null && (now - multasCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isMemoryFresh) return multasCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("MULTAS_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("MULTAS_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)

        if (isDiskFresh) {
            multasCache = cacheList as MutableList<List<Any>>
            multasCacheTimestamp = cacheTimestamp
            return multasCache!!
        }

        // 3. Si hace falta actualizar y hay Internet
        if (thereConection) {

            try {
                //AUTOS EVENTOS
                val allRows = mutableListOf<List<Any>>()
                val date15daysAgo = LocalDate.now().minusDays(15)
                GlobalScope.launch(Dispatchers.IO) {

                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                    val response = sheetsService.spreadsheets().values()
                        .get(yourEventsSpreadSheetID, "MultasGeneradas!A:E") // Fecha,	Calle,	Numero,	Placa,	PerkingSlot - (Fecha)
                        .execute()
                    val rows = response.getValues().drop(1) ?: emptyList()
                    withContext(Dispatchers.Main) {
                        allRows.addAll(rows)
                        // Cachear y timestamp
                        mySettings?.saveList("MULTAS_CACHE", allRows as List<List<String>>)
                        mySettings?.saveLong("MULTAS_CACHE_TIMESTAMP", now)
                        multasCache = allRows
                        multasCacheTimestamp = now

                    }
                }

                return multasCache ?: mutableListOf<List<Any>>()
            } catch (e: Exception) {
                // Si falla recarga, pero hay cache local reciente, úsala
                if (multasCache != null) return multasCache!!
                if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
                return mutableListOf<List<Any>>()
            }
        } else {
            // Sin red, usar cache vieja si existe
            if (multasCache != null) return multasCache!!
            if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
            // Si no hay nada, regresa vacío
            return mutableListOf<List<Any>>()
        }
    }
    fun addMulta(row: List<String>): Boolean{
        if (multasCache == null) getMultas()
        multasCache?.add(row)
        mySettings?.saveList("MULTAS_CACHE", multasCache as List<List<String>>)
        mySettings?.saveLong("MULTAS_CACHE_TIMESTAMP", System.currentTimeMillis())
        return saveMultaGeneradaToSheetWithRetry(row)
    }
    fun updateMulta(row:List<String>): Boolean{
        var indexFind = -1
        var currentCount = 0
        multasCache?.forEachIndexed { index, multa ->
            if (multa.size >= 2 && multa[0].toString() == row[0].toString() && multa[1].toString() == row[1].toString()) {
                indexFind = index
                currentCount = multa[2].toString().toIntOrNull() ?: 0
                return@forEachIndexed
            }
        }
        val newData = listOf(
            row[0].toString(),
            row[1].toString(),
            currentCount + 1
        )
        if (indexFind>=0){
            multasCache?.set(indexFind,newData)
            mySettings?.saveList("MULTAS_CACHE", multasCache as List<List<String>>)
            mySettings?.saveLong("MULTAS_CACHE_TIMESTAMP", System.currentTimeMillis())
            return updateMultaGeneradaToSheetWithRetry(newData as List<String>,indexFind+2)
        }else{
            //Es nuevo entonces agregarlo
            return addMulta(row)
        }
        return false
    }

    //DomicilioWarnings
    fun getDomicilioWarnings():MutableList<List<Any>>?{
        val now = System.currentTimeMillis()
        val thereConection = isNetworkAvailable()
        // 1. Revisa caché de memoria RAM
        val isMemoryFresh = domicilioWarningsCache != null && (now - domicilioWarningsCacheTimestamp <= CACHE_DURATION_MS || thereConection==false)
        if (isMemoryFresh) return domicilioWarningsCache!!

        // 2. Si RAM no, revisa persisted cache (MySettings)
        val cacheList = mySettings?.getList("DOMICILIOWARNINGS_CACHE")!!.toMutableList()
        val cacheTimestamp = mySettings?.getLong("DOMICILIOWARNINGS_CACHE_TIMESTAMP", 0L) ?: 0L
        val isDiskFresh = cacheList?.isEmpty() == false && (now - cacheTimestamp <= CACHE_DURATION_MS || thereConection==false)

        if (isDiskFresh) {
            domicilioWarningsCache = cacheList as MutableList<List<Any>>
            domicilioWarningsCacheTimestamp = cacheTimestamp
            return domicilioWarningsCache!!
        }

        // 3. Si hace falta actualizar y hay Internet
        if (thereConection) {

            try {
                //AUTOS EVENTOS
                val allRows = mutableListOf<List<Any>>()
                //val date15daysAgo = LocalDate.now().minusDays(15)
                GlobalScope.launch(Dispatchers.IO) {

                    val yourEventsSpreadSheetID = mySettings?.getString("PARKING_SPREADSHEET_ID", "")!!
                    val response = sheetsService.spreadsheets().values()
                        .get(yourEventsSpreadSheetID, "DomicilioWarnings!A:D") // Calle, Numero, ContadorWarning, Tipo
                        .execute()
                    val rows = response.getValues().drop(1) ?: emptyList()
                    withContext(Dispatchers.Main) {
                        allRows.addAll(rows)
                        // Cachear y timestamp
                        mySettings?.saveList("DOMICILIOWARNINGS_CACHE", allRows as List<List<String>>)
                        mySettings?.saveLong("DOMICILIOWARNINGS_CACHE_TIMESTAMP", now)
                        domicilioWarningsCache = allRows
                        domicilioWarningsCacheTimestamp = now

                    }
                }

                return domicilioWarningsCache ?: mutableListOf<List<Any>>()
            } catch (e: Exception) {
                // Si falla recarga, pero hay cache local reciente, úsala
                if (domicilioWarningsCache != null) return domicilioWarningsCache!!
                if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
                return mutableListOf<List<Any>>()
            }
        } else {
            // Sin red, usar cache vieja si existe
            if (domicilioWarningsCache != null) return domicilioWarningsCache!!
            if (cacheList.isNotEmpty()) return cacheList as MutableList<List<Any>>?
            // Si no hay nada, regresa vacío
            return mutableListOf<List<Any>>()
        }
    }
    fun addDomicilioWarning(row: List<String>): Boolean{
        if (domicilioWarningsCache == null) getDomicilioWarnings()
        domicilioWarningsCache?.add(row)
        mySettings?.saveList("DOMICILIOWARNINGS_CACHE", domicilioWarningsCache as List<List<String>>)
        mySettings?.saveLong("DOMICILIOWARNINGS_CACHE_TIMESTAMP", System.currentTimeMillis())
        return saveDomicilioWarningToSheetWithReatry(row)
    }
    fun updateDomicilioWarning(row: List<String>): Int{
        var indexFind = -1
        var currentCount = 0
        if (domicilioWarningsCache == null) getDomicilioWarnings()
        domicilioWarningsCache?.forEachIndexed { index, domWarn ->
            if (domWarn.size >= 2 &&
                domWarn[0].toString() == row[0].toString() &&  //calle
                domWarn[1].toString() == row[1].toString() &&  //numero
                domWarn[3].toString() == row[3].toString()     //tipo
                ) {
                    indexFind = index
                    currentCount = domWarn[2].toString().toIntOrNull() ?: 0
                    return@forEachIndexed
            }
        }
        val newData = listOf(
            row[0].toString(),
            row[1].toString(),
            (currentCount + 1).toString(),
            row[3].toString()
        )
        if (indexFind>=0){
            domicilioWarningsCache?.set(indexFind,newData)
            mySettings?.saveList("DOMICILIOWARNINGS_CACHE", domicilioWarningsCache as List<List<String>>)
            mySettings?.saveLong("DOMICILIOWARNINGS_CACHE_TIMESTAMP", System.currentTimeMillis())
            if (updateDomicilioWarningToSheetWithReatry(newData as List<String>,indexFind+2) == true)
                return currentCount + 1
        }else{
            //Es nuevo entonces agregarlo
            if (addDomicilioWarning(newData as List<String>) == true)
                return currentCount + 1
        }
        return currentCount + 1
    }
}