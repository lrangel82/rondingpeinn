import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class MySettings(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("larangel.rondingpeinn", Context.MODE_PRIVATE)

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