package com.example.calculator

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

data class CalculationHistoryItem(
    val id: Long = System.currentTimeMillis(),
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class CalculationResult {
    data class Success(val value: Double, val formatted: String) : CalculationResult()
    data class Error(val message: String) : CalculationResult()
}

/**
 * Pure Kotlin calculator engine providing expression parsing, arithmetic evaluation,
 * string formatting, and input manipulation.
 */
class CalculatorEngine {

    companion object {
        const val OP_ADD = "+"
        const val OP_SUB = "−"
        const val OP_MUL = "×"
        const val OP_DIV = "÷"
        const val OP_MOD = "%"

        val OPERATORS = setOf(OP_ADD, OP_SUB, OP_MUL, OP_DIV, OP_MOD, "-", "*", "/")
    }

    /**
     * Evaluates a mathematical string expression and returns CalculationResult.
     */
    fun evaluate(expression: String): CalculationResult {
        val sanitized = sanitizeExpression(expression)
        if (sanitized.isBlank()) {
            return CalculationResult.Success(0.0, "0")
        }

        return try {
            val tokens = tokenize(sanitized)
            if (tokens.isEmpty()) {
                return CalculationResult.Success(0.0, "0")
            }
            val parser = ExpressionParser(tokens)
            val result = parser.parse()
            if (result.isNaN()) {
                CalculationResult.Error("Error")
            } else if (result.isInfinite()) {
                CalculationResult.Error("Cannot divide by zero")
            } else {
                CalculationResult.Success(result, formatNumber(result))
            }
        } catch (e: ArithmeticException) {
            CalculationResult.Error(e.message ?: "Error")
        } catch (e: Exception) {
            CalculationResult.Error("Invalid expression")
        }
    }

    /**
     * Formats a Double value into a clean, human-readable string.
     * E.g.: 4.0 -> "4", 4.125 -> "4.125", 1000000000000 -> scientific notation
     */
    fun formatNumber(value: Double): String {
        if (value.isNaN()) return "Error"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

        val absVal = abs(value)
        if (absVal >= 1e12 || (absVal < 1e-6 && absVal > 0.0)) {
            val symbols = DecimalFormatSymbols(Locale.US)
            val scientificFormat = DecimalFormat("0.######E0", symbols)
            return scientificFormat.format(value)
        }

        // Check if value is whole number
        if (abs(value - round(value)) < 1e-10) {
            return value.toLong().toString()
        }

        val symbols = DecimalFormatSymbols(Locale.US)
        val decimalFormat = DecimalFormat("#,##0.########", symbols)
        return decimalFormat.format(value)
    }

    /**
     * Appends a digit (0-9) to the current expression.
     */
    fun appendDigit(current: String, digit: String): String {
        return if (current == "0") {
            digit
        } else {
            current + digit
        }
    }

    /**
     * Appends an operator (+, −, ×, ÷) safely.
     * Replaces previous operator if the expression already ends with one.
     */
    fun appendOperator(current: String, operator: String): String {
        val trimmed = current.trimEnd()
        if (trimmed.isEmpty()) {
            return if (operator == OP_SUB) OP_SUB else ""
        }

        // Check if expression ends with an operator
        val lastChar = trimmed.takeLast(1)
        if (OPERATORS.contains(lastChar)) {
            return trimmed.dropLast(1).trimEnd() + " " + operator + " "
        }

        return "$trimmed $operator "
    }

    /**
     * Appends a decimal point. Prevents consecutive decimals in the current operand.
     */
    fun appendDecimal(current: String): String {
        if (current.isEmpty()) return "0."

        val lastToken = current.trimEnd().split(" ").lastOrNull() ?: ""
        if (lastToken.contains(".")) {
            return current
        }

        val lastChar = current.takeLast(1)
        return if (lastChar == " " || OPERATORS.contains(lastChar)) {
            "$current 0."
        } else {
            "$current."
        }
    }

    /**
     * Appends percentage (x% -> evaluates to x * 0.01).
     */
    fun appendPercentage(current: String): String {
        val trimmed = current.trimEnd()
        if (trimmed.isEmpty()) return ""
        val lastChar = trimmed.takeLast(1)
        return if (lastChar.first().isDigit() || lastChar == ")") {
            "$trimmed%"
        } else {
            current
        }
    }

    /**
     * Appends parentheses intelligently.
     * If there are open parentheses, and last char is digit or close paren, closes it.
     * Otherwise opens a parenthesis.
     */
    fun appendParenthesis(current: String): String {
        val trimmed = current.trimEnd()
        val openCount = trimmed.count { it == '(' }
        val closeCount = trimmed.count { it == ')' }

        if (trimmed.isEmpty()) {
            return "("
        }

        val lastChar = trimmed.takeLast(1).first()
        return if (openCount > closeCount && (lastChar.isDigit() || lastChar == ')')) {
            "$trimmed)"
        } else if (lastChar.isDigit() || lastChar == ')') {
            "$trimmed × ("
        } else {
            "$trimmed("
        }
    }

    /**
     * Toggles the sign (±) of the current operand at the end of the expression.
     */
    fun toggleSign(current: String): String {
        val trimmed = current.trimEnd()
        if (trimmed.isEmpty()) return "-"

        val tokens = trimmed.split(" ").toMutableList()
        val lastToken = tokens.lastOrNull() ?: return "-"

        val toggled = if (lastToken.startsWith("-")) {
            lastToken.removePrefix("-")
        } else if (lastToken.toDoubleOrNull() != null) {
            "-$lastToken"
        } else {
            return current
        }

        tokens[tokens.size - 1] = toggled
        return tokens.joinToString(" ")
    }

    /**
     * Removes the last character or trailing operator from the expression.
     */
    fun backspace(current: String): String {
        val trimmed = current.trimEnd()
        if (trimmed.isEmpty()) return ""
        return trimmed.dropLast(1).trimEnd()
    }

    private fun sanitizeExpression(expr: String): String {
        return expr.replace("−", "-")
            .replace("×", "*")
            .replace("÷", "/")
            .replace(",", "")
            .trim()
    }

    private fun tokenize(input: String): List<CalcToken> {
        val tokens = mutableListOf<CalcToken>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    var hasDot = (c == '.')
                    i++
                    while (i < input.length && (input[i].isDigit() || (!hasDot && input[i] == '.'))) {
                        if (input[i] == '.') hasDot = true
                        i++
                    }
                    val numStr = input.substring(start, i)
                    val numVal = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $numStr")
                    tokens.add(CalcToken.Number(numVal))
                }
                c == '+' -> {
                    tokens.add(CalcToken.Plus)
                    i++
                }
                c == '-' -> {
                    tokens.add(CalcToken.Minus)
                    i++
                }
                c == '*' -> {
                    tokens.add(CalcToken.Multiply)
                    i++
                }
                c == '/' -> {
                    tokens.add(CalcToken.Divide)
                    i++
                }
                c == '%' -> {
                    tokens.add(CalcToken.Percentage)
                    i++
                }
                c == '(' -> {
                    tokens.add(CalcToken.OpenParen)
                    i++
                }
                c == ')' -> {
                    tokens.add(CalcToken.CloseParen)
                    i++
                }
                else -> {
                    i++
                }
            }
        }
        return tokens
    }

    private sealed class CalcToken {
        data class Number(val value: Double) : CalcToken()
        object Plus : CalcToken()
        object Minus : CalcToken()
        object Multiply : CalcToken()
        object Divide : CalcToken()
        object Percentage : CalcToken()
        object OpenParen : CalcToken()
        object CloseParen : CalcToken()
    }

    /**
     * Recursive descent parser:
     * expression = term ( ('+' | '-') term )*
     * term       = factor ( ('*' | '/') factor )*
     * factor     = ('+' | '-') factor | primary ('%')*
     * primary    = NUMBER | '(' expression ')'
     */
    private class ExpressionParser(private val tokens: List<CalcToken>) {
        private var pos = 0

        fun parse(): Double {
            if (tokens.isEmpty()) return 0.0
            val result = expression()
            if (pos < tokens.size) {
                throw IllegalArgumentException("Unexpected token at position $pos")
            }
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (pos < tokens.size) {
                when (tokens[pos]) {
                    is CalcToken.Plus -> {
                        pos++
                        value += term()
                    }
                    is CalcToken.Minus -> {
                        pos++
                        value -= term()
                    }
                    else -> break
                }
            }
            return value
        }

        private fun term(): Double {
            var value = factor()
            while (pos < tokens.size) {
                when (tokens[pos]) {
                    is CalcToken.Multiply -> {
                        pos++
                        value *= factor()
                    }
                    is CalcToken.Divide -> {
                        pos++
                        val divisor = factor()
                        if (abs(divisor) < 1e-15) {
                            throw ArithmeticException("Cannot divide by zero")
                        }
                        value /= divisor
                    }
                    else -> break
                }
            }
            return value
        }

        private fun factor(): Double {
            if (pos >= tokens.size) {
                throw IllegalArgumentException("Unexpected end of expression")
            }

            // Unary operator
            val currentToken = tokens[pos]
            if (currentToken is CalcToken.Plus) {
                pos++
                return factor()
            }
            if (currentToken is CalcToken.Minus) {
                pos++
                return -factor()
            }

            var value = primary()
            // Handle postfix percentage: e.g. 50% -> 0.5
            while (pos < tokens.size && tokens[pos] is CalcToken.Percentage) {
                pos++
                value *= 0.01
            }
            return value
        }

        private fun primary(): Double {
            if (pos >= tokens.size) {
                throw IllegalArgumentException("Unexpected end of expression")
            }

            return when (val token = tokens[pos]) {
                is CalcToken.Number -> {
                    pos++
                    token.value
                }
                is CalcToken.OpenParen -> {
                    pos++
                    val value = expression()
                    if (pos < tokens.size && tokens[pos] is CalcToken.CloseParen) {
                        pos++
                    } else {
                        // Implicitly allow unclosed trailing paren in live expression
                    }
                    value
                }
                else -> throw IllegalArgumentException("Unexpected token: $token")
            }
        }
    }
}
