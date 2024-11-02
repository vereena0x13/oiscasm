package gay.vereena.sicoasm.mid

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.frontend.*
import gay.vereena.sicoasm.util.*


data class Binding(val value: Node? = null, val export: Boolean = false)


class Scope(private val parent: Scope? = null) {
    val depth: Int = if(parent == null) 0 else parent.depth + 1

    private val bindings = mutableMapOf<String, Binding>()
    private val includes: MutableSet<Scope> = if(parent?.includes == null) mutableSetOf() else parent.includes

    fun set(name: String, value: Node?, export: Boolean = false) {
        if (bindings[name] != null) throw IllegalStateException("Attempt to re-declare '$name'")
        bindings[name] = Binding(value, export)
    }

    operator fun set(name: String, value: Node?) = set(name, value, false)

    operator fun get(name: String, checkIncludes: Boolean = true): Binding? {
        if(name in bindings) return bindings[name]!!

        if(checkIncludes) {
            val v = includes.firstNotNullOfOrNull { it[name, false] }
            if(v != null && v.export) return v
        }

        return parent?.get(name)
    }
}


class WithScopes(val scope: Scope) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithScopes>

    internal var currentScope = scope
    internal val scopes = Stack<Scope>()
}

val WorkerScope.scope get() = withExt(WithScopes) { currentScope }

suspend fun <T> WorkerScope.withScope(s: Scope, action: suspend (Scope) -> T): T = withExt(WithScopes) {
    scopes.push(scope)
    currentScope = s
    val result = action(scope)
    currentScope = scopes.pop()
    result
}


suspend fun WorkerScope.lookupBinding(name: IdentST): Binding = with(WithScopes) { lookupBinding(name, scope) }

suspend fun WorkerScope.lookupBinding(name: IdentST, scope: Scope) = with(WithScopes) {
    val ws = this
    val binding = scope[name.value]
    if (binding == null) waitOn(name, NAME_BOUND::class) { ws.reportError("Undeclared identifier: $name") }
    scope[name.value]!!
}


val NAME_BOUND = object: Notification {}


fun bindNames(ast: FileST) = worker(WorkerName("scoping") + WithScopes(ast.scope)) {
    val nameBinder = object : ASTVisitor {
        override suspend fun visitLabel(n: LabelST): ExprST {
            scope[n.value] = LabelRefST(n.value)
            notifyOf(n, NAME_BOUND)
            return super.visitLabel(n)
        }

        override suspend fun visitDefine(n: DefineST): Node {
            scope[n.name.value] = n.value
            notifyOf(n.name, NAME_BOUND)
            return super.visitDefine(n)
        }

        override suspend fun visitMacro(n: MacroST): Node {
            scope[n.name.value] = n
            notifyOf(n.name, NAME_BOUND)
            return super.visitMacro(n)
        }

        override suspend fun visitInclude(n: IncludeST): Node = TODO()

        override suspend fun visitFile(n: FileST): Node = FileST(n.lexer, n.includes, n.body.map { visit(it) }.filter { it !is MacroST && it !is DefineST }, scope)
    }
    enqueueWorker(expandMacros(nameBinder.visit(ast) as FileST))
}