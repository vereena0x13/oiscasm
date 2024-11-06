package gay.vereena.sicoasm.mid

import gay.vereena.sicoasm.*
import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.back.*


data class MacroExpanded(val macro: MacroST, val call: MacroCallST, val result: Node) : Notification
data object Expanded : Notification


fun expansion(ast: FileST) = worker(WorkerName("expansion") + WithScopes(ast.scope)) {
    val ws = this
    val expander = object : ASTAdapter {
        var labels: Set<String>? = null

        override suspend fun visitIdent(n: IdentST) = when {
            labels?.contains(n.value) == true -> LabelRefST(n.value)
            scope[n.value] != null -> scope[n.value]!!.value as ExprST // TODO: don't just cast to ExprST
            else -> n
        }.also { if(it !is IdentST) notifyOf(n, Expanded) }

        // NOTE TODO: can't support ComputeLater; do we care?
        override suspend fun visitIf(n: IfST) = when {
            eval(n.cond, null).checkBool() -> visit(n.then)
            n.otherwise != null -> visit(n.otherwise)
            else -> EmptyST
        }.also { notifyOf(n, Expanded) }

        override suspend fun visitMacroCall(n: MacroCallST): Node {
            val macro = lookupBinding(n.name.value).value
            val block = if (macro is MacroST) {
                if (n.args.size > macro.params.size) ws.reportFatal(
                    "Macro '${n.name.value}' expects ${macro.params.size} arguments; found ${n.args.size}",
                    true
                )

                val args = mutableListOf<ExprST>()
                args.addAll(n.args)
                while(args.size < macro.params.size) args += EmptyExprST

                withScope(Scope(scope)) {
                    args.zip(macro.params).forEach { (value, name) -> scope[name] = visit(value) }
                    labels = findLabels(macro)
                    BlockST(macro.body.map { visit(it) }.filter { it !is EmptyST }, scope).also { labels = null }
                }
            } else {
                ws.reportFatal("Attempt to call non-macro value '$macro'", true)
            }
            notifyOf(n, MacroExpanded(macro, n, block))
            return block
        }

        // NOTE TODO: can't support ComputeLater; do we care?
        override suspend fun visitRepeat(n: RepeatST): Node {
            val count = eval(n.count, null).checkInt()
            return BlockST((0..<count).map { i ->
                // NOTE TODO: maybe don't create a new scope each time
                // if iteratorName == null?
                withScope(Scope(scope)) {
                    if(n.iteratorName != null) scope[n.iteratorName] = IntST(i)
                    visit(n.body)
                }
            }, scope).also { notifyOf(n, Expanded) }
        }
    }

    val expandedAst = expander.visit(ast)
    if(config.debug.printExpandedAst) println("expanded AST:\n${astToString(expandedAst)}")
    enqueueWorker(assembleTree(expandedAst as FileST))
}