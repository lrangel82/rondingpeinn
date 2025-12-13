import CheckPoint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class MySettings(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("larangel.rondingpeinn", Context.MODE_PRIVATE)

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