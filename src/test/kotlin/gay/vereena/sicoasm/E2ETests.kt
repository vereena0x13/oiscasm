package gay.vereena.sicoasm

import gay.vereena.sicoasm.util.formatIntTable
import java.nio.file.*
import kotlin.io.path.*

import io.kotest.core.spec.style.FunSpec


val SPLIT_SPACE_REGEX = "\\s+".toRegex()
fun readExpected(path: Path) = path
    .toFile()
    .readLines()
    .flatMap { it.trim().split(SPLIT_SPACE_REGEX).map { x -> x.toInt() } }
    .toIntArray()

class E2ETests : FunSpec({
    Path("src/test/resources/e2e")
        .listDirectoryEntries()
        .filter { it.extension != "out" }
        .forEach { it ->
            test(it.nameWithoutExtension) {
                build(it.toFile(), defaultConfig()) { code: IntArray ->
                    val expected = readExpected(it.resolveSibling(it.nameWithoutExtension + ".out"))

                    fun errorString() = with(StringBuilder()) {
                        val chunkSize = 16
                        append("Expected size: ${expected.size}\n")
                        append("Actual size: ${code.size}\n")
                        append("Expected output:\n")
                        append(formatIntTable(expected, chunkSize))
                        append("\nActual output:\n")
                        append(formatIntTable(code, chunkSize))
                        toString()
                    }

                    assert(code.size == expected.size) { errorString() }
                    assert(code.zip(expected).map { (a, b) -> a == b }.fold(true) { a, b -> a && b }) { errorString() }
                }
            }
        }
})