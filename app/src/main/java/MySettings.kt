import CheckPoint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

////import aws.smithy.kotlin.runtime.auth.awscredentials.AnonymousCredentialsProvider
//import aws.sdk.kotlin.runtime.auth.credentials.
//import aws.sdk.kotlin.services.s3.S3Client
//import aws.sdk.kotlin.services.s3.model.GetObjectRequest
//import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
//import aws.smithy.kotlin.runtime.content.decodeToString
//import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

import java.util.Properties
import android.util.Log

class MySettings(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("larangel.rondingpeinn", Context.MODE_PRIVATE)

    suspend fun fetchAndProcessS3Config(bucketName: String, regionStr: String, targetHKey: String) {
        val regex = Regex("configCasetaApp/config\\.ini_.*[0-9.]+")
        val bucketUrl = "https://$bucketName.s3.$regionStr.amazonaws.com"
        val client = OkHttpClient()
        saveInt("APP_ACTIVADA",0)
        try {
            // 1. Obtener el XML del listado del bucket
            val request = Request.Builder().url(bucketUrl).build()
            val response = client.newCall(request).execute()
            val xmlBody = response.body?.string() ?: ""

            if (!response.isSuccessful) throw Exception("Error al obtener lista: ${response.code}")

            // 2. Parsear el XML para encontrar la llave (Key) que coincida con la Regex
            val filesFoundKey = parseS3XmlForMatchingKey(xmlBody, regex)
            var founded = false
            filesFoundKey?.forEach { fileKey ->
                // 3. Descargar el archivo .ini encontrado
                val fileUrl = if (bucketUrl.endsWith("/")) "$bucketUrl$fileKey" else "$bucketUrl/$fileKey"
                val fileRequest = Request.Builder().url(fileUrl).build()
                val fileResponse = client.newCall(fileRequest).execute()
                val iniContent = fileResponse.body?.string() ?: ""

                // 4. Parsear el contenido del .ini
                val properties = Properties()
                properties.load(StringReader(iniContent))

                val hKeyVal = properties.getProperty("hkeyseguridad")

                // 5. Validar y guardar
                if (hKeyVal == targetHKey) {
                    founded = true
                    saveToPreferences(properties)
                    Log.d("ConfigS3", "Configuración guardada correctamente.")
                    return@forEach
                } else {
                    Log.e("ConfigS3", "HKey inválido: $hKeyVal")
                }
            }
            if (founded == false)
                cleanPreference()

        } catch (e: Exception) {
            Log.e("ConfigS3", "Error: ${e.message}")
        }
    }
    private fun saveToPreferences(props: Properties) {
        saveInt("APP_ACTIVADA",1)
        with(sharedPreferences.edit()) {
            val REGISTRO_CARROS_SPREADSHEET_ID  = props.getProperty("googlesheet_registro_carros_id") ?: ""
            val PARKING_SPREADSHEET_ID          = props.getProperty("googlesheet_parking_id") ?: ""
            val PERMISOS_SPREADSHEET_ID         = props.getProperty("googlesheet_permisos_ids") ?: "[]"
            val COTO                            = if(props.getProperty("AppType") == "admon1") "coto1" else "coto2"
            val WS_AUTOS_REGISTRADOS            = props.getProperty("worksheet_autos_registrados") + "_$COTO"
            val WS_DOMICILIOS_UBICACION         = props.getProperty("worksheet_domicilios") + "_$COTO"
            val WS_POR_REVISAR                  = props.getProperty("worksheet_porRevisar") + "_$COTO"
            val WS_PARKING_SLOTS                = props.getProperty("worksheet_porkingSlots") + "_$COTO"
            val WS_AUTOS_EVENTOS                = props.getProperty("worksheet_autos_eventos") + "_$COTO"
            val WS_INCIDENCIAS_CONFIG           = props.getProperty("worksheet_incidencias_config") + "_$COTO"
            val WS_INCIDENCIAS_EVENTOS          = props.getProperty("worksheet_incidencias_eventos") + "_$COTO"
            val WS_MULTAS_GENERADAS             = props.getProperty("worksheet_multas") + "_$COTO"
            val WS_DOMICILIO_WARNINGS           = props.getProperty("worksheet_domicilio_warnings") + "_$COTO"

            putString("REGISTRO_CARROS_SPREADSHEET_ID", REGISTRO_CARROS_SPREADSHEET_ID)
            putString("PARKING_SPREADSHEET_ID", PARKING_SPREADSHEET_ID)
            putString("PERMISOS_SPREADSHEET_ID", PERMISOS_SPREADSHEET_ID)
            putString("COTO", COTO)
            putString("WS_AUTOS_REGISTRADOS", WS_AUTOS_REGISTRADOS)
            putString("WS_DOMICILIOS_UBICACION", WS_DOMICILIOS_UBICACION)
            putString("WS_POR_REVISAR", WS_POR_REVISAR)
            putString("WS_PARKING_SLOTS", WS_PARKING_SLOTS)
            putString("WS_AUTOS_EVENTOS", WS_AUTOS_EVENTOS)
            putString("WS_INCIDENCIAS_CONFIG", WS_INCIDENCIAS_CONFIG)
            putString("WS_INCIDENCIAS_EVENTOS", WS_INCIDENCIAS_EVENTOS)
            putString("WS_MULTAS_GENERADAS", WS_MULTAS_GENERADAS)
            putString("WS_DOMICILIO_WARNINGS", WS_DOMICILIO_WARNINGS)

            //CLEAN TIME CACHE
            putLong("PERMISOS_CACHE_TIMESTAMP",0)
            putLong("VEHICLE_CACHE_TIMESTAMP",0)
            putLong("TAGS_CACHE_TIMESTAMP",0)
            putLong("DIRECTIONS_CACHE_TIMESTAMP",0)
            putLong("PORREVISAR_CACHE_TIMESTAMP",0)
            putLong("PARKINGSLOTS_CACHE_TIMESTAMP",0)
            putLong("EVENTOS_CACHE_TIMESTAMP",0)
            putLong("INCIDENCIACONFIG_CACHE_TIMESTAMP",0)
            putLong("INCIDENCIAEVENTOS_CACHE_TIMESTAMP",0)
            putLong("MULTAS_CACHE_TIMESTAMP",0)
            putLong("DOMICILIOWARNINGS_CACHE_TIMESTAMP",0)
            apply()
        }
    }
    private fun cleanPreference(){
        with(sharedPreferences.edit()) {
            putInt("APP_ACTIVADA", 0)
            putString("REGISTRO_CARROS_SPREADSHEET_ID", "")
            putString("PARKING_SPREADSHEET_ID", "")
            putString("PERMISOS_SPREADSHEET_ID", "[]")
            //putString("COTO", COTO)
            putString("WS_AUTOS_REGISTRADOS", "")
            putString("WS_DOMICILIOS_UBICACION", "")
            putString("WS_POR_REVISAR", "")
            putString("WS_PARKING_SLOTS", "")
            putString("WS_AUTOS_EVENTOS", "")
            putString("WS_INCIDENCIAS_CONFIG", "")
            putString("WS_INCIDENCIAS_EVENTOS", "")
            putString("WS_MULTAS_GENERADAS", "")
            putString("WS_DOMICILIO_WARNINGS", "")

            //CLEAN TIME CACHE
            putLong("PERMISOS_CACHE_TIMESTAMP",0)
            putLong("VEHICLE_CACHE_TIMESTAMP",0)
            putLong("TAGS_CACHE_TIMESTAMP",0)
            putLong("DIRECTIONS_CACHE_TIMESTAMP",0)
            putLong("PORREVISAR_CACHE_TIMESTAMP",0)
            putLong("PARKINGSLOTS_CACHE_TIMESTAMP",0)
            putLong("EVENTOS_CACHE_TIMESTAMP",0)
            putLong("INCIDENCIACONFIG_CACHE_TIMESTAMP",0)
            putLong("INCIDENCIAEVENTOS_CACHE_TIMESTAMP",0)
            putLong("MULTAS_CACHE_TIMESTAMP",0)
            putLong("DOMICILIOWARNINGS_CACHE_TIMESTAMP",0)

            //CLEAN DATA CACHE
            putString("PERMISOS_CACHE","[]")
            putString("VEHICLE_CACHE","[]")
            putString("TAGS_CACHE","[]")
            putString("DIRECTIONS_CACHE","[]")
            putString("PORREVISAR_CACHE","[]")
            putString("PARKINGSLOTS_CACHE","[]")
            putString("EVENTOS_CACHE","[]")
            putString("INCIDENCIACONFIG_CACHE","[]")
            putString("INCIDENCIAEVENTOS_CACHE","[]")
            putString("MULTAS_CACHE","[]")
            putString("DOMICILIOWARNINGS_CACHE","[]")
            apply()
        }
    }
    private fun parseS3XmlForMatchingKey(xml: String, regex: Regex): MutableList<String>? {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var eventType = parser.eventType
        var currentTag: String? = null

        var listResult: MutableList<String> = mutableListOf()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                currentTag = parser.name
            } else if (eventType == XmlPullParser.TEXT && currentTag == "Key") {
                val key = parser.text
                if (key.matches(regex)){
                    listResult.add( key )
                }
            }
            eventType = parser.next()
        }
        return listResult
    }
    fun saveListCheckPoint(key: String, ListaCheckP: List<CheckPoint>){
        val jsonString = Json.encodeToString(ListaCheckP)
        sharedPreferences.edit() { putString(key, jsonString) }
    }
    fun getListCheckPoint(key: String): List<CheckPoint> {
        val jsonString=sharedPreferences.getString(key, "[]") ?: "[]"
        val objectList = Json.decodeFromString<List<CheckPoint>>(jsonString)
        return objectList
    }

    fun saveList(key: String, DataList: List<List<String>>){
        val jsonString = Json.encodeToString(DataList)
        sharedPreferences.edit() { putString(key, jsonString) }
    }
    fun saveSingleList(key: String, DataList: List<String>){
        val jsonString = Json.encodeToString(DataList)
        sharedPreferences.edit() { putString(key, jsonString) }
    }
    fun getList(key: String): List<List<String>>{
        val jsonString=sharedPreferences.getString(key, "[]") ?: "[]"
        val objectList = Json.decodeFromString<List<List<String>>>(jsonString)
        return objectList
    }
    fun getSimpleList(key: String): List<String>{
        val jsonString=sharedPreferences.getString(key, "[]") ?: "[]"
        val objectList = Json.decodeFromString<List<String>>(jsonString)
        return objectList
    }


    fun saveLong(key: String, value: Long){
        sharedPreferences.edit() { putLong(key, value) }
    }
    fun getLong(key: String, defaultValue: Long): Long{
        return sharedPreferences.getLong(key, defaultValue) ?: defaultValue
    }

    fun saveString(key: String, value: String) {
        sharedPreferences.edit() { putString(key, value) }
    }

    fun getString(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    fun saveBoolean(key: String, value: Boolean) {
        sharedPreferences.edit() { putBoolean(key, value) }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun saveInt(key: String, value: Int) {
        sharedPreferences.edit() { putInt(key, value) }
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    fun clearAllPreferences() {
        sharedPreferences.edit() { clear() }
    }

    fun removePreference(key: String) {
        sharedPreferences.edit() { remove(key) }
    }
}