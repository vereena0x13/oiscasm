package gay.vereena.oiscasm.mid

import kotlinx.coroutines.flow.*

import gay.vereena.oiscasm.*
import gay.vereena.oiscasm.driver.*
import gay.vereena.oiscasm.front.*
import gay.vereena.oiscasm.back.*


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
            if(macro !is MacroST) ws.reportFatal("Attempt to call non-macro value '$macro'", true)

            if (n.args.size > macro.params.size) ws.reportFatal(
                "Macro '${n.name.value}' expects ${macro.params.size} arguments; found ${n.args.size}",
                true
            )

            val args = mutableListOf<ExprST>()
            args.addAll(n.args)
            while(args.size < macro.params.size) args += EmptyExprST

            return withScope(Scope(scope)) {
                args.zip(macro.params).forEach { (value, name) -> scope[name] = visit(value) }
                labels = findLabels(macro)
                BlockST(macro.body.asFlow().map { visit(it) }.filter { it !is EmptyST }.toList(), scope).also { labels = null }
            }.also { notifyOf(n, MacroExpanded(macro, n, it)) }
        }

        // NOTE TODO: can't support ComputeLater; do we care?
        override suspend fun visitRepeat(n: RepeatST) =
            eval(n.count, null).checkInt().let { count ->
                BlockST((0..<count).map { i ->
                    if(n.iteratorName != null) withScope(Scope(scope)) {
                        scope[n.iteratorName] = IntST(i)
                        visit(n.body)
                    } else visit(n.body)
                }, scope).also { notifyOf(n, Expanded) }
            }
    }

    val expandedAst = expander.visit(ast)
    if(config.debug.printExpandedAst) println("expanded AST:\n${astToString(expandedAst)}")
    enqueueWorker(assembleTree(expandedAst as FileST))
}