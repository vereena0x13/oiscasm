package gay.vereena.sicoasm.front

import java.io.*

import gay.vereena.sicoasm.front.TokenType.*
import gay.vereena.sicoasm.mid.*
import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.util.*


class Parser(private val scope: WorkerScope, private val lexer: Lexer) {
    private class ParseException : Throwable()


    private val tokens = lexer.tokenize()
    private var index: Int = 0
    private var reportErrors: Boolean = true


    private var currentScope = Scope()

    private fun <T> pushScope(action: (Scope) -> T): T {
        currentScope = Scope(currentScope)
        val result = action(currentScope)
        currentScope = currentScope.parent!!
        return result
    }

    private fun current(): Token = tokens[index]

    private fun prev(): Token {
        index--
        return current()
    }

    private fun next() = current().also { index++ }

    private fun more(): Boolean = index < tokens.size

    private fun accept(vararg valid: TokenType): Boolean {
        if (!more()) return false
        val cur = current()
        return valid.any { t -> cur.type == t }
    }

    private fun acceptNext(vararg valid: TokenType) = if(accept(*valid)) { next(); true } else false

    private fun expect(vararg valid: TokenType): Token {
        if (accept(*valid)) return current()
        unexpected(false, *valid)
    }

    private fun expectNext(vararg valid: TokenType) = expect(*valid).also { next() }

    private fun unexpected(not: Boolean = false, expected: String? = null): Nothing {
        if (reportErrors) {
            val t = current()
            val es = if (expected != null) {
                val ns = if (not) "not " else ""
                "; expected $ns${expected}"
            } else {
                ""
            }
            Throwable().printStackTrace() // NOTE: remove for non-debug
            scope.reportFatal(lexer.formatError("Unexpected token: '${t.value}' (${t.type})$es", t), true)
        } else {
            throw ParseException()
        }
    }

    private fun unexpected(not: Boolean, vararg expected: TokenType): Nothing {
        if (expected.isEmpty()) unexpected()
        else unexpected(not, expected.joinToString(", ") { "$it" })
    }

    private fun acceptDirective(value: String) = accept(DIRECTIVE) && current().value == value

    private fun acceptDirectiveNext(value: String): Boolean {
        if(acceptDirective(value)) {
            next()
            return true
        }
        return false
    }

    private fun expectDirectiveNext(value: String): Token {
        val t = expectNext(DIRECTIVE)
        if (t.value != value) unexpected(expected = value)
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
            if (retry != null) retry() else null
        } finally {
            reportErrors = true
        }
    }

    private fun parseInt(): IntST {
        val v = expectNext(INT).value
        return IntST(when {
            v.startsWith("0x") -> v.substring(2).toInt(16)
            v.startsWith("0b") -> v.substring(2).toInt(2)
            else -> v.toInt()
        })
    }

    private fun parseIdent() = IdentST(expectNext(IDENT).value)

    private fun parseString() = StringST(expectNext(STRING).value)

    private fun parseBlank(): BlankST {
        expectDirectiveNext("blank")
        expectNext(LPAREN)
        return BlankST(parseExpr()).also { expectNext(RPAREN) }
    }

    private fun parseAtom() = when {
        accept(INT) -> parseInt()
        accept(IDENT) -> when (current().value) {
            "true" -> BoolST(true).also { next() }
            "false" -> BoolST(false).also { next() }
            else -> parseIdent()
        }
        accept(STRING) -> parseString()
        acceptDirective("blank") -> parseBlank()
        acceptNext(POS) -> PosST
        acceptNext(LPAREN) -> parseExpr().also { expectNext(RPAREN) }
        else -> unexpected()
    }

    private fun parsePower() = parseBinary(::parseAtom, POW)

    private fun parseUnaryOp() = when {
        accept(SUB) -> UnaryOP.NEG
        accept(BIT_NOT) -> UnaryOP.BIT_NOT
        accept(NOT) -> UnaryOP.NOT
        else -> ice()
    }

    private fun parseUnary(): ExprST {
        if (accept(SUB, BIT_NOT, NOT)) {
            val ops = mutableListOf<UnaryOP>()
            while (more() && accept(SUB, BIT_NOT, NOT)) {
                ops += parseUnaryOp()
                next()
            }

            var ret = parsePower()
            for (i in (ops.size - 1)..0) ret = UnaryST(ops[i], ret)

            return ret
        }
        return parsePower()
    }

    private fun parseBinaryOp() = when {
        accept(ADD) -> BinaryOP.ADD
        accept(SUB) -> BinaryOP.SUB
        accept(MUL) -> BinaryOP.MUL
        accept(DIV) -> BinaryOP.DIV
        accept(MOD) -> BinaryOP.MOD
        accept(POW) -> BinaryOP.POW
        accept(BIT_AND) -> BinaryOP.BIT_AND
        accept(BIT_OR) -> BinaryOP.BIT_OR
        accept(BIT_XOR) -> BinaryOP.BIT_XOR
        accept(SHL) -> BinaryOP.SHL
        accept(SHR) -> BinaryOP.SHR
        accept(EQ) -> BinaryOP.EQ
        accept(NE) -> BinaryOP.NE
        accept(LT) -> BinaryOP.LT
        accept(GT) -> BinaryOP.GT
        accept(LTE) -> BinaryOP.LTE
        accept(GTE) -> BinaryOP.GTE
        accept(AND) -> BinaryOP.AND
        accept(OR) -> BinaryOP.OR
        else -> ice()
    }

    private fun parseBinary(next: () -> ExprST, vararg types: TokenType): ExprST {
        var ret = next()
        while (more() && accept(*types)) {
            val op = parseBinaryOp()
            this.next()
            ret = BinaryST(op, ret, next())
        }
        return ret
    }

    private fun parseBinaryMul()        = parseBinary(::parseUnary, MUL, DIV, MOD)
    private fun parseBinaryAdd()        = parseBinary(::parseBinaryMul, ADD, SUB)
    private fun parseBinaryShift()      = parseBinary(::parseBinaryAdd, SHL, SHR)
    private fun parseBinaryComparison() = parseBinary(::parseBinaryShift, LT, GT, LTE, GTE)
    private fun parseBinaryEquality()   = parseBinary(::parseBinaryComparison, EQ, NE)
    private fun parseBinaryAnd()        = parseBinary(::parseBinaryEquality, AND)
    private fun parseBinaryOr()         = parseBinary(::parseBinaryAnd, OR)
    private fun parseBinaryLogicalAnd() = parseBinary(::parseBinaryOr, BIT_AND)
    private fun parseBinaryLogicalOr()  = parseBinary(::parseBinaryLogicalAnd, BIT_OR)
    private fun parseExpr()             = parseBinary(::parseBinaryLogicalOr, ADD, SUB)

    private fun parseInclude() = IncludeST(expectDirectiveNext("include").let { parseString().value })

    private fun parseBlockRaw(): List<Node> {
        expectNext(LBRACE)
        val xs = mutableListOf<Node>()
        while (more() && !accept(RBRACE)) xs += parse2()
        expectNext(RBRACE)
        return xs
    }

    private fun parseBlock(): BlockST {
        val (xs, scope) = pushScope {
            Pair(parseBlockRaw(), currentScope)
        }
        return BlockST(xs, scope)
    }

    private fun parseDefine(): DefineST {
        expectDirectiveNext("define")
        val name = parseIdent()
        val value = parseExpr()
        return DefineST(name, value)
    }

    private fun parseMacroCall(): MacroCallST {
        val name = parseIdent()

        expectNext(LPAREN)
        val args = mutableListOf<ExprST>()
        while (more() && !accept(RPAREN)) {
            args += parseExpr()
            if (accept(COMMA)) next()
            else expect(RPAREN)
        }
        expectNext(RPAREN)

        return MacroCallST(name, args)
    }

    private fun parseParamList(): List<String> {
        expectNext(LPAREN)
        val xs = mutableListOf<String>()
        while (more() && !accept(RPAREN)) {
            xs += expectNext(IDENT).value
            if (accept(COMMA)) next()
            else expect(RPAREN)
        }
        expectNext(RPAREN)
        return xs
    }

    private fun parseMacro(): MacroST {
        expectDirectiveNext("macro")
        val name = parseIdent()
        val params = if(accept(LPAREN)) parseParamList() else listOf()
        val body = parseBlockRaw()
        return MacroST(name, params, body)
    }

    private fun parseRepeat() = pushScope {
        expectDirectiveNext("repeat")
        val count = parseExpr()
        val iteratorName = if (acceptNext(COMMA)) parseIdent().value else "i"
        val body = parse2()
        RepeatST(count, iteratorName, body)
    }

    private fun parseRes(): RepeatST {
        expectDirectiveNext("res")
        val count = parseExpr()
        val value = if (acceptNext(COMMA)) parseExpr() else IntST(0)
        return RepeatST(count, null, value)
    }

    private inline fun parseIfLike(
        directive: String,
        condfn: () -> ExprST = { parseExpr() },
        thenfn: () -> Node = { parse2() },
        otherwisefn: () -> Node? = { parse2() }
    ): IfST {
        expectDirectiveNext(directive)
        val cond = condfn()
        val then = thenfn()
        val otherwise = if(acceptDirectiveNext("else")) otherwisefn() else null
        return IfST(cond, then, otherwise)
    }

    private fun parseIf() = parseIfLike("if")
    private fun parseIfBlank() = parseIfLike("ifblank", { BlankST(parseExpr()) })

    // TODO: think of a name for this
    private fun parse2(): Node {
        if (acceptNext(IDENT)) {
            // TODO: this is dumb; stop it.
            val st = parseWithRetry({
                if (accept(LPAREN)) {
                    prev()
                    parseMacroCall()
                } else {
                    throw ParseException()
                }
            })
            if (st != null) return st
            prev()
        }

        return when {
            accept(LABEL) -> LabelST(expectNext(LABEL).value)
            accept(LBRACE) -> parseBlock()
            acceptDirective("define") -> parseDefine()
            acceptDirective("repeat") -> parseRepeat()
            acceptDirective("res") -> parseRes()
            acceptDirective("if") -> parseIf()
            acceptDirective("ifblank") -> parseIfBlank()
            else -> parseExpr()
        }
    }

    fun parse(): FileST {
        val includes = mutableListOf<IncludeST>()
        val body = mutableListOf<Node>()
        while (more()) when {
            acceptDirective("include") -> includes += parseInclude()
            acceptDirective("macro") -> body += parseMacro()
            else -> body += parse2()
        }
        return FileST(lexer, includes, body, currentScope)
    }
}

fun parse(file: File) = worker(WorkerName("parse")) {
    val lexer = Lexer(this, file.name, file.readText())
    val parser = Parser(this, lexer)
    val ast = parser.parse()

    println("parsedAst:\n${astToString(ast)}")

    enqueueWorker(bindNames(ast))
}