package gay.vereena.sicoasm.back

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.mid.*
import gay.vereena.sicoasm.util.*


fun assembleTree(ast: FileST) = worker(WorkerName("scoping") + WithScopes(ast.scope)) {
    val asm = Assembler()
    val labels = mutableMapOf<String, Label>()

    val labelFinder = object : ASTVisitor {
        override suspend fun visitLabel(n: LabelST): ExprST {
            if(labels[n.value] != null) reportFatal("Duplicate label '${n.value}")
            labels[n.value] = asm.label()
            return n
        }
    }

    val treeAssembler = object : ASTVisitor {
        suspend fun eval(n: ExprST): Int = when(n) {
            is IntST -> n.value
            is StringST -> ice()
            is IdentST -> eval(lookupBinding(n).value!! as ExprST)
            is LabelST -> ice()
            is LabelRefST -> labels[n.value]!!.addr!! // TODO: wrong
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

        override suspend fun visitInt(n: IntST): ExprST = n.also { asm.word(it.value) }
        override suspend fun visitString(n: StringST): ExprST = n.also { it.value.forEach { c -> asm.word(c.code) } }
        override suspend fun visitIdent(n: IdentST): ExprST = ice()
        override suspend fun visitLabel(n: LabelST): ExprST = n.also { asm.mark(labels[n.value]!!) }
        override suspend fun visitLabelRef(n: LabelRefST): ExprST = n.also { asm.word(labels.getOrPut(n.value) { asm.label() }) }
        override suspend fun visitUnary(n: UnaryST): ExprST = IntST(eval(n).also { asm.word(it) })
        override suspend fun visitBinary(n: BinaryST): ExprST = IntST(eval(n).also { asm.word(it) })
        override suspend fun visitPos(n: PosST): ExprST = IntST(eval(n).also { asm.word(it) })
        override suspend fun visitNext(n: NextST): ExprST = IntST(eval(n).also { asm.word(it) })
        override suspend fun visitParen(n: ParenST): ExprST = IntST(eval(n).also { asm.word(it) })
        override suspend fun visitBlock(n: BlockST): Node = BlockST(n.values.map { visit(it) }.filter { it !is LabelST }.toList())
        override suspend fun visitMacroCall(n: MacroCallST): Node = ice()
        override suspend fun visitDefine(n: DefineST): Node = ice()
        override suspend fun visitMacro(n: MacroST): Node = ice()
        override suspend fun visitFile(n: FileST): Node = FileST(n.lexer, n.includes, n.body.map { visit(it) }.filter { it !is LabelST }.toList(), n.scope, n.isPrimary
        )
    }
    val finalAst = treeAssembler.visit(labelFinder.visit(ast))

    println(astToString(finalAst))

    val code = asm.assemble()
    println(code.joinToString(" "))
}
