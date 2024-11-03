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
        val ls = labels
        labels = mutableMapOf()
        labels.putAll(ls)
    }

    fun popLabels() { labels = labelsStack.pop() }

    fun getLabel(s: String) =
        if(s in labels) labels[s]
        else labelsStack.rawData().firstOrNull { s in it }?.get(s)

    val treeAssembler = object : ASTAdapter {
        suspend fun eval(n: ExprST): Int = when(n) {
            is IntST -> n.value
            is StringST -> ice()
            is IdentST -> eval(lookupBinding(n).value as ExprST)
            is LabelST -> ice()
            is LabelRefST -> ice()
            is UnaryST -> when(n.op) {
                UnaryOP.NEG -> -eval(n.value)
            }
            is BinaryST -> when(n.op) {
                BinaryOP.ADD -> eval(n.left) + eval(n.right)
                BinaryOP.SUB -> eval(n.left) - eval(n.right)
                BinaryOP.MUL -> eval(n.left) * eval(n.right)
                BinaryOP.DIV -> eval(n.left) / eval(n.right)
            }
            is PosST -> asm.pos()
            is NextST -> asm.pos() + 1
            is ParenST -> eval(n.value)
        }

        override suspend fun visitInt(n: IntST) = n.also { asm.emit(it.value) }
        override suspend fun visitString(n: StringST) = n.also { it.value.forEach { c -> asm.emit(c.code) } }
        override suspend fun visitIdent(n: IdentST) = ice()
        override suspend fun visitLabel(n: LabelST) = n.also {
            val l = getLabel(n.value)
            if(l == null) {
                val l2 = asm.label()
                labels[n.value] = l2
                asm.mark(l2)
            } else {
                asm.mark(l)
            }
        }
        override suspend fun visitLabelRef(n: LabelRefST) = n.also {
            val l = getLabel(n.value)
            if(l == null) {
                val l2 = asm.label()
                labels[n.value] = l2
                asm.word(l2)
            } else {
                asm.word(l)
            }
        }
        override suspend fun visitUnary(n: UnaryST) = IntST(eval(n).also { asm.emit(it) })
        override suspend fun visitBinary(n: BinaryST) = IntST(eval(n).also { asm.emit(it) })
        override suspend fun visitPos(n: PosST) = IntST(eval(n).also { asm.emit(it) })
        override suspend fun visitNext(n: NextST) = IntST(eval(n).also { asm.emit(it) })
        override suspend fun visitParen(n: ParenST) = IntST(eval(n).also { asm.emit(it) })
        override suspend fun visitBlock(n: BlockST) = withScope(n.scope) {
            //pushLabels()
            BlockST(n.values.map { visit(it) }.filter { it !is LabelST }.toList(), n.scope)//.also { popLabels() }
        }
        override suspend fun visitMacroCall(n: MacroCallST) = ice()
        override suspend fun visitDefine(n: DefineST) = ice()
        override suspend fun visitMacro(n: MacroST) = ice()
        override suspend fun visitFile(n: FileST) = FileST(n.lexer, n.includes, n.body.map { visit(it) }.filter { it !is LabelST }.toList(), n.scope)
    }
    val finalAst = treeAssembler.visit(ast)

    println("final AST:\n${astToString(finalAst)}")

    val code = asm.assemble()
    println(code.joinToString(" "))
    notifyOf(ast, TreeAssembled(code))
}
