package dev.knowledge82.swiftyproteins.ui

import dev.knowledge82.swiftyproteins.loading.LoadStage
import java.util.Locale

/**
 * The loading dialog's checklist (bonus VII.4, document 20). Pure Kotlin, tested on the JVM.
 *
 * Four lines, always the same, in the order the work goes through them: the phone's copy, the connection, the
 * download, the parsing. Each one is done, the current one, or still waiting, so the user sees WHERE the time goes
 * (for a small ligand it is almost all "Connecting": the file itself is a few kilobytes).
 *
 * A line that was skipped (a fresh copy on the phone needs no network) is never reached: the dialog closes first.
 */
object LoadStageText {

    enum class Mark { DONE, ACTIVE, WAITING }

    data class Line(val label: String, val mark: Mark, val detail: String)

    private val LABELS = listOf("Archive", "Connect", "Download", "Parse")

    /** Which line a stage is: 0..3, or -1 before anything was reported. */
    fun step(stage: LoadStage): Int = when (stage) {
        LoadStage.Starting -> -1
        LoadStage.Archive -> 0
        LoadStage.Connecting -> 1
        is LoadStage.Downloading -> 2
        LoadStage.Parsing -> 3
    }

    fun lines(stage: LoadStage): List<Line> {
        val now = step(stage)
        return LABELS.mapIndexed { i, label ->
            val mark = when {
                i < now -> Mark.DONE
                i == now -> Mark.ACTIVE
                else -> Mark.WAITING
            }
            Line(label, mark, detail(i, mark, stage))
        }
    }

    private fun detail(i: Int, mark: Mark, stage: LoadStage): String = when (mark) {
        Mark.DONE -> "OK"
        Mark.WAITING -> ""
        Mark.ACTIVE -> when (stage) {
            is LoadStage.Downloading -> downloaded(stage)
            else -> ACTIVE_TEXT[i]
        }
    }

    private val ACTIVE_TEXT = listOf("searching", "waiting for the server", "", "atoms and bonds")

    /** "62 % · 4.6 / 7.4 KB", or "4.6 KB" when the server did not announce the length. */
    fun downloaded(stage: LoadStage.Downloading): String {
        val pct = percent(stage) ?: return "${kb(stage.received)} KB"
        return "$pct % · ${kb(stage.received)} / ${kb(stage.expected)} KB"
    }

    /** Whole percent, rounded DOWN (100 only when everything arrived), or null without a known length. */
    fun percent(stage: LoadStage.Downloading): Int? =
        if (stage.expected <= 0) null else (stage.received * 100 / stage.expected).toInt().coerceIn(0, 100)

    /** For the progress bar: 0..1 while downloading a file of known length, otherwise null (an indeterminate bar). */
    fun fraction(stage: LoadStage): Float? {
        val d = stage as? LoadStage.Downloading ?: return null
        if (d.expected <= 0) return null
        return (d.received.toFloat() / d.expected).coerceIn(0f, 1f)
    }

    private fun kb(bytes: Long): String = String.format(Locale.ROOT, "%.1f", bytes / 1024.0)

    /** The mark as console text: never colour alone (TalkBack and colour-blind users read the text). */
    fun markText(mark: Mark): String = when (mark) {
        Mark.DONE -> "[OK]"
        Mark.ACTIVE -> "[>>]"
        Mark.WAITING -> "[  ]"
    }

    /** What TalkBack says for a line: "Download: in progress, 62 % · 4.6 / 7.4 KB". */
    fun spoken(line: Line): String {
        val state = when (line.mark) {
            Mark.DONE -> "done"
            Mark.ACTIVE -> "in progress"
            Mark.WAITING -> "waiting"
        }
        return if (line.detail.isEmpty() || line.mark == Mark.DONE) "${line.label}: $state" else "${line.label}: $state, ${line.detail}"
    }
}
