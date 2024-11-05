package gay.vereena.sicoasm

import java.nio.file.*
import kotlin.io.path.*

import io.kotest.core.spec.style.FunSpec


fun readExpected(path: Path) = path.toFile().readLines().map { it.toInt() }.toIntArray()

class E2ETests : FunSpec({
    Path("src/test/resources/e2e")
        .listDirectoryEntries()
        .filter { it.extension != "out" }
        .forEach {
            test(it.nameWithoutExtension) {
                val wof = { code: IntArray ->
                    val expected = readExpected(it.resolveSibling(it.nameWithoutExtension + ".out"))
                    assert(code.size == expected.size)
                    assert(code.zip(expected).map { (a, b) -> a == b }.fold(true) { a, b -> a && b})
                }
                build(Config(
                    inFile = it.toFile(),
                    bitWidth = 16,
                    debug = false,
                    writeOutFile = wof
                ))
            }
        }
})