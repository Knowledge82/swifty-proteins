package dev.knowledge82.swiftyproteins.prefs

import dev.knowledge82.swiftyproteins.scene.AtomColors
import dev.knowledge82.swiftyproteins.scene.Representation

/** Light or dark (bonus VII.2 "dark mode", document 21). */
enum class ThemeMode(val label: String) {
    /** What the phone says (Quick Settings "Dark theme"): the default. */
    SYSTEM("Follow the phone"),
    DARK("Dark (cogitator)"),
    LIGHT("Light (parchment)");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]

    /** Is the app dark, given whether the phone is? */
    fun isDark(phoneIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> phoneIsDark
        DARK -> true
        LIGHT -> false
    }
}

/**
 * The settings of the app (bonus VII.2, document 16). For the whole app on this phone, not per account: they say how
 * the app behaves here (sounds, how a molecule first appears), nothing private. The favourites ARE per account.
 *
 * @param defaultModel   the model a molecule opens with (the key on the molecule screen still changes it)
 * @param showHydrogens  whether hydrogens are shown when a molecule opens
 * @param sounds         the cogitator's sounds (document 14); the phone's silent mode still wins when they are on
 * @param theme          light, dark or as the phone (document 21)
 * @param atomColors     the colour scheme of the atoms (document 21)
 * @param tutorialSeen   the first-run tutorial was finished or skipped (document 21); it can be replayed from here
 */
data class Settings(
    val defaultModel: Representation = Representation.BALL_AND_STICK,
    val showHydrogens: Boolean = true,
    val sounds: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val atomColors: AtomColors = AtomColors.JMOL,
    val tutorialSeen: Boolean = false,
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
    const val THEME = "theme"
    const val ATOM_COLORS = "atom_colors"
    const val TUTORIAL = "tutorial_seen"

    /** Every key a store must read. One list, next to [encode]: a new setting cannot be saved and never read back. */
    val KEYS = listOf(MODEL, HYDROGENS, SOUNDS, THEME, ATOM_COLORS, TUTORIAL)

    fun encode(settings: Settings): Map<String, String> = mapOf(
        MODEL to settings.defaultModel.name,
        HYDROGENS to settings.showHydrogens.toString(),
        SOUNDS to settings.sounds.toString(),
        THEME to settings.theme.name,
        ATOM_COLORS to settings.atomColors.name,
        TUTORIAL to settings.tutorialSeen.toString(),
    )

    fun decode(values: Map<String, String?>): Settings {
        val defaults = Settings()
        return Settings(
            defaultModel = Representation.entries.firstOrNull { it.name == values[MODEL] } ?: defaults.defaultModel,
            showHydrogens = values[HYDROGENS]?.toBooleanStrictOrNull() ?: defaults.showHydrogens,
            sounds = values[SOUNDS]?.toBooleanStrictOrNull() ?: defaults.sounds,
            theme = ThemeMode.entries.firstOrNull { it.name == values[THEME] } ?: defaults.theme,
            atomColors = AtomColors.entries.firstOrNull { it.name == values[ATOM_COLORS] } ?: defaults.atomColors,
            tutorialSeen = values[TUTORIAL]?.toBooleanStrictOrNull() ?: defaults.tutorialSeen,
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
