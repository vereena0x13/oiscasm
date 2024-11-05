package gay.vereena.sicoasm.back

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.mid.*
import gay.vereena.sicoasm.util.*


class TreeAssembled(val code: IntArray) : Notification

private data class DeferredComputation(val pos: Int, val expr: ExprST, val labels: Map<String, Label>)

fun assembleTree(ast: FileST) = worker(WorkerName("assembly") + WithScopes(ast.scope)) {
    val asm = Assembler()

    val treeAssembler = object : ASTAdapter {
        private var labels = mutableMapOf<String, Label>()
        private val labelsStack = Stack<MutableMap<String, Label>>()

        private fun pushLabels() {
            labelsStack.push(labels)
            val l = labels
            labels = mutableMapOf()
            labels.putAll(l)
        }

        private fun popLabels() { labels = labelsStack.pop() }

        private val evalCtx = object : EvalContext {
            override fun pos() = asm.pos()

            override fun labelAddr(name: String): Int? {
                val label = labels[name]
                return label?.addr
            }
        }

        private val computeLaters = mutableListOf<DeferredComputation>()

        private suspend fun evalMaybeLater(n: ExprST): ExprST {
            return try {
                val value = eval(n)
                if(value is IntValue) asm.emit(value.value)
                value.toAST()
            } catch(_: ComputeLater) {
                val pos = asm.pos()
                asm.emit(DeferredST.MAGIC_NUMBER)
                val replacer = object : ASTAdapter {
                    override suspend fun visitPos(n: PosST): ExprST = IntST(pos)
                }
                computeLaters += DeferredComputation(pos, replacer.visitExpr(n), labels)
                DeferredST(pos)
            }
        }

        private suspend fun eval(n: ExprST) = eval(n, evalCtx)

        override suspend fun visitInt(n: IntST) = n.also { asm.emit(it.value) }
        override suspend fun visitString(n: StringST) = n.also { it.value.forEach { c -> asm.emit(c.code) } }
        override suspend fun visitLabelRef(n: LabelRefST) = n.also { asm.word(labels.getOrPut(n.value) { asm.label() }) }
        override suspend fun visitUnary(n: UnaryST) = evalMaybeLater(n)
        override suspend fun visitBinary(n: BinaryST) = evalMaybeLater(n)
        override suspend fun visitPos(n: PosST) = asm.pos().let { asm.emit(it); IntST(it) }
        override suspend fun visitParen(n: ParenST) = visitExpr(n.value)

        override suspend fun visitLabel(n: LabelST) = n.also { asm.mark(labels.getOrPut(n.value) { asm.label() }) }

        override suspend fun visitBlock(n: BlockST) = withScope(n.scope) {
            pushLabels()
            BlockST(n.values.map { visit(it) }.filter { it !is LabelST && it !is EmptyST }, scope)
                .also { popLabels()}
        }

        override suspend fun visitFile(n: FileST): FileST {
            n.body.filterIsInstance<LabelST>().forEach { labels[it.value] = asm.label() }

            val f = FileST(n.lexer, n.includes, n.body.map { visit(it) }.filter { it !is LabelST && it !is EmptyST }, n.scope)

            computeLaters.forEach {
                val evalCtx = object : EvalContext {
                    override fun pos() = ice()
                    override fun labelAddr(name: String) = it.labels[name]!!.addr!!
                }
                val value = eval(it.expr, evalCtx)
                asm.set(it.pos, value.checkInt())
            }

            // NOTE: we don't really need to do this, but I wanted to. Bite me.
            val replacer = object : ASTAdapter {
                override suspend fun visitDeferred(n: DeferredST) = IntST(asm.get(n.pos))
            }
            return replacer.visitFile(f)
        }

        override suspend fun visitBool(n: BoolST) = ice()
        override suspend fun visitIdent(n: IdentST) = ice()
        override suspend fun visitIf(n: IfST) = ice()
        override suspend fun visitMacroCall(n: MacroCallST) = ice()
        override suspend fun visitDefine(n: DefineST) = ice()
        override suspend fun visitMacro(n: MacroST) = ice()
        override suspend fun visitRepeat(n: RepeatST) = ice()
    }

    val finalAst = treeAssembler.visit(ast)
    println("final AST:\n${astToString(finalAst)}")

    val code = asm.assemble()
    println("i: " + code.indices.joinToString(" ") { it.toString().padStart(2) })
    println("c: " + code.joinToString(" ") { it.toString().padStart(2) })
    notifyOf(ast, TreeAssembled(code))
}
