package gay.vereena.oiscasm.back

import kotlinx.coroutines.flow.*

import gay.vereena.oiscasm.config
import gay.vereena.oiscasm.driver.*
import gay.vereena.oiscasm.front.*
import gay.vereena.oiscasm.mid.*
import gay.vereena.oiscasm.util.*


class TreeAssembled(val code: IntArray) : Notification

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
            override fun labelAddr(name: String) = labels[name]?.addr
        }

        private suspend fun eval(n: ExprST) = eval(n, evalCtx)

        private suspend fun evalMaybeLater(n: ExprST) = try {
            val value = eval(n)
            if(value is IntValue) asm.emit(value.value)
            value.toAST()
        } catch(_: ComputeLater) {
            val pos = asm.emit(DeferredST.MAGIC_NUMBER)
            val replacer = object : ASTAdapter {
                override suspend fun visitPos(n: PosST) = IntST(pos)
            }
            DeferredST(pos, replacer.visitExpr(n), labels)
        }

        override suspend fun visitInt(n: IntST)             = n.also { asm.emit(it.value) }
        override suspend fun visitString(n: StringST)       = n.also { it.value.forEach { c -> asm.emit(c.code) } }
        override suspend fun visitIdent(n: IdentST)         = visitExpr(lookupBinding(n.value).value as ExprST) // TODO: don't just cast to ExprST
        override suspend fun visitLabelRef(n: LabelRefST)   = n.also { asm.word(labels.getOrPut(n.value) { asm.label() }) }
        override suspend fun visitUnary(n: UnaryST)         = evalMaybeLater(n)
        override suspend fun visitBinary(n: BinaryST)       = evalMaybeLater(n)
        override suspend fun visitPos(n: PosST)             = asm.pos().let { asm.emit(it); IntST(it) }
        override suspend fun visitParen(n: ParenST)         = visitExpr(n.value)

        override suspend fun visitLabel(n: LabelST)         = n.also { asm.mark(labels.getOrPut(n.value) { asm.label() }) }

        override suspend fun visitBlock(n: BlockST) = withScope(n.scope) {
            pushLabels()
            BlockST(n.values.asFlow().map { visit(it) }.filter { it !is EmptyST }.toList(), scope)
                .also { popLabels()}
        }

        override suspend fun visitDefine(n: DefineST) = EmptyST.also {
            scope[n.name.value] = eval(n.value).toAST()
            notifyOf(Pair(n.name, scope), NameBound)
        }

        override suspend fun visitFile(n: FileST): FileST {
            n.body.asFlow().filterIsInstance<LabelST>().collect { labels[it.value] = asm.label() }

            val f = FileST(n.lexer, n.includes, n.body.asFlow().map { visit(it) }.filter { it !is EmptyST }.toList(), n.scope)

            val replacer = object : ASTAdapter {
                override suspend fun visitDeferred(n: DeferredST): ExprST {
                    val evalCtx = object : EvalContext {
                        override fun pos() = ice()
                        override fun labelAddr(name: String) = n.labels[name]!!.addr!!
                    }
                    val value = eval(n.expr, evalCtx)
                    asm.set(n.pos, value.checkInt())
                    return value.toAST()
                }
            }
            return replacer.visitFile(f)
        }

        override suspend fun visitEmpty(n: EmptyST)         = ice()
        override suspend fun visitEmptyExpr(n: EmptyExprST) = ice()
        override suspend fun visitBlank(n: BlankST)         = ice()
        override suspend fun visitDeferred(n: DeferredST)   = ice()
        override suspend fun visitBool(n: BoolST)           = ice()
        override suspend fun visitIf(n: IfST)               = ice()
        override suspend fun visitMacroCall(n: MacroCallST) = ice()
        override suspend fun visitMacro(n: MacroST)         = ice()
        override suspend fun visitRepeat(n: RepeatST)       = ice()
    }

    val finalAst = treeAssembler.visit(ast)
    val code = asm.assemble()

    if(config.debug.printAssembledAst) {
        println("final AST:\n${astToString(finalAst)}")

        if (false) {
            val maxLen = code.maxOfOrNull { it.toString().length }!!
            println("i: " + code.indices.joinToString(" ") { it.toString().padStart(maxLen) })
            println("c: " + code.joinToString(" ") { it.toString().padStart(maxLen) })
            println()
        } else {
            println(formatIntTable(code, 16))
            println()
        }
    }

    notifyOf(ast, TreeAssembled(code))
}