package gay.vereena.sicoasm.mid

import gay.vereena.sicoasm.back.assembleTree
import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*


suspend fun findLabels(n: Node): Set<String> {
    val labels = mutableSetOf<String>()
    val finder = object : ASTAdapter {
        override suspend fun visitLabel(n: LabelST) = n.also { labels += n.value }
        override suspend fun visitMacro(n: MacroST) = n.also { n.body.forEach { visit(it) } }
    }
    finder.visit(n)
    return labels.toSet()
}


data class MacroExpanded(val macro: MacroST, val call: MacroCallST, val result: Node) : Notification


fun expansion(ast: FileST) = worker(WorkerName("expansion") + WithScopes(ast.scope)) {
    val ws = this

    val expander = object : ASTAdapter {
        var labels: Set<String>? = null

        override suspend fun visitIdent(n: IdentST) =
            if(labels?.contains(n.value) == true) LabelRefST(n.value)
            else lookupBinding(n).value as ExprST // TODO: don't just cast to ExprST

        override suspend fun visitIf(n: IfST) = when {
            eval(n.cond, null).check<BoolValue>().value -> n.then
            n.otherwise != null -> n.otherwise
            else -> EmptyST
        }

        override suspend fun visitMacroCall(n: MacroCallST): Node {
            val macro = lookupBinding(n.name).value
            val block = if (macro is MacroST) {
                if (n.args.size != macro.params.size) ws.reportFatal(
                    "Macro '${n.name.value}' expects ${macro.params.size} arguments; found ${n.args.size}",
                    true
                )
                withScope(Scope(scope)) {
                    n.args.zip(macro.params).forEach { (value, name) -> scope[name] = visit(value) }
                    labels = findLabels(macro)
                    BlockST(macro.body.map { visit(it) }.filter { it !is DefineST && it !is EmptyST }, scope).also { labels = null }
                }
            } else {
                ws.reportFatal("Attempt to call non-macro value '$macro'", true)
            }
            notifyOf(n, MacroExpanded(macro, n, block))
            return block
        }

        override suspend fun visitDefine(n: DefineST) = n.also {
            if(n.value is PosST) scope[n.name.value] = PosST
            else scope[n.name.value] = eval(n.value, null).check<IntValue>().toAST()
        }

        override suspend fun visitRepeat(n: RepeatST): Node {
            val count = eval(n.count, null).check<IntValue>().value
            return BlockST((0..<count).flatMap { i ->
                withScope(Scope(scope)) {
                    if(n.iteratorName != null) scope[n.iteratorName] = IntST(i)
                    n.body.map { visit(it) }.filter { it !is EmptyST }
                }
            }, scope)
        }
    }

    val expandedAst = expander.visit(ast)
    println("expanded AST:\n${astToString(expandedAst)}")
    enqueueWorker(assembleTree(expandedAst as FileST))
}