package dev.knowledge82.swiftyproteins.prefs

import dev.knowledge82.swiftyproteins.scene.Representation

/**
 * The settings of the app (bonus VII.2, document 16). For the whole app on this phone, not per account: they say how
 * the app behaves here (sounds, how a molecule first appears), nothing private. The favourites ARE per account.
 *
 * @param defaultModel   the model a molecule opens with (the key on the molecule screen still changes it)
 * @param showHydrogens  whether hydrogens are shown when a molecule opens
 * @param sounds         the cogitator's sounds (document 14); the phone's silent mode still wins when they are on
 */
data class Settings(
    val defaultModel: Representation = Representation.BALL_AND_STICK,
    val showHydrogens: Boolean = true,
    val sounds: Boolean = true,
)

/**
 * Settings <-> text pairs, for any key-value storage (SharedPreferences on the phone). Pure, tested. Reading is
 * FORGIVING: a missing key, an unknown model name (a future version, a damaged file) or a value that is not
 * "true"/"false" gives the default for THAT setting only; the others are kept.
 */
object SettingsCodec {
    const val MODEL = "default_model"
    const val HYDROGENS = "show_hydrogens"
    const val SOUNDS = "sounds"

    fun encode(settings: Settings): Map<String, String> = mapOf(
        MODEL to settings.defaultModel.name,
        HYDROGENS to settings.showHydrogens.toString(),
        SOUNDS to settings.sounds.toString(),
    )

    fun decode(values: Map<String, String?>): Settings {
        val defaults = Settings()
        return Settings(
            defaultModel = Representation.entries.firstOrNull { it.name == values[MODEL] } ?: defaults.defaultModel,
            showHydrogens = values[HYDROGENS]?.toBooleanStrictOrNull() ?: defaults.showHydrogens,
            sounds = values[SOUNDS]?.toBooleanStrictOrNull() ?: defaults.sounds,
        )
    }
}

/** Where the settings live. Never throws: a store that cannot be read gives the defaults. */
interface SettingsStore {
    fun load(): Settings
    fun save(settings: Settings)
}

/** For tests and previews. */
class MemorySettingsStore(var saved: Settings = Settings()) : SettingsStore {
    override fun load(): Settings = saved
    override fun save(settings: Settings) { saved = settings }
}
