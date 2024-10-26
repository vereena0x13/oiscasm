package gay.vereena.sicoasm.util

import java.lang.IllegalStateException

class StackUnderflowException : Exception()

class Stack<T> {
    private val data = mutableListOf<T>()
    private val marks = mutableListOf<Int>()

    fun push(x: T) {
        data.add(x)
    }

    fun peek(): T {
        if(data.isEmpty()) throw StackUnderflowException()
        return data[data.size - 1]
    }

    fun pop(): T {
        if(data.isEmpty()) throw StackUnderflowException()
        return data.removeAt(data.size - 1)
    }

    //fun popMaybe(): Maybe<T> = if(data.isEmpty()) None() else Some(pop())
    fun popMaybe(): T? = if(data.isEmpty()) null else pop()

    fun mark() { marks.add(data.size) }

    fun reset(): List<T> {
        if(marks.isEmpty()) throw IllegalStateException("Attempt to reset Stack with no marks")
        val mark = marks.removeAt(marks.size - 1)
        if(data.size < mark) throw IllegalStateException("Attempt to reset Stack to invalid mark")
        val xs = mutableListOf<T>()
        while(data.size != mark) xs.add(pop())
        return xs.reversed()
    }

    fun marks() = marks.size

    fun clear() {
        data.clear()
        marks.clear()
    }

    val size: Int
        get() = data.size

    fun isEmpty() = data.isEmpty()
    fun isNotEmpty() = data.isNotEmpty()

    fun rawData() = data.toList()
}