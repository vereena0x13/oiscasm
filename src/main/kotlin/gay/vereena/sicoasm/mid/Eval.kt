package gay.vereena.sicoasm.mid

import kotlin.math.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.util.*


sealed class Value {
    private inline fun <reified T: Value> check(): T {
        if(this !is T) panic() // TODO: actual error reporting
        return this
    }

    fun checkInt() = check<IntValue>().value
    fun checkBool() = check<BoolValue>().value
    fun checkString() = check<StringValue>().value

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


interface EvalContext {
    fun pos(): Int
    fun labelAddr(name: String): Int?
}

data object ComputeLater : Exception() {
    private fun readResolve(): Any = ComputeLater
}

suspend fun WorkerScope.eval(n: ExprST, ctx: EvalContext?): Value = with(WithScopes) {
    return when(n) {
        is DeferredST -> ice()
        is EmptyExprST -> ice()
        is IntST -> IntValue(n.value)
        is StringST -> StringValue(n.value)
        is IdentST -> eval(lookupBinding(n.value).value as ExprST, ctx) // NOTE TODO: don't just cast to ExprST
        is BoolST -> BoolValue(n.value)
        is LabelRefST -> {
            val addr = ctx!!.labelAddr(n.value)
            if(addr != null) return IntValue(addr)
            throw ComputeLater
        }
        is UnaryST -> when(n.op) {
            UnaryOP.NEG -> IntValue(-eval(n.value, ctx).checkInt())
            UnaryOP.BIT_NOT -> IntValue(eval(n.value, ctx).checkInt().inv())
            UnaryOP.NOT -> BoolValue(!eval(n.value, ctx).checkBool())
        }
        is BinaryST -> {
            val left = eval(n.left, ctx)
            val right = eval(n.right, ctx)
            when(n.op) {
                BinaryOP.ADD -> Binary()
                    .binary<IntValue, IntValue>(left, right) { l, r, _ -> IntValue(l.value + r.value) }
                    .binary<StringValue, StringValue>(left, right) { l, r, _ -> StringValue(l.value + r.value) }
                    .fold({ it }, { ice() })
                BinaryOP.SUB -> IntValue(left.checkInt() - right.checkInt())
                BinaryOP.MUL -> IntValue(left.checkInt() * right.checkInt())
                BinaryOP.DIV -> IntValue(left.checkInt() / right.checkInt())
                BinaryOP.MOD -> IntValue(left.checkInt() % right.checkInt())
                BinaryOP.POW -> IntValue(left.checkInt().toDouble().pow(right.checkInt()).toInt())
                BinaryOP.BIT_AND -> IntValue(left.checkInt() and right.checkInt())
                BinaryOP.BIT_OR -> IntValue(left.checkInt() or right.checkInt())
                BinaryOP.BIT_XOR -> IntValue(left.checkInt() xor right.checkInt())
                BinaryOP.SHL -> IntValue(left.checkInt() shl right.checkInt())
                BinaryOP.SHR -> IntValue(left.checkInt() shr right.checkInt())
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
                BinaryOP.LT -> BoolValue(left.checkInt() < right.checkInt())
                BinaryOP.GT -> BoolValue(left.checkInt() > right.checkInt())
                BinaryOP.LTE -> BoolValue(left.checkInt() <= right.checkInt())
                BinaryOP.GTE -> BoolValue(left.checkInt() >= right.checkInt())
                BinaryOP.AND -> BoolValue(left.checkBool() && right.checkBool())
                BinaryOP.OR -> BoolValue(left.checkBool() || right.checkBool())
            }
        }
        is PosST -> IntValue(ctx!!.pos())
        is ParenST -> eval(n.value, ctx)
        is BlankST -> {
            val value = if(n.value is IdentST) scope[n.value.value]?.value else n.value
            BoolValue(value == null || value is EmptyExprST)
        }
    }
}