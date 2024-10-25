package gay.vereena.sicoasm.util

sealed class Maybe<A> {
    fun get(): A = when (this) {
        is Some -> a
        is None -> panic("attempt to unwrap None")
    }

    fun getOrNull(): A? = when (this) {
        is Some -> a
        is None -> null
    }

    inline fun <R> fold(
        crossinline some: (A) -> R,
        crossinline none: () -> R
    ): R = when (this) {
        is Some -> some(a)
        is None -> none()
    }

    inline fun orElse(
        crossinline fn: () -> Maybe<A>
    ): Maybe<A> = when (this) {
        is Some -> this
        is None -> fn()
    }

    inline fun getOrElse(
        crossinline fn: () -> A
    ): A = when (this) {
        is Some -> a
        is None -> fn()
    }

    inline fun <B> map(
        crossinline fn: (A) -> B
    ): Maybe<B> = when (this) {
        is Some -> Some(fn(a))
        is None -> None()
    }

    inline fun <B> flatMap(
        crossinline fn: (A) -> Maybe<B>
    ): Maybe<B> = when (this) {
        is Some -> fn(a)
        is None -> None()
    }

    inline fun filter(
        crossinline fn: (A) -> Boolean
    ): Maybe<A> = when (this) {
        is Some -> if (fn(a)) this else None()
        is None -> this
    }

    abstract val isSome: Boolean
    abstract val isNone: Boolean
}

data class Some<A>(val a: A) : Maybe<A>() {
    override val isSome = true
    override val isNone = false
}

class None<A> : Maybe<A>() {
    override val isSome = false
    override val isNone = true

    override fun toString() = "None()"
}
