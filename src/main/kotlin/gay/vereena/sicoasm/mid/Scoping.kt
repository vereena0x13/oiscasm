package gay.vereena.sicoasm.mid

import gay.vereena.sicoasm.*
import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.util.*


data class Binding(val value: Node, val export: Boolean = false)

class Scope(val parent: Scope? = null) {
    private val bindings = mutableMapOf<String, Binding>()
    private val includes: MutableSet<Scope> = if(parent?.includes == null) mutableSetOf() else parent.includes.toMutableSet()

    fun set(name: String, value: Node, export: Boolean = false) {
        if (bindings[name] != null) throw IllegalStateException("Attempt to re-declare '$name'")
        bindings[name] = Binding(value, export)
    }

    operator fun set(name: String, value: Node) = set(name, value, false)

    operator fun get(name: String, checkIncludes: Boolean = true, checkParent: Boolean = true): Binding? {
        if(name in bindings) return bindings[name]!!

        if(checkIncludes) {
            val v = includes.firstNotNullOfOrNull { it[name, false] }
            if(v != null && v.export) return v
        }

        if(checkParent) return parent?.get(name)

        return null
    }
}


class WithScopes(scope: Scope) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithScopes>

    internal var currentScope = scope
}

val WorkerScope.scope get() = withExt(WithScopes) { currentScope }

suspend fun <T> WorkerScope.withScope(s: Scope, action: suspend (Scope) -> T): T = withExt(WithScopes) {
    val os = currentScope
    currentScope = s
    val result = action(currentScope)
    currentScope = os
    result
}

suspend fun WorkerScope.lookupBinding(name: String, scope: Scope = this.scope) = with(WithScopes) { lookupBinding(name, scope, true)!! }

suspend fun WorkerScope.lookupBinding(name: String, scope: Scope = this.scope, wait: Boolean = true) = with(WithScopes) {
    val ws = this
    val binding = scope[name]
    val t = Throwable()
    if (binding == null) {
        if(wait) waitOn<NameBound>(Pair(name, scope)) {
            t.printStackTrace()
            ws.reportError("Undeclared identifier: $name")
        }
        else return@with null
    }
    scope[name]!!
}


data object NameBound : Notification


fun bindNames(ast: FileST) = worker(WorkerName("scoping") + WithScopes(ast.scope)) {
    val nameBinder = object : ASTAdapter {
        override suspend fun visitLabel(n: LabelST) = n.also {
            scope[n.value] = LabelRefST(n.value)
            notifyOf(Pair(n, scope), NameBound)
        }

        override suspend fun visitDefine(n: DefineST) = n.also {
            scope[n.name.value] = n.value
            notifyOf(Pair(n.name, scope), NameBound)
        }

        override suspend fun visitMacro(n: MacroST) = n.also {
            scope[n.name.value] = n
            notifyOf(Pair(n.name, scope), NameBound)
        }

        override suspend fun visitIf(n: IfST) = n
        override suspend fun visitMacroCall(n: MacroCallST) = n
        override suspend fun visitRepeat(n: RepeatST) = n

        override suspend fun visitFile(n: FileST) = FileST(n.lexer, n.includes, n.body.map { visit(it) }.filter { it !is MacroST && it !is DefineST }, scope)
    }

    val scopedAst = nameBinder.visitFile(ast)
    if(config.debug) println("scopedAst:\n${astToString(scopedAst)}")
    enqueueWorker(expansion(scopedAst))
}