package gay.vereena.sicoasm.frontend


sealed class Node

sealed class ExprST : Node()
data class IntST(val value: Int) : ExprST()
data class StringST(val value: String) : ExprST()
data class IdentST(val value: String) : ExprST()
data class LabelST(val value: String) : ExprST()

enum class UnaryOP {
    NEG
}
data class UnaryST(val op: UnaryOP, val value: ExprST) : ExprST()

enum class BinaryOP {
    ADD,
    SUB,
    MUL,
    DIV
}
data class BinaryST(val op: BinaryOP, val left: ExprST, val right: ExprST) : ExprST()

data object PosST : ExprST()
data object NextST : ExprST()
data class ParenST(val value: ExprST) : ExprST()

data class MacroCallST(val name: String, val args: List<ExprST>) : Node()

data class DefineST(val name: String, val value: ExprST) : Node()
data class MacroST(val name: String, val params: List<String>, val body: List<Node>) : Node()
data class IncludeST(val path: String) : Node()
data class FileST(val lexer: Lexer, val includes: List<IncludeST>, val body: List<Node>) : Node()


fun astToString(n: Node): String {
    val sb = StringBuilder()
    var level = 0

    fun emit(s: String) = sb.append(s)
    fun emitln(s: String) = sb.append(s + "\n")
    fun indent() {  for(i in 0..<level) emit("  ") }

    fun visit(n: Node) {
        when(n) {
            is IntST -> emitln("int(" + n.value + ")")
            is StringST -> emitln("string(" + n.value +  ")")
            is IdentST -> emitln("ident(" + n.value + ")")
            is LabelST -> emitln("label(" + n.value + ")")
            is UnaryST -> {
                emitln("unary(${n.op}):")
                level++
                indent()
                visit(n.value)
                level--
            }
            is BinaryST -> {
                emitln("binary(${n.op}):")
                level++
                indent()
                    emitln("left:")
                    level++
                    indent()
                    visit(n.left)
                    level--

                    indent()
                    level++
                    emitln("right:")
                    indent()
                    visit(n.right)
                    level--
                level--
            }
            is PosST -> emitln("pos")
            is NextST -> emitln("next")
            is ParenST -> {
                emitln("paren:")
                level++
                indent()
                visit(n.value)
                level--
            }
            is IncludeST -> emitln("include(" + n.path + ")")
            is DefineST -> {
                emitln("define(${n.name}):")
                level++
                indent()
                visit(n.value)
                level--
            }
            is MacroST -> {
                emitln("macro(${n.name}):")
                level++
                    if(n.params.isNotEmpty()) {
                        indent()
                        emitln("params:")
                        level++
                        indent()
                        n.params.forEach { emit("$it ") }
                        emit("\n")
                        level--
                    }

                    if(n.body.isNotEmpty()) {
                        indent()
                        emitln("body:")
                        level++
                        n.body.forEach {
                            indent()
                            visit(it)
                        }
                        level--
                    }
                level--
            }
            is MacroCallST -> {
                emitln("macroCall(${n.name}):")
                level++
                    if(n.args.isNotEmpty()) {
                        indent()
                        emitln("args:")
                        level++
                        n.args.forEach {
                            indent()
                            visit(it)
                        }
                        level--
                    }
                level--
            }
            is FileST -> {
                emitln("file:")
                level++
                    if(n.includes.isNotEmpty()) {
                        indent()
                        emitln("includes:")
                        level++
                        n.includes.forEach {
                            indent()
                            visit(it)
                        }
                        level--
                    }

                    if(n.body.isNotEmpty()) {
                        indent()
                        emitln("body:")
                        level++
                        n.body.forEach {
                            indent()
                            visit(it)
                        }
                        level--
                    }
                level--
            }
        }
    }

    visit(n)
    assert(level == 0)

    return sb.toString()
}