// Builds app/src/main/assets/ligand_index.tsv (document 21): one line per ligand of ligands.txt,
//   ID <tab> name <tab> Hill formula <tab> number of atoms
// with the APP'S OWN parser and formula, so the list shows exactly what the molecule screen will show after loading.
//
// Usage (from the repository root, after compiling the app's pure sources with kotlinc):
//   kotlin -cp <classes> BuildLigandIndexKt <dir with ID.cif files> app/src/main/assets/ligands.txt app/src/main/assets/ligand_index.tsv [names.tsv]
// The .cif files are the ones RCSB serves at https://files.rcsb.org/ligands/view/<ID>.cif (the Chemical Component
// Dictionary). [names.tsv] (ID <tab> name) is used when a file has no name of its own: the corpus this was first built
// from had atoms and bonds from the CCD but "?" as the name, so the names came from the CCD's chem_comp.name.
import dev.knowledge82.swiftyproteins.cif.CifParser
import dev.knowledge82.swiftyproteins.cif.ParseResult
import dev.knowledge82.swiftyproteins.molecule.MolecularFormula
import java.io.File

fun main(args: Array<String>) {
    require(args.size == 3 || args.size == 4) { "usage: <cif dir> <ligands.txt> <out.tsv> [names.tsv]" }
    val dir = File(args[0])
    val names: Map<String, String> = if (args.size == 4) File(args[3]).readLines().mapNotNull { line ->
        val tab = line.indexOf('\t'); if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
    }.toMap() else emptyMap()
    val ids = File(args[1]).readLines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    var missing = 0
    val lines = ids.mapNotNull { id ->
        val file = File(dir, "$id.cif")
        if (!file.isFile) { missing++; System.err.println("no file for $id"); return@mapNotNull null }
        when (val parsed = CifParser.parse(file.readText())) {
            is ParseResult.Success -> {
                val m = parsed.molecule
                val name = (m.name ?: names[id] ?: "").replace(Regex("\\s+"), " ").trim().take(MAX_NAME)
                "$id\t$name\t${MolecularFormula.plain(m.atoms)}\t${m.atoms.size}"
            }
            is ParseResult.Failure -> { missing++; System.err.println("$id does not parse: ${parsed.failure}"); null }
        }
    }
    File(args[2]).writeText("# Swifty Proteins ligand index (document 21): ID\tname\tformula\tatoms\n" + lines.joinToString("\n") + "\n")
    println("${lines.size} ligands written, $missing without a usable file")
}

/** The same limit as the parser's (CifExtractor): a name is a label, not a paragraph. */
private const val MAX_NAME = 400
