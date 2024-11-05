package gay.vereena.sicoasm.front

import kotlin.math.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.util.*

import gay.vereena.sicoasm.front.TokenType.*


enum class TokenType {
    LBRACE,                 // {
    RBRACE,                 // }
    LBRACK,                 // [
    RBRACK,                 // ]
    LPAREN,                 // (
    RPAREN,                 // )

    COMMA,                  // ,
    COLON,                  // :

    SUB,                    // -
    ADD,                    // +
    MUL,                    // *
    DIV,                    // /
    MOD,                    // %
    POW,                    // **

    BIT_NOT,                // ~
    NOT,                    // !

    BIT_AND,                // &
    BIT_OR,                 // |
    BIT_XOR,                // ^

    SHL,                    // <<
    SHR,                    // >>

    EQ,                     // ==
    NE,                     // !=
    LT,                     // <
    GT,                     // >
    LTE,                    // <=
    GTE,                    // >=

    AND,                    // &&
    OR,                     // ||

    POS,                    // ?

    STRING,                 // "..."
    INT,                    // [0-9]*
    IDENT,                  // ident
    DIRECTIVE,              // .ident
    LABEL,                  // ident:
}


data class Span(val start: Int, val end: Int = start) {
    init {
        if (start > end) throw IllegalArgumentException("Span start must be <= end; got [$start,$end]")
    }

    val range = start..end

    infix fun union(other: Span) = Span(min(start, other.start), max(end, other.end))
    infix fun overlaps(other: Span) = other.start in range || other.end in range
    override fun toString() = "[$start,$end]"
}


data class Token(val lexer: Lexer, val index: Int, val type: TokenType, val value: String, val line: Int, val col: Int, val span: Span)


class LexException(msg: String) : Exception(msg)


class Lexer(private val scope: WorkerScope, private val file: String, private val source: String) {
    private fun isLetter(c: Char) = (c in 'a'..'z') || (c in 'A'..'Z')
    private fun isDigit(c: Char) = c in '0'..'9'
    private fun isIdent(c: Char) = isLetter(c) || isDigit(c) || c == '_'


    private var start: Int = 0
    private var pos: Int = 0
    private var line: Int = 1
    private var col: Int = 1
    private var tokens = mutableListOf<Token>()
    private var tokenIndex: Int = 0
    private val index2Line = mutableMapOf<Int, Int>() // NOTE TODO: This is kinda dumb...


    init {
        var line = 1
        source.indices.forEach { i ->
            if (source[i] == '\n') line++
            index2Line[i] = line
        }
    }


    private fun more() = pos < source.length

    private fun peek(): Char = source[pos]

    private fun next(): Char {
        val c = peek()
        pos++
        col++
        return c
    }

    private fun current(): String = source.substring(start, pos)

    private fun accept(vararg valid: Char): Boolean {
        if(!more()) return false
        return if(valid.contains(peek())) { next(); true } else false
    }

    private fun acceptSeq(seq: String): Boolean {
        if(!more()) return false
        val pos = pos
        val col = col
        for (c in seq) {
            if (next() != c) {
                this.pos = pos
                this.col = col
                return false
            }
        }
        return true
    }

    private fun acceptRun(valid: String) { while(more() && valid.contains(peek())) next() }

    private fun emit(type: TokenType, str: String? = null, col: Int = 0, span: Span? = null) {
        val s = str ?: current()
        val sp = span ?: span()
        tokens.add(Token(this, tokenIndex++, type, s, line, if (col == 0) this.col - s.length else col, sp))
        start = pos
    }

    private fun ignore() { start = pos }

    private fun unexpected(c: String): Nothing = scope.reportFatal(
        formatError(
            "Unexpected: '${unescape(c)}'",
            Token(this, -1, IDENT, c, line, col - 1, Span(pos - 1)) // TODO
        ),
        true
    )

    private fun skipWhitespace() {
        while (more()) {
            when (peek()) {
                '\n' -> {
                    line++
                    col = 0
                    next()
                }
                ' ', '\t', '\r' -> next()
                else -> break
            }
        }
        ignore()
    }

    private fun scanIdent() { while (more() && isIdent(peek())) next() }

    fun tokenize(): List<Token> {
        while (more()) {
            skipWhitespace()

            if (!more()) break

            when (val c = next()) {
                '{' -> emit(LBRACE)
                '}' -> emit(RBRACE)
                '[' -> emit(LBRACK)
                ']' -> emit(RBRACK)
                '(' -> emit(LPAREN)
                ')' -> emit(RPAREN)

                ',' -> emit(COMMA)
                ':' -> emit(COLON)

                '+' -> emit(ADD)
                '-' -> emit(SUB)
                '*' -> when {
                    accept('*') -> emit(POW)
                    else -> emit(MUL)
                }
                '/' -> when {
                    accept('/') -> {
                        while(more() && peek() != '\n') next()
                        ignore()
                    }
                    accept('*') -> {
                        var depth = 1
                        while(more() && depth > 0) {
                            when {
                                acceptSeq("/*") -> depth++
                                acceptSeq("*/") -> depth--
                                else -> if(next() == '\n') line++
                            }
                        }
                        assert(depth == 0)
                        ignore()
                    }
                    else -> emit(DIV)
                }
                '%' -> emit(MOD)

                '~' -> emit(BIT_NOT)

                '&' -> when {
                    accept('&') -> emit(AND)
                    else -> emit(BIT_AND)
                }
                '|' -> when {
                    accept('}') -> emit(OR)
                    else -> emit(BIT_OR)
                }
                '^' -> emit(BIT_XOR)

                '<' -> when {
                    accept('<') -> emit(SHL)
                    accept('=') -> emit(LTE)
                    else -> emit(LT)
                }
                '>' -> when {
                    accept('>') -> emit(SHR)
                    accept('=') -> emit(GTE)
                    else -> emit(GT)
                }

                '=' -> when {
                    accept('=') -> emit(EQ)
                    else -> unexpected(c.toString())
                }
                '!' -> when {
                    accept('=') -> emit(NE)
                    else -> emit(NOT)
                }

                '?' -> emit(POS)

                '"' -> {
                    val start = pos
                    val col = col

                    val sb = StringBuilder()
                    while (more() && peek() != '"' && peek() != '\n') {
                        if(accept('\\')) {
                            when {
                                accept('\\') -> sb.append('\\')
                                accept('"') -> sb.append('"')
                                accept('t') -> sb.append('\t')
                                accept('b') -> sb.append('\b')
                                accept('n') -> sb.append('\n')
                                accept('r') -> sb.append('\r')
                                accept('u') -> {
                                    val cb = StringBuilder()
                                    (0..<4).forEach { _ -> cb.append(next()) }
                                    sb.append(cb.toString().toInt(16).toChar())
                                }
                            }
                        } else {
                            sb.append(next())
                        }
                    }

                    if (!accept('"')) throw LexException("Unclosed string")

                    val end = pos - 1
                    emit(STRING, sb.toString(), col, Span(start, end - 1))
                }

                else -> when {
                    isLetter(c) || c == '_' -> {
                        scanIdent()
                        if(accept(':')) emit(LABEL, source.substring(start, pos - 1))
                        else emit(IDENT)
                    }
                    c == '.' -> {
                        scanIdent()
                        emit(DIRECTIVE, source.substring(start + 1, pos))
                    }
                    isDigit(c) -> {
                        if(c == '0' && (peek() == 'x' || peek() == 'b')) when {
                            accept('x') -> acceptRun("0123456789abcdefABCDEF")
                            accept('b') -> acceptRun("01")
                            else -> unexpected(peek().toString())
                        } else while (more() && isDigit(peek())) next()
                        emit(INT, current())
                    }
                    else -> unexpected(c.toString())
                }
            }
        }

        return this.tokens
    }

    fun indexToLine(index: Int) = index2Line[index]

    fun span() = Span(start, if (pos == start) pos else pos - 1)

    fun getInSpan(span: Span) = source.substring(span.start, span.end)

    fun getLine(index: Int): String {
        assert(source[index] != '\n')

        var s = index
        while (true) {
            if (s == 0 || source[s - 1] == '\n') break
            s--
        }

        var e = index
        while (true) {
            if (e + 1 >= source.length || source[e + 1] == '\n') break
            e++
        }

        return source.substring(s, e + 1)
    }

    fun getLinesInSpan(span: Span): List<String> {
        assert(span.end < source.length)

        val result = mutableListOf<String>()

        var last = 0
        (span.start..span.end).forEach { i ->
            if (source[i] != '\n') {
                val curr = indexToLine(i)!!
                if (last != curr) {
                    val line = getLine(i)
                    result += line
                    last = curr
                }
            }
        }

        return result
    }

    fun formatError(error: String, vararg ts: Token): String {
        ts.sortBy { it.index }
        assert(ts.isNotEmpty())
        val result = StringBuilder()
        val theSpan = ts.map { it.span }.reduce { acc, it -> it union acc }
        val lines = getLinesInSpan(theSpan)
        val rawlinenos = ts.flatMap { (it.span.start..it.span.end).map { i -> indexToLine(i)!! } }.distinct()
        assert(lines.size == rawlinenos.size)
        assert(lines.isNotEmpty())
        result.append("${boldRed("error:")} $error\n")
        result.append("   ${boldBlue("-->")} $file\n")
        result.append("    ${boldBlue("|")}\n")
        lines.zip(rawlinenos).forEach { (line, rawlineno) ->
            val lineno = rawlineno.toString().padStart(3, ' ')
            result.append("${boldBlue("$lineno |")} $line\n")
        }
        result.append("    ${boldBlue("|")}")
        if (ts.size == 1) {
            val col = ts.fold(ts[0].col) { acc, it -> min(acc, it.col) }
            repeat(col) { result.append(' ') }
            val span = ts.fold(ts[0].span) { acc, it -> acc union it.span }
            result.append(boldRed(span.range.joinToString("") { "^" }))
        }
        result.append("\u001b[0m")
        return result.toString()
    }
}
