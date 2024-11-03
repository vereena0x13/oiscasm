package gay.vereena.sicoasm.back

data class Label(val id: Int, var addr: Int? = null)

class Assembler {
    private data class Patch(val addr: Int, val label: Int)

    private val code = mutableListOf<Int>()
    private val labels = mutableListOf<Label>()
    private val patches = mutableListOf<Patch>()

    private fun markPatch(l: Label) { patches += Patch(pos(), l.id) }

    fun emit(x: Int) { code += x }

    fun pos() = code.size

    fun label(): Label {
        val l = Label(labels.size)
        labels += l
        return l
    }

    fun mark(l: Label): Label {
        assert(l.addr == null)
        l.addr = pos()
        return l
    }

    fun word(l: Label): Label {
        if(l.addr == null) {
            markPatch(l)
            emit(0)
        } else {
            emit(l.addr!!)
        }
        return l
    }

    fun assemble(): IntArray {
        patches.forEach { code[it.addr] = labels[it.label].addr!! }
        return code.toIntArray()
    }
}