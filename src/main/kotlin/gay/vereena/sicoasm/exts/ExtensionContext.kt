package gay.vereena.sicoasm.exts

interface ExtensionContext {
    interface Key<E : Element>

    operator fun <E : Element> get(key: Key<E>): E?

    fun <R> fold(initial: R, operation: (R, Element) -> R): R

    operator fun plus(ctx: ExtensionContext): ExtensionContext {
        if (ctx == Empty) return this
        return ctx.fold(this) { acc, element ->
            val nacc = acc without element.key
            if (nacc == Empty) element
            else Combined(acc, element)
        }
    }

    infix fun without(key: Key<*>): ExtensionContext

    interface Element : ExtensionContext {
        val key: Key<*>

        override operator fun <E : Element> get(key: Key<E>): E? =
            @Suppress("UNCHECKED_CAST")
            if (key == this.key) this as E else null

        override fun <R> fold(initial: R, operation: (R, Element) -> R): R =
            operation(initial, this)

        override infix fun without(key: Key<*>): ExtensionContext =
            if (key == this.key) Empty else this
    }

    object Empty : ExtensionContext {
        override fun <E : Element> get(key: Key<E>): E? = null
        override fun <R> fold(initial: R, operation: (R, Element) -> R): R = initial
        override infix fun without(key: Key<*>): ExtensionContext = this

        override fun toString() = "Empty"
    }

    class Combined internal constructor(private val left: ExtensionContext, private val element: Element) : ExtensionContext {
        override fun <E : Element> get(key: Key<E>): E? {
            var cur = this
            while (true) {
                cur.element[key]?.let { return it }
                val next = cur.left
                if (next is Combined) cur = next
                else return next[key]
            }
        }

        override fun <R> fold(initial: R, operation: (R, Element) -> R): R =
            operation(left.fold(initial, operation), element)

        override fun without(key: Key<*>): ExtensionContext {
            element[key]?.let { return left }
            val newLeft = left without key
            return when {
                newLeft === left -> this
                newLeft === Empty -> element
                else -> Combined(newLeft, element)
            }
        }

        override fun toString(): String {
            val sb = StringBuilder()

            sb.append('[')
            sb.append(element.toString())
            sb.append(", ")

            var curr = left
            while(curr is Combined) {
                sb.append(curr.element.toString())

                curr = curr.left
                sb.append(", ")
            }

            sb.append(curr.toString())
            sb.append(']')

            return sb.toString()
        }
    }

    interface IKey<E : Element> : Key<E>

    abstract class AbstractElement(override val key: Key<*>) : Element {
        override fun toString(): String = this::class.simpleName ?: "<unknown>"
    }
}

inline fun <T, E: ExtensionContext.AbstractElement> ExtensionContext.with(key: ExtensionContext.IKey<E>, block: (E) -> T): T {
    val ext = get(key) ?: error("Extension not found: $key")
    return block(ext)
}