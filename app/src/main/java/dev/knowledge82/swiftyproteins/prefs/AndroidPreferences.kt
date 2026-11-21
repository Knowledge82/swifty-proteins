package dev.knowledge82.swiftyproteins.prefs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * The settings in a SharedPreferences file (`settings.xml`, private to the app). Every value is TEXT through
 * [SettingsCodec], so the rules of reading (defaults for anything missing or odd) are the tested ones.
 */
class PrefsSettingsStore(context: Context) : SettingsStore {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): Settings = try {
        SettingsCodec.decode(KEYS.associateWith { prefs.getString(it, null) })
    } catch (e: Exception) {
        Settings()   // a value of another type in the file (getString on an int throws): defaults
    }

    // apply(): written in the background by Android; commit() would block, and we are on the IO thread anyway.
    override fun save(settings: Settings) {
        val editor = prefs.edit()
        for ((key, value) in SettingsCodec.encode(settings)) editor.putString(key, value)
        editor.apply()
    }

    private companion object {
        const val FILE = "settings"
        val KEYS = listOf(SettingsCodec.MODEL, SettingsCodec.HYDROGENS, SettingsCodec.SOUNDS)
    }
}

/** Keeps the [PreferencesController] alive across rotations, like the other ViewModels: a thin wrapper. */
class PreferencesViewModel(settingsStore: SettingsStore, favoritesStore: FavoritesStore) : ViewModel() {
    // ONE thread for the disk: saves in the order of the taps (PreferencesController).
    val controller = PreferencesController(settingsStore, favoritesStore, viewModelScope, Dispatchers.IO.limitedParallelism(1))
}

class PreferencesViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PreferencesViewModel::class.java)) {
            "This factory only builds PreferencesViewModel, not ${modelClass.name}"
        }
        // The favourites next to the accounts: noBackupFilesDir (not in cloud backups, like the accounts, document 05).
        return PreferencesViewModel(PrefsSettingsStore(appContext), FileFavoritesStore(File(appContext.noBackupFilesDir, "favorites"))) as T
    }
}
