package gay.vereena.oiscasm.util

sealed class Either<A, B> {
    fun swap(): Either<B, A> = when (this) {
        is Left -> Right(a)
        is Right -> Left(b)
    }

    inline fun <R> fold(
        crossinline left: (A) -> R,
        crossinline right: (B) -> R
    ): R = when (this) {
        is Left -> left(a)
        is Right -> right(b)
    }

    inline fun <B2> map(
        crossinline fn: (B) -> B2
    ): Either<A, B2> = when (this) {
        is Left -> Left(a)
        is Right -> Right(fn(b))
    }

    inline fun <B2> flatMap(
        crossinline fn: (B) -> Either<A, B2>
    ): Either<A, B2> = when (this) {
        is Left -> Left(a)
        is Right -> fn(b)
    }
}

data class Left<A, B>(val a: A) : Either<A, B>()
data class Right<A, B>(val b: B) : Either<A, B>()