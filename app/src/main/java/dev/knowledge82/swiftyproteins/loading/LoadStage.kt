package dev.knowledge82.swiftyproteins.loading

/**
 * Where a load is right now (bonus VII.4 "background parsing with progress indicators", document 20). The loading
 * dialog turns it into a checklist; the last step of all, building the 3D scene, is shown by the molecule screen.
 *
 * The stages always come in this order, and some may be skipped: a fresh copy on the phone goes from [Archive] straight
 * to the end, without the network.
 */
sealed interface LoadStage {

    /** Nothing reported yet (the instant between the tap and the first report from the background thread). */
    data object Starting : LoadStage

    /** Looking for (and reading) the phone's copy of the file (document 13). */
    data object Archive : LoadStage

    /** Asking the server: DNS, TCP, TLS, the request and the status line. For a small ligand, most of the wait. */
    data object Connecting : LoadStage

    /**
     * The body is arriving.
     * @param expected what the server announced in `Content-Length`, or -1 if it did not say (then no percentage).
     */
    data class Downloading(val received: Long, val expected: Long) : LoadStage

    /** The text is being turned into atoms and bonds (CifParser). */
    data object Parsing : LoadStage
}
