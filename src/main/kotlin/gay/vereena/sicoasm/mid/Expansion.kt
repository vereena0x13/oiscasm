package gay.vereena.sicoasm.mid

import gay.vereena.sicoasm.back.assembleTree
import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.util.Stack


fun expansion(ast: FileST) = worker(WorkerName("expansion") + WithScopes(ast.scope)) {
    val ws = this
    val expander = object : ASTVisitor {
        private var macroArgs = mutableSetOf<String>()
        private val macroArgsStack = Stack<MutableSet<String>>()

        private fun pushMacroCall() {
            macroArgsStack.push(macroArgs)
            macroArgs = mutableSetOf()
        }

        private fun popMacroCall() { macroArgs = macroArgsStack.pop() }

        override suspend fun visitIdent(n: IdentST): ExprST = lookupBinding(n).value as ExprST // TODO: don't just cast to ExprST

        override suspend fun visitMacroCall(n: MacroCallST): Node {
            val macro = lookupBinding(n.name).value
            val result = if(macro is MacroST) {
                if(n.args.size != macro.params.size) ws.reportFatal("Macro '${n.name.value}' expects ${macro.params.size} arguments; found ${n.args.size}", true)
                withScope(Scope(scope)) {
                    pushMacroCall()
                    n.args.zip(macro.params).forEach { (value, name) ->
                        macroArgs += name
                        scope[name] = visit(value)
                    }
                    BlockST(macro.body.map { visit(it) }).also { popMacroCall() }
                }
            } else {
                ws.reportFatal("Attempt to call non-macro value '$macro'", true)
            }
            return if(result.values.size == 1) result.values[0] else result
        }
    }
    val expandedAst = expander.visit(ast)
    enqueueWorker(assembleTree(expandedAst as FileST))
}