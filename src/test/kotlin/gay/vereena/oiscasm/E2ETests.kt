package gay.vereena.oiscasm

import java.nio.file.*

import kotlin.io.path.*

import io.kotest.core.spec.style.FunSpec

import gay.vereena.oiscasm.util.*



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
        .forEach {
            test(it.nameWithoutExtension) {
                build(it.toFile(), defaultConfig()) { code ->
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
                    assert(code.contentEquals(expected)) { errorString() }
                }
            }
        }
})