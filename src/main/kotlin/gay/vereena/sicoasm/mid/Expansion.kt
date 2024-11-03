package gay.vereena.sicoasm.mid

import gay.vereena.sicoasm.back.assembleTree
import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.util.Stack


data class MacroExpanded(val macro: MacroST, val call: MacroCallST, val result: Node) : Notification


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

        override suspend fun visitIdent(n: IdentST) = lookupBinding(n).value as ExprST // TODO: don't just cast to ExprST

        override suspend fun visitMacroCall(n: MacroCallST): Node {
            val macro = lookupBinding(n.name).value
            val block = if(macro is MacroST) {
                if(n.args.size != macro.params.size) ws.reportFatal("Macro '${n.name.value}' expects ${macro.params.size} arguments; found ${n.args.size}", true)
                withScope(macro.scope) {
                    pushMacroCall()
                    n.args.zip(macro.params).forEach { (value, name) ->
                        macroArgs += name
                        scope[name] = visit(value)
                    }
                    BlockST(macro.body.map { visit(it) }, scope).also { popMacroCall() }
                }
            } else {
                ws.reportFatal("Attempt to call non-macro value '$macro'", true)
            }
            notifyOf(n, MacroExpanded(macro, n, block))
            return block
        }
    }
    val expandedAst = expander.visit(ast)
    println("expanded AST:\n${astToString(expandedAst)}")
    enqueueWorker(assembleTree(expandedAst as FileST))
}