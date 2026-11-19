import dev.knowledge82.swiftyproteins.cif.CifParser
import dev.knowledge82.swiftyproteins.cif.ParseResult
import java.io.File

fun main(args: Array<String>) {
    val out = StringBuilder()
    for (file in File(args[0]).listFiles()!!.filter { it.name.endsWith(".cif") }.sortedBy { it.name }) {
        val id = file.name.removeSuffix(".cif")
        when (val r = CifParser.parse(file.readText())) {
            is ParseResult.Failure -> out.append("L\t$id\tFAIL\t${r.failure}\n")
            is ParseResult.Success -> {
                val m = r.molecule
                out.append("L\t$id\tOK\t${m.atoms.size}\t${m.bonds.size}\n")
                for (a in m.atoms) out.append("A\t${a.name}\t${a.element}\t${a.x}\t${a.y}\t${a.z}\n")
                for (b in m.bonds) out.append("B\t${b.from}\t${b.to}\t${b.order}\n")
            }
        }
    }
    print(out)
}
