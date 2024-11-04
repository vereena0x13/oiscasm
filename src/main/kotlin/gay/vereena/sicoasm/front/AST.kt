package gay.vereena.sicoasm.front

import kotlin.math.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.mid.*
import gay.vereena.sicoasm.util.*


sealed class Node : WorkUnit

sealed class ExprST : Node()

enum class UnaryOP {
    NEG,
    BIT_NOT,
    NOT
}

enum class BinaryOP {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    POW,

    BIT_AND,
    BIT_OR,
    BIT_XOR,

    SHL,
    SHR,

    EQ,
    NE,
    LT,
    GT,
    LTE,
    GTE,

    AND,
    OR
}

data class IntST(val value: Int) : ExprST()
data class StringST(val value: String) : ExprST()
data class IdentST(val value: String) : ExprST()
data class BoolST(val value: Boolean) : ExprST()
data class LabelST(val value: String) : ExprST()
data class LabelRefST(val value: String) : ExprST()
data class UnaryST(val op: UnaryOP, val value: ExprST) : ExprST()
data class BinaryST(val op: BinaryOP, val left: ExprST, val right: ExprST) : ExprST()
data object PosST : ExprST()
data class ParenST(val value: ExprST) : ExprST()
data class BlockST(val values: List<Node>, val scope: Scope) : Node()
data class MacroCallST(val name: IdentST, val args: List<ExprST>) : Node()
data class DefineST(val name: IdentST, val value: ExprST) : Node()
data class MacroST(val name: IdentST, val params: List<String>, val body: List<Node>, val scope: Scope) : Node()
data class RepeatST(val count: ExprST, val iteratorName: String, val body: List<Node>, val scope: Scope) : Node()
data class ResST(val count: ExprST, val value: ExprST) : Node()
data class IncludeST(val path: String) : Node()
data class FileST(val lexer: Lexer, val includes: List<IncludeST>, val body: List<Node>, val scope: Scope) : Node()


suspend fun WorkerScope.evalExpr(n: ExprST, pos: (() -> Int)? = null): Int = with(WithScopes) {
    when (n) {
        is IntST -> n.value
        is StringST -> ice()
        is IdentST -> evalExpr(lookupBinding(n).value as ExprST, pos)
        is BoolST -> ice()
        is LabelST -> ice()
        is LabelRefST -> ice()
        is UnaryST -> when (n.op) {
            UnaryOP.NEG -> -evalExpr(n.value, pos)
            UnaryOP.BIT_NOT -> evalExpr(n.value, pos).inv()
            UnaryOP.NOT -> ice()
        }
        is BinaryST -> when (n.op) {
            BinaryOP.ADD -> evalExpr(n.left, pos) + evalExpr(n.right, pos)
            BinaryOP.SUB -> evalExpr(n.left, pos) - evalExpr(n.right, pos)
            BinaryOP.MUL -> evalExpr(n.left, pos) * evalExpr(n.right, pos)
            BinaryOP.DIV -> evalExpr(n.left, pos) / evalExpr(n.right, pos)
            BinaryOP.MOD -> evalExpr(n.left, pos) % evalExpr(n.right, pos)
            BinaryOP.POW -> evalExpr(n.left, pos).toDouble().pow(evalExpr(n.right, pos)).toInt()
            BinaryOP.BIT_AND -> evalExpr(n.left, pos) and evalExpr(n.right, pos)
            BinaryOP.BIT_OR -> evalExpr(n.left, pos) or evalExpr(n.right, pos)
            BinaryOP.BIT_XOR -> evalExpr(n.left, pos) xor evalExpr(n.right, pos)
            BinaryOP.SHL -> evalExpr(n.left, pos) shl evalExpr(n.right, pos)
            BinaryOP.SHR -> evalExpr(n.left, pos) shr evalExpr(n.right, pos)
            BinaryOP.EQ -> ice()
            BinaryOP.NE -> ice()
            BinaryOP.LT -> ice()
            BinaryOP.GT -> ice()
            BinaryOP.LTE -> ice()
            BinaryOP.GTE -> ice()
            BinaryOP.AND -> ice()
            BinaryOP.OR -> ice()
        }
        is PosST -> if(pos != null) pos() else ice()
        is ParenST -> evalExpr(n.value, pos)
    }
}

interface ASTAdapter {
    suspend fun visit(n: Node): Node = when(n) {
        is IntST -> visitInt(n)
        is StringST -> visitString(n)
        is IdentST -> visitIdent(n)
        is BoolST -> visitBool(n)
        is LabelST -> visitLabel(n)
        is LabelRefST -> visitLabelRef(n)
        is UnaryST -> visitUnary(n)
        is BinaryST -> visitBinary(n)
        is PosST -> visitPos(n)
        is ParenST -> visitParen(n)
        is BlockST -> visitBlock(n)
        is MacroCallST -> visitMacroCall(n)
        is DefineST -> visitDefine(n)
        is MacroST -> visitMacro(n)
        is RepeatST -> visitRepeat(n)
        is ResST -> visitRes(n)
        is IncludeST -> visitInclude(n)
        is FileST -> visitFile(n)
    }

    suspend fun visitExpr(n: ExprST): ExprST = when(n) {
        is IntST -> visitInt(n)
        is StringST -> visitString(n)
        is IdentST -> visitIdent(n)
        is BoolST -> visitBool(n)
        is LabelST -> visitLabel(n)
        is LabelRefST -> visitLabelRef(n)
        is UnaryST -> visitUnary(n)
        is BinaryST -> visitBinary(n)
        is PosST -> visitPos(n)
        is ParenST -> visitParen(n)
    }

    suspend fun visitInt(n: IntST): ExprST = n
    suspend fun visitString(n: StringST): ExprST = n
    suspend fun visitIdent(n: IdentST): ExprST = n
    suspend fun visitBool(n: BoolST): ExprST = n
    suspend fun visitLabel(n: LabelST): ExprST = n
    suspend fun visitLabelRef(n: LabelRefST): ExprST = n
    suspend fun visitUnary(n: UnaryST): ExprST = UnaryST(n.op, visitExpr(n.value))
    suspend fun visitBinary(n: BinaryST): ExprST = BinaryST(n.op, visitExpr(n.left), visitExpr(n.right))
    suspend fun visitPos(n: PosST): ExprST = n
    suspend fun visitParen(n: ParenST): ExprST = ParenST(visitExpr(n.value))
    suspend fun visitMacroCall(n: MacroCallST): Node = MacroCallST(n.name, n.args.map { visitExpr(it) })
    suspend fun visitBlock(n: BlockST): Node = BlockST(n.values.map { visit(it) }, n.scope)
    suspend fun visitDefine(n: DefineST): Node = DefineST(n.name, visitExpr(n.value))
    suspend fun visitMacro(n: MacroST): Node = n
    suspend fun visitRepeat(n: RepeatST): Node = n
    suspend fun visitRes(n: ResST): Node = n
    suspend fun visitInclude(n: IncludeST): Node = n
    suspend fun visitFile(n: FileST): FileST = FileST(n.lexer, n.includes, n.body.map { visit(it) }, n.scope)
}


fun astToString(n: Node): String {
    val sb = StringBuilder()
    var level = 0

    fun emit(s: String) = sb.append(s)
    fun emitln(s: String) = sb.append(s + "\n")
    fun indent() {  for(i in 0..<level) emit("  ") }

    fun visit(n: Node) {
        when(n) {
            is IntST -> emitln("int(${n.value})")
            is StringST -> emitln("string(${n.value})")
            is IdentST -> emitln("ident(${n.value})")
            is BoolST -> emitln("bool(${n.value})")
            is LabelST -> emitln("label(${n.value})")
            is LabelRefST -> emitln("label_ref(${n.value})")
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
            is ParenST -> {
                emitln("paren:")
                level++
                indent()
                visit(n.value)
                level--
            }
            is BlockST -> {
                emitln("block:")
                level++
                n.values.forEach {
                    indent()
                    visit(it)
                }
                level--
            }
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
            is RepeatST -> {
                emitln("repeat(${n.count}, ${n.iteratorName}):")
                level++
                n.body.forEach {
                    indent()
                    visit(it)
                }
                level--
            }
            is ResST -> {
                emitln("res(${n.count}, ${n.value})")
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
            is IncludeST -> emitln("include(${n.path})")
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