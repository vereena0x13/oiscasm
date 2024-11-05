package gay.vereena.sicoasm

import java.nio.file.*
import kotlin.io.path.*

import io.kotest.core.spec.style.FunSpec


fun readExpected(path: Path) = path.toFile().readLines().map { it.toInt() }.toIntArray()

class E2ETests : FunSpec({
    Path("src/test/resources/e2e")
        .listDirectoryEntries()
        .filter { it.extension != "out" }
        .forEach { it ->
            test(it.nameWithoutExtension) {
                build(Config(
                    inFile = it.toFile(),
                    bitWidth = 16,
                    debug = false
                ) { code: IntArray ->
                    val expected = readExpected(it.resolveSibling(it.nameWithoutExtension + ".out"))

                    fun errorString() = with(StringBuilder()) {
                        val chunkSize = 16
                        append("Expected size: ${expected.size}\n")
                        append("Actual size: ${code.size}\n")
                        append("Expected output:\n")
                        expected.toList().chunked(chunkSize).forEach { it ->
                            append("  ")
                            append(it.joinToString(", "))
                            append('\n')
                        }
                        append("Actual output:\n")
                        code.toList().chunked(chunkSize).forEach { it ->
                            append("  ")
                            append(it.joinToString(", "))
                            append('\n')
                        }
                        toString()
                    }

                    assert(code.size == expected.size) { errorString() }
                    assert(code.zip(expected).map { (a, b) -> a == b }.fold(true) { a, b -> a && b}) { errorString() }
                })
            }
        }
})