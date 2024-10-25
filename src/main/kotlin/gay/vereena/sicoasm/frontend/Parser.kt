package gay.vereena.sicoasm.frontend

import gay.vereena.sicoasm.*
import gay.vereena.sicoasm.util.*


class Parser(private val scope: WorkerScope, private val lexer: Lexer) {
    private class ParseException : Throwable()


    private val tokens = lexer.tokenize()
    private var index: Int = 0
    private var start: Int = 0
    private var reportErrors: Boolean = true


    private fun currentRange(): Array<Token> {
        val r = tokens(start, index)
        start = index
        return r
    }

    private fun current(): Token = tokens[index]

    private fun tokens(start: Int, end: Int) = tokens.subList(start, end).toTypedArray()

    private fun prev(): Token {
        index--
        return tokens[index]
    }

    private fun next(): Token {
        val t = current()
        index++
        return t
    }

    private fun more(): Boolean = index < tokens.size

    private fun accept(vararg valid: TokenType): Boolean {
        if (!more()) return false
        val cur = current()
        for (type in valid) {
            if (cur.type == type) {
                return true
            }
        }
        return false
    }

    private fun acceptNext(vararg valid: TokenType): Boolean {
        if (accept(*valid)) {
            next()
            return true
        }
        return false
    }

    private fun expect(vararg valid: TokenType): Token {
        val cur = current()
        if (accept(*valid)) return cur
        unexpected(false, *valid)
    }

    private fun expectNext(vararg valid: TokenType): Token {
        val cur = expect(*valid)
        next()
        return cur
    }

    private fun expectNot(vararg invalid: TokenType): Token {
        if (accept(*invalid)) unexpected(true, *invalid)
        return current()
    }

    private fun error(msg: String, vararg tokens: Token) {
        scope.reportFatal(lexer.formatError(msg, *tokens), true)
    }

    private fun unexpected(not: Boolean = false, expected: Maybe<String> = None()): Nothing {
        if (reportErrors) {
            val t = current()
            val es = expected.fold({ e ->
                val ns = if (not) "not " else ""
                "; expected $ns$e"
            }, { "" })
            scope.reportFatal(lexer.formatError("Unexpected token: '${t.value}'$es", t), true)
        } else {
            throw ParseException()
        }
    }

    private fun unexpected(not: Boolean, vararg expected: TokenType): Nothing {
        if (expected.isEmpty()) unexpected()
        else unexpected(not, Some(expected.joinToString(", ") { "$it" }))
    }

    private fun acceptIdent(value: String): Boolean {
        if(accept(TokenType.IDENT)) return current().value == value
        return false
    }

    private fun expectNextIdent(value: String): Token {
        val t = expectNext(TokenType.IDENT)
        if(t.value != value) unexpected(expected = Some(value))
        return t
    }

    private fun acceptPoundIdent(value: String): Boolean {
        if(accept(TokenType.POUND_IDENT)) return current().value == value
        return false
    }

    private fun expectPoundIdentNext(value: String): Token {
        val t = expectNext(TokenType.POUND_IDENT)
        if(t.value != value) unexpected(expected = Some(value))
        return t
    }

    // NOTE TODO: This is not reentrant!
    private fun <T> parseWithRetry(parse: () -> T, retry: (() -> T)? = null): Maybe<T> {
        assert(reportErrors)
        val oldIndex = index
        val oldStart = start
        return try {
            reportErrors = false
            Some(parse())
        } catch (_: ParseException) {
            reportErrors = true
            index = oldIndex
            start = oldStart
            if (retry != null) Some(retry())
            else None()
        } finally {
            reportErrors = true
        }
    }

    private fun parseString(): StringST {
        val t = expectNext(TokenType.STRING)
        return StringST(t.value, t, *currentRange())
    }

    private fun parseIdent(): IdentST = IdentST(expectNext(TokenType.IDENT).value, *currentRange())

    private fun parseInt(): IntST {
        return IntST(expectNext(TokenType.INT).value.toInt(), *currentRange()) // TODO
    }

    private fun parseAtom(): ExprST = when {
        accept(TokenType.INT) -> parseInt()
        accept(TokenType.IDENT) -> parseIdent()
        acceptNext(TokenType.POS) -> PosST(*currentRange())
        acceptNext(TokenType.NEXT) -> NextST(*currentRange())
        else -> unexpected()
    }

    private fun parseUnaryOp(): UnaryOP {
        return when {
            accept(TokenType.SUB) -> UnaryOP.NEG
            else -> ice()
        }
    }

    private fun parseUnary(): ExprST {
        if(accept(TokenType.SUB)) {
            val start = index

            val ops = mutableListOf<UnaryOP>()
            while(accept(TokenType.SUB)) {
                ops += parseUnaryOp()
                next()
            }

            var ret = parseAtom()
            for(i in (ops.size - 1)..0) {
                ret = UnaryST(ops[i], ret, *tokens(start, index))
            }

            this.start = index

            return ret
        }
        return parseAtom()
    }

    private fun parseBinaryOp(): BinaryOP {
        return when {
            accept(TokenType.ADD) -> BinaryOP.ADD
            accept(TokenType.SUB) -> BinaryOP.SUB
            accept(TokenType.MUL) -> BinaryOP.MUL
            accept(TokenType.DIV) -> BinaryOP.DIV
            else -> ice()
        }
    }

    private fun parseBinary(next: () -> ExprST, vararg types: TokenType): ExprST {
        val start = index
        var ret = next()

        while(more() && accept(*types)) {
            val op = parseBinaryOp()
            this.next()
            ret = BinaryST(op, ret, next(), *tokens(start, index))
        }

        this.start = index

        return ret
    }

    private fun parseBinaryMul(): ExprST = parseBinary(::parseUnary, TokenType.MUL, TokenType.DIV)

    private fun parseExpr(): ExprST = parseBinary(::parseBinaryMul, TokenType.ADD, TokenType.SUB)

    private fun parseInclude(): IncludeST {
        expectPoundIdentNext("include")
        return IncludeST(parseString().value, *currentRange())
    }

    private fun parseDefine(): DefST {
        expectPoundIdentNext("define")
        val name = parseIdent()
        val value = parseExpr()
        return DefST(name.value, value, *currentRange())
    }

    private fun parseMacroCall(): MacroCallST {
        val name = expectNext(TokenType.IDENT).value

        expectNext(TokenType.LPAREN)
        val args = mutableListOf<ExprST>()
        while(more() && !accept(TokenType.RPAREN)) {
            args += parseExpr()
            if(accept(TokenType.COMMA)) next()
            else expect(TokenType.RPAREN)
        }
        expectNext(TokenType.RPAREN)

        return MacroCallST(name, args, *currentRange())
    }

    private fun parseMacro(): MacroST {
        expectPoundIdentNext("macro")

        val name = expectNext(TokenType.IDENT).value

        expectNext(TokenType.LPAREN)
        val params = mutableListOf<String>()
        while(more() && !accept(TokenType.RPAREN)) {
            params += expectNext(TokenType.IDENT).value
            if(accept(TokenType.COMMA)) next()
            else expect(TokenType.RPAREN)
        }
        expectNext(TokenType.RPAREN)

        expectNext(TokenType.LBRACE)
        val body = mutableListOf<Node>()
        while(more() && !accept(TokenType.RBRACE)) {
            body += parse2()
        }
        expectNext(TokenType.RBRACE)

        return MacroST(name, params, body, *currentRange())
    }

    private fun parse2(): Node {
        if(accept(TokenType.IDENT)) {
            val st = parseWithRetry({
                val ident = expectNext(TokenType.IDENT).value
                if(acceptNext(TokenType.COLON)) {
                    LabelST(ident, *currentRange())
                } else if(accept(TokenType.LPAREN)) {
                    prev()
                    parseMacroCall()
                } else {
                    throw ParseException()
                }
            })
            if(st.isSome) {
                return st.get()
            }
        }
        return parseExpr()
    }

    fun parse(): FileST {
        val includes = mutableListOf<IncludeST>()
        val body = mutableListOf<Node>()
        while(more()) {
            when {
                acceptPoundIdent("include") -> includes += parseInclude()
                acceptPoundIdent("define") -> body += parseDefine()
                acceptPoundIdent("macro") -> body += parseMacro()
                else -> body += parse2()
            }
        }
        return FileST(lexer, includes, body)
    }
}

fun parse(name: String, path: String, code: String) = worker(WorkerName("parse")) {
    val lexer = Lexer(this, name, code)
    val parser = Parser(this, lexer)
    val ast = parser.parse()

    println(astToString(ast))

    TODO()
}