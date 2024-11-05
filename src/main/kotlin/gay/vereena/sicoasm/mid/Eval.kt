package gay.vereena.sicoasm.mid

import kotlin.math.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.util.*


sealed class Value {
    inline fun <reified T: Value> check(): T {
        if(this !is T) panic() // TODO: actual error reporting
        return this
    }

    abstract fun toAST(): ExprST
}

data class IntValue(val value: Int) : Value() {
    override fun toAST() = IntST(value)
}

data class BoolValue(val value: Boolean) : Value() {
    override fun toAST() = BoolST(value)
}

data class StringValue(val value: String) : Value() {
    override fun toAST() = StringST(value)
}


private class Binary

private inline fun <reified L : Value, reified R : Value> Either<Value, Binary>.binary(
    a: Value, b: Value, commutative: Boolean = true,
    crossinline block: (L, R, Boolean) -> Value
): Either<Value, Binary> = this.flatMap { bldr -> bldr.binary(a, b, commutative, block) }

private inline fun <reified L : Value, reified R : Value> Binary.binary(
    l: Value, r: Value, commutative: Boolean = true,
    block: (L, R, Boolean) -> Value
): Either<Value, Binary> {
    return when {
        l is L && r is R -> Left(block(l, r, false))
        commutative && (L::class != R::class) && l is R && r is L -> Left(block(r, l, true))
        else -> Right(this)
    }
}


suspend fun WorkerScope.eval(n: ExprST, pos: (() -> Int)?): Value = with(WithScopes) {
    return when(n) {
        is IntST -> IntValue(n.value)
        is StringST -> StringValue(n.value)
        is IdentST -> eval(lookupBinding(n).value as ExprST, pos) // NOTE TODO: don't just cast to ExprST
        is BoolST -> BoolValue(n.value)
        is LabelRefST -> TODO()
        is UnaryST -> when(n.op) {
            UnaryOP.NEG -> IntValue(-eval(n.value, pos).check<IntValue>().value)
            UnaryOP.BIT_NOT -> IntValue(eval(n.value, pos).check<IntValue>().value.inv())
            UnaryOP.NOT -> BoolValue(!eval(n.value, pos).check<BoolValue>().value)
        }
        is BinaryST -> {
            val left = eval(n.left, pos)
            val right = eval(n.right, pos)
            when(n.op) {
                BinaryOP.ADD -> Binary()
                    .binary<IntValue, IntValue>(left, right) { l, r, _ -> IntValue(l.value + r.value) }
                    .binary<StringValue, StringValue>(left, right) { l, r, _ -> StringValue(l.value + r.value) }
                    .fold({ it }, { ice() })
                BinaryOP.SUB -> IntValue(left.check<IntValue>().value - right.check<IntValue>().value)
                BinaryOP.MUL -> IntValue(left.check<IntValue>().value * right.check<IntValue>().value)
                BinaryOP.DIV -> IntValue(left.check<IntValue>().value / right.check<IntValue>().value)
                BinaryOP.MOD -> IntValue(left.check<IntValue>().value % right.check<IntValue>().value)
                BinaryOP.POW -> IntValue(left.check<IntValue>().value.toDouble().pow(right.check<IntValue>().value).toInt())
                BinaryOP.BIT_AND -> IntValue(left.check<IntValue>().value and right.check<IntValue>().value)
                BinaryOP.BIT_OR -> IntValue(left.check<IntValue>().value or right.check<IntValue>().value)
                BinaryOP.BIT_XOR -> IntValue(left.check<IntValue>().value xor right.check<IntValue>().value)
                BinaryOP.SHL -> IntValue(left.check<IntValue>().value shl right.check<IntValue>().value)
                BinaryOP.SHR -> IntValue(left.check<IntValue>().value shr right.check<IntValue>().value)
                BinaryOP.EQ -> Binary()
                    .binary<IntValue, IntValue>(left, right) { l, r, _ -> BoolValue(l.value == r.value) }
                    .binary<StringValue, StringValue>(left, right) { l, r, _ -> BoolValue(l.value == r.value) }
                    .binary<BoolValue, BoolValue>(left, right) { l, r, _ -> BoolValue(l.value == r.value) }
                    .fold({ it }, { ice() })
                BinaryOP.NE -> Binary()
                    .binary<IntValue, IntValue>(left, right) { l, r, _ -> BoolValue(l.value != r.value) }
                    .binary<StringValue, StringValue>(left, right) { l, r, _ -> BoolValue(l.value != r.value) }
                    .binary<BoolValue, BoolValue>(left, right) { l, r, _ -> BoolValue(l.value != r.value) }
                    .fold({ it }, { ice() })
                BinaryOP.LT -> BoolValue(left.check<IntValue>().value < right.check<IntValue>().value)
                BinaryOP.GT -> BoolValue(left.check<IntValue>().value > right.check<IntValue>().value)
                BinaryOP.LTE -> BoolValue(left.check<IntValue>().value <= right.check<IntValue>().value)
                BinaryOP.GTE -> BoolValue(left.check<IntValue>().value >= right.check<IntValue>().value)
                BinaryOP.AND -> BoolValue(left.check<BoolValue>().value && right.check<BoolValue>().value)
                BinaryOP.OR -> BoolValue(left.check<BoolValue>().value || right.check<BoolValue>().value)
            }
        }
        is PosST -> IntValue(pos!!())
        is ParenST -> eval(n.value, pos)
    }
}