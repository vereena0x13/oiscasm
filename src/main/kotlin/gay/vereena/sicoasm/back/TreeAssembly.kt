package gay.vereena.sicoasm.back

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.mid.*
import gay.vereena.sicoasm.util.*


class TreeAssembled(val code: IntArray) : Notification

fun assembleTree(ast: FileST) = worker(WorkerName("assembly") + WithScopes(ast.scope)) {
    val asm = Assembler()
    var labels = mutableMapOf<String, Label>()
    val labelsStack = Stack<MutableMap<String, Label>>()

    fun pushLabels() {
        labelsStack.push(labels)
        val l = labels
        labels = mutableMapOf()
        labels.putAll(l)
    }

    fun popLabels() { labels = labelsStack.pop() }

    val treeAssembler = object : ASTAdapter {
        var blockLabels: Set<String>? = null

        suspend fun eval(n: ExprST): Int = when(n) {
            is IntST -> n.value
            is StringST -> ice()
            is IdentST -> eval(lookupBinding(n).value as ExprST)
            is BoolST -> ice()
            is LabelST -> ice()
            is LabelRefST -> ice()
            is UnaryST -> when(n.op) {
                UnaryOP.NEG -> -eval(n.value)
                UnaryOP.BIT_NOT -> eval(n.value).inv()
                UnaryOP.NOT -> TODO()
            }
            is BinaryST -> when(n.op) {
                BinaryOP.ADD -> eval(n.left) + eval(n.right)
                BinaryOP.SUB -> eval(n.left) - eval(n.right)
                BinaryOP.MUL -> eval(n.left) * eval(n.right)
                BinaryOP.DIV -> eval(n.left) / eval(n.right)
                BinaryOP.MOD -> eval(n.left) % eval(n.right)
                BinaryOP.POW -> TODO()
                BinaryOP.BIT_AND -> eval(n.left) and eval(n.right)
                BinaryOP.BIT_OR -> eval(n.left) or eval(n.right)
                BinaryOP.BIT_XOR -> eval(n.left) xor eval(n.right)
                BinaryOP.SHL -> eval(n.left) shl eval(n.right)
                BinaryOP.SHR -> eval(n.left) shr eval(n.right)
                BinaryOP.EQ -> TODO()
                BinaryOP.NE -> TODO()
                BinaryOP.LT -> TODO()
                BinaryOP.GT -> TODO()
                BinaryOP.LTE -> TODO()
                BinaryOP.GTE -> TODO()
                BinaryOP.AND -> TODO()
                BinaryOP.OR -> TODO()
            }
            is PosST -> asm.pos()
            is ParenST -> eval(n.value)
        }

        override suspend fun visitInt(n: IntST) = n.also { asm.emit(it.value) }
        override suspend fun visitString(n: StringST) = n.also { it.value.forEach { c -> asm.emit(c.code) } }
        override suspend fun visitIdent(n: IdentST) = ice()
        override suspend fun visitLabel(n: LabelST) = n.also { asm.mark(labels.getOrPut(n.value) { asm.label() }) }
        override suspend fun visitLabelRef(n: LabelRefST) = n.also { asm.word(labels.getOrPut(n.value) { asm.label() }) }
        override suspend fun visitUnary(n: UnaryST) = IntST(eval(n).also { asm.emit(it) })
        override suspend fun visitBinary(n: BinaryST) = IntST(eval(n).also { asm.emit(it) })
        override suspend fun visitPos(n: PosST) = IntST(eval(n).also { asm.emit(it) })
        override suspend fun visitParen(n: ParenST) = IntST(eval(n).also { asm.emit(it) })
        override suspend fun visitBlock(n: BlockST) = withScope(n.scope) {
            pushLabels()
            blockLabels = findLabels(n)
            BlockST(n.values.map { visit(it) }.filter { it !is LabelST }.toList(), n.scope)
                .also { popLabels(); blockLabels = null }
        }
        override suspend fun visitMacroCall(n: MacroCallST) = ice()
        override suspend fun visitDefine(n: DefineST) = ice()
        override suspend fun visitMacro(n: MacroST) = ice()
        override suspend fun visitFile(n: FileST): FileST {
            n.body.filterIsInstance<LabelST>().forEach { labels[it.value] = asm.label() }
            return FileST(n.lexer, n.includes, n.body.map { visit(it) }.filter { it !is LabelST }.toList(), n.scope)
        }
    }

    val finalAst = treeAssembler.visit(ast)
    println("final AST:\n${astToString(finalAst)}")

    val code = asm.assemble()
    println(code.indices.joinToString(" ") { it.toString().padStart(2) })
    println(code.joinToString(" ") { it.toString().padStart(2) })
    notifyOf(ast, TreeAssembled(code))
}
