package gay.vereena.sicoasm.front

import gay.vereena.sicoasm.mid.*


sealed class Node
data object EmptyST : Node()

sealed class ExprST : Node()

data class DeferredST(val pos: Int) : ExprST() {
    companion object {
        val MAGIC_NUMBER = "D3F3553D".toLong(16).toInt()
    }
}

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
data class LabelST(val value: String) : Node()
data class LabelRefST(val value: String) : ExprST()
data class UnaryST(val op: UnaryOP, val value: ExprST) : ExprST()
data class BinaryST(val op: BinaryOP, val left: ExprST, val right: ExprST) : ExprST()
data object PosST : ExprST()
data class ParenST(val value: ExprST) : ExprST()
data class IfST(val cond: ExprST, val then: Node, val otherwise: Node? = null): Node()
data class BlockST(val values: List<Node>, val scope: Scope) : Node()
data class MacroCallST(val name: IdentST, val args: List<ExprST>) : Node()
data class DefineST(val name: IdentST, val value: ExprST) : Node()
data class MacroST(val name: IdentST, val params: List<String>, val body: List<Node>, val scope: Scope) : Node()
data class RepeatST(val count: ExprST, val iteratorName: String?, val body: List<Node>, val scope: Scope) : Node()
data class IncludeST(val path: String) : Node()
data class FileST(val lexer: Lexer, val includes: List<IncludeST>, val body: List<Node>, val scope: Scope) : Node()


interface ASTAdapter {
    suspend fun visit(n: Node): Node = when(n) {
        is EmptyST      -> n
        is DeferredST   -> visitDeferred(n)
        is IntST        -> visitInt(n)
        is StringST     -> visitString(n)
        is IdentST      -> visitIdent(n)
        is BoolST       -> visitBool(n)
        is LabelST      -> visitLabel(n)
        is LabelRefST   -> visitLabelRef(n)
        is UnaryST      -> visitUnary(n)
        is BinaryST     -> visitBinary(n)
        is PosST        -> visitPos(n)
        is ParenST      -> visitParen(n)
        is IfST         -> visitIf(n)
        is BlockST      -> visitBlock(n)
        is MacroCallST  -> visitMacroCall(n)
        is DefineST     -> visitDefine(n)
        is MacroST      -> visitMacro(n)
        is RepeatST     -> visitRepeat(n)
        is IncludeST    -> visitInclude(n)
        is FileST       -> visitFile(n)
    }

    suspend fun visitExpr(n: ExprST): ExprST = when(n) {
        is DeferredST   -> visitDeferred(n)
        is IntST        -> visitInt(n)
        is StringST     -> visitString(n)
        is IdentST      -> visitIdent(n)
        is BoolST       -> visitBool(n)
        is LabelRefST   -> visitLabelRef(n)
        is UnaryST      -> visitUnary(n)
        is BinaryST     -> visitBinary(n)
        is PosST        -> visitPos(n)
        is ParenST      -> visitParen(n)
    }

    suspend fun visitDeferred(n: DeferredST): ExprST    = n
    suspend fun visitInt(n: IntST): ExprST              = n
    suspend fun visitString(n: StringST): ExprST        = n
    suspend fun visitIdent(n: IdentST): ExprST          = n
    suspend fun visitBool(n: BoolST): ExprST            = n
    suspend fun visitLabelRef(n: LabelRefST): ExprST    = n
    suspend fun visitUnary(n: UnaryST): ExprST          = UnaryST(n.op, visitExpr(n.value))
    suspend fun visitBinary(n: BinaryST): ExprST        = BinaryST(n.op, visitExpr(n.left), visitExpr(n.right))
    suspend fun visitPos(n: PosST): ExprST              = n
    suspend fun visitParen(n: ParenST): ExprST          = ParenST(visitExpr(n.value))
    suspend fun visitLabel(n: LabelST): Node            = n
    suspend fun visitIf(n: IfST): Node                  = IfST(visitExpr(n.cond), visit(n.then), if(n.otherwise == null) null else visit(n.otherwise))
    suspend fun visitMacroCall(n: MacroCallST): Node    = MacroCallST(n.name, n.args.map { visitExpr(it) })
    suspend fun visitBlock(n: BlockST): Node            = BlockST(n.values.map { visit(it) }, n.scope)
    suspend fun visitDefine(n: DefineST): Node          = DefineST(n.name, visitExpr(n.value))
    suspend fun visitMacro(n: MacroST): Node            = MacroST(n.name, n.params, n.body.map { visit(it) }, n.scope)
    suspend fun visitRepeat(n: RepeatST): Node          = RepeatST(visitExpr(n.count), n.iteratorName, n.body.map { visit(it) }, n.scope)
    suspend fun visitInclude(n: IncludeST): Node        = n
    suspend fun visitFile(n: FileST): FileST            = FileST(n.lexer, n.includes, n.body.map { visit(it) }, n.scope)
}


suspend fun findLabels(n: Node): Set<String> {
    val labels = mutableSetOf<String>()
    val finder = object : ASTAdapter {
        override suspend fun visitLabel(n: LabelST)     = n.also { labels += n.value }
        override suspend fun visitIf(n: IfST)           = IfST(n.cond, visit(n.then), if(n.otherwise == null) null else visit(n.otherwise))
        override suspend fun visitRepeat(n: RepeatST)   = RepeatST(n.count, n.iteratorName, n.body.map { visit(it) }, n.scope)
        override suspend fun visitMacro(n: MacroST)     = n.also { n.body.forEach { visit(it) } }
    }
    finder.visit(n)
    return labels.toSet()
}


fun astToString(n: Node): String {
    val sb = StringBuilder()
    var level = 0

    fun emit(s: String) = sb.append(s)
    fun emitln(s: String) = sb.append(s + "\n")
    fun indent() {  for(i in 0..<level) emit("  ") }

    fun visit(n: Node) {
        when(n) {
            is DeferredST -> emitln("deferred(${n.pos})")
            is EmptyST -> emitln("empty")
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
            is IfST -> {
                emitln("if:")
                level++
                    indent()
                    emitln("cond:")
                    level++
                        indent()
                        visit(n.cond)
                    level--

                    indent()
                    emitln("then:")
                    level++
                        indent()
                        visit(n.then)
                    level--

                    if(n.otherwise != null) {
                        indent()
                        emitln("otherwise:")
                        level++
                            indent()
                            visit(n.otherwise)
                        level--
                    }
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
            is MacroCallST -> {
                emitln("macroCall(${n.name})${if(n.args.isEmpty()) "" else ":"}")
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