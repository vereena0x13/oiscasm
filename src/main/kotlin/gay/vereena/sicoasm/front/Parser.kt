package gay.vereena.sicoasm.front

import java.io.*

import gay.vereena.sicoasm.front.TokenType.*
import gay.vereena.sicoasm.mid.*
import gay.vereena.sicoasm.back.*
import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.util.*


class Parser(private val scope: WorkerScope, private val lexer: Lexer) {
    private class ParseException : Throwable()


    private val tokens = lexer.tokenize()
    private var index: Int = 0
    private var reportErrors: Boolean = true

    private val fileScope = Scope()


    private fun current(): Token = tokens[index]

    private fun prev(): Token {
        index--
        return current()
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
        return valid.any { t -> cur.type == t }
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

    private fun unexpected(not: Boolean = false, expected: String? = null): Nothing {
        if (reportErrors) {
            val t = current()
            val es = if(expected != null) {
                val ns = if(not) "not " else ""
                "; expected $ns${expected}"
            } else { "" }
            scope.reportFatal(lexer.formatError("Unexpected token: '${t.value}'$es", t), true)
        } else {
            throw ParseException()
        }
    }

    private fun unexpected(not: Boolean, vararg expected: TokenType): Nothing {
        if (expected.isEmpty()) unexpected()
        else unexpected(not, expected.joinToString(", ") { "$it" })
    }

    private fun acceptIdent(value: String): Boolean {
        if(accept(IDENT)) return current().value == value
        return false
    }

    private fun expectIdentNext(value: String): Token {
        val t = expectNext(IDENT)
        if(t.value != value) unexpected(expected = value)
        return t
    }

    // NOTE TODO: This is not reentrant!
    private fun <T> parseWithRetry(parse: () -> T, retry: (() -> T)? = null): T? {
        assert(reportErrors)
        val oldIndex = index
        return try {
            reportErrors = false
            parse()
        } catch (_: ParseException) {
            reportErrors = true
            index = oldIndex
            if (retry != null) retry()
            else null
        } finally {
            reportErrors = true
        }
    }

    private fun parseString(): StringST = StringST(expectNext(STRING).value)

    private fun parseIdent(): IdentST = IdentST(expectNext(IDENT).value)

    private fun parseInt(): IntST {
        return IntST(expectNext(INT).value.toInt()) // TODO
    }

    private fun parseAtom(): ExprST = when {
        accept(INT) -> parseInt()
        accept(IDENT) -> when(current().value) {
            "true" -> BoolST(true).also { next() }
            "false" -> BoolST(false).also { next() }
            else -> parseIdent()
        }
        acceptNext(POS) -> PosST
        acceptNext(NEXT) -> NextST
        acceptNext(LPAREN) -> parseExpr().also { expectNext(RPAREN) }
        else -> unexpected()
    }

    private fun parseUnaryOp(): UnaryOP {
        return when {
            accept(SUB) -> UnaryOP.NEG
            else -> ice()
        }
    }

    private fun parseUnary(): ExprST {
        if(accept(SUB)) {
            val ops = mutableListOf<UnaryOP>()
            while(accept(SUB)) {
                ops += parseUnaryOp()
                next()
            }

            var ret = parseAtom()
            for(i in (ops.size - 1)..0) {
                ret = UnaryST(ops[i], ret)
            }

            return ret
        }
        return parseAtom()
    }

    private fun parseBinaryOp(): BinaryOP {
        return when {
            accept(ADD) -> BinaryOP.ADD
            accept(SUB) -> BinaryOP.SUB
            accept(MUL) -> BinaryOP.MUL
            accept(DIV) -> BinaryOP.DIV
            else -> ice()
        }
    }

    private fun parseBinary(next: () -> ExprST, vararg types: TokenType): ExprST {
        var ret = next()

        while(more() && accept(*types)) {
            val op = parseBinaryOp()
            this.next()
            ret = BinaryST(op, ret, next())
        }

        return ret
    }

    private fun parseBinaryMul(): ExprST = parseBinary(::parseUnary, MUL, DIV)

    private fun parseExpr(): ExprST = parseBinary(::parseBinaryMul, ADD, SUB)

    private fun parseInclude(): IncludeST {
        expectIdentNext("include")
        return IncludeST(parseString().value)
    }

    private fun parseDefine(): DefineST {
        expectIdentNext("define")
        val name = parseIdent()
        val value = parseExpr()
        return DefineST(name, value)
    }

    private fun parseMacroCall(): MacroCallST {
        val name = parseIdent()

        expectNext(LPAREN)
        val args = mutableListOf<ExprST>()
        while(more() && !accept(RPAREN)) {
            args += parseExpr()
            if(accept(COMMA)) next()
            else expect(RPAREN)
        }
        expectNext(RPAREN)

        return MacroCallST(name, args)
    }

    private fun parseMacro(): MacroST {
        expectIdentNext("macro")

        val name = parseIdent()

        expectNext(LPAREN)
        val params = mutableListOf<String>()
        while(more() && !accept(RPAREN)) {
            params += expectNext(IDENT).value
            if(accept(COMMA)) next()
            else expect(RPAREN)
        }
        expectNext(RPAREN)

        expectNext(LBRACE)
        val body = mutableListOf<Node>()
        while(more() && !accept(RBRACE)) {
            body += parse2()
        }
        expectNext(RBRACE)

        return MacroST(name, params, body, Scope(fileScope))
    }

    // TODO: think of a name for this
    private fun parse2(): Node {
        if(acceptNext(IDENT)) {
            val st = parseWithRetry({
                if(accept(LPAREN)) {
                    prev()
                    parseMacroCall()
                } else {
                    throw ParseException()
                }
            })
            if(st != null) return st
            prev()
        }

        return when {
            accept(LABEL) -> LabelST(expectNext(LABEL).value)
            else -> parseExpr()
        }
    }

    fun parse(): FileST {
        val includes = mutableListOf<IncludeST>()
        val body = mutableListOf<Node>()
        while(more()) {
            when {
                acceptIdent("include") -> includes += parseInclude()
                acceptIdent("define") -> body += parseDefine()
                acceptIdent("macro") -> body += parseMacro()
                else -> body += parse2()
            }
        }
        return FileST(lexer, includes, body, fileScope)
    }
}

fun parse(file: File, outFile: File? = null) = worker(WorkerName("parse")) {
    val lexer = Lexer(this, file.name, file.readText())
    val parser = Parser(this, lexer)
    val ast = parser.parse()

    println("parsedAst:\n${astToString(ast)}")

    enqueueWorker(bindNames(ast))
}