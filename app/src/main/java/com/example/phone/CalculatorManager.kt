package com.example.phone

import com.example.utils.ErrorTracker
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CalculatorManager — Pure-Kotlin safe mathematical evaluator supporting English & Bangla
 * arithmetic phrases without any dynamic code loading or script engines.
 */
class CalculatorManager(
  private val errorTracker: ErrorTracker,
) {

  private val _history = MutableStateFlow<List<CalculationRecord>>(emptyList())
  val history: StateFlow<List<CalculationRecord>> = _history.asStateFlow()

  fun evaluate(rawExpression: String): Result<CalculationRecord> {
    val normalized = normalizeNaturalMath(rawExpression)
    if (normalized.isBlank()) {
      return Result.failure(IllegalArgumentException("Please enter a valid math expression."))
    }

    return runCatching {
      val parser = ExpressionParser(normalized)
      val numericValue = parser.parseExpression()
      if (parser.hasRemainingTokens()) {
        throw IllegalArgumentException("Unexpected symbol in expression.")
      }
      if (numericValue.isNaN() || numericValue.isInfinite()) {
        throw ArithmeticException("Result is undefined.")
      }

      val formatted = formatCleanNumber(numericValue)
      val record = CalculationRecord(expression = normalized, result = formatted)
      _history.value = (_history.value + record).takeLast(MAX_HISTORY)
      errorTracker.logInfo(MODULE_NAME, "Evaluated '$normalized' = $formatted")
      record
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "Calculator error for '$rawExpression': ${err.message}")
    }
  }

  fun clearHistory() {
    _history.value = emptyList()
  }

  /**
   * Translates Bangla numerals (`০`-`৯`) and common natural math words into clean arithmetic syntax.
   */
  fun normalizeNaturalMath(raw: String): String {
    val banglaDigits = mapOf(
      '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
      '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9',
    )
    val asciiDigits = buildString(raw.length) {
      raw.forEach { ch -> append(banglaDigits[ch] ?: ch) }
    }

    return asciiDigits
      .replace("যোগ", "+")
      .replace("বিয়োগ", "-")
      .replace("বিয়োগ", "-")
      .replace("গুণ", "*")
      .replace("ভাগ", "/")
      .replace(Regex("(?i)\\bplus\\b"), "+")
      .replace(Regex("(?i)\\bminus\\b"), "-")
      .replace(Regex("(?i)\\btimes\\b|\\bmultiplied by\\b"), "*")
      .replace(Regex("(?i)\\bdivided by\\b|\\bover\\b"), "/")
      .replace("×", "*")
      .replace("÷", "/")
      .replace("−", "-")
      .replace(Regex("(?i)(\\d+(?:\\.\\d+)?)%\\s*of\\s*(\\d+(?:\\.\\d+)?)")) { match ->
        "(${match.groupValues[1]}/100*${match.groupValues[2]})"
      }
      .replace(Regex("[^0-9.+\\-*/%^()sqrt ]", RegexOption.IGNORE_CASE), "")
      .replace(Regex("\\s+"), "")
  }

  private fun formatCleanNumber(value: Double): String {
    val rounded = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
    return rounded.toPlainString()
  }

  private class ExpressionParser(private val input: String) {
    private var pos = 0

    fun hasRemainingTokens(): Boolean = pos < input.length

    fun parseExpression(): Double {
      var x = parseTerm()
      while (pos < input.length) {
        when (input[pos]) {
          '+' -> {
            pos++
            x += parseTerm()
          }
          '-' -> {
            pos++
            x -= parseTerm()
          }
          else -> return x
        }
      }
      return x
    }

    private fun parseTerm(): Double {
      var x = parsePower()
      while (pos < input.length) {
        when (input[pos]) {
          '*' -> {
            pos++
            x *= parsePower()
          }
          '/' -> {
            pos++
            val denom = parsePower()
            if (denom == 0.0) throw ArithmeticException("Cannot divide by zero.")
            x /= denom
          }
          '%' -> {
            pos++
            x /= 100.0
          }
          else -> return x
        }
      }
      return x
    }

    private fun parsePower(): Double {
      var x = parseFactor()
      if (pos < input.length && input[pos] == '^') {
        pos++
        x = x.pow(parsePower())
      }
      return x
    }

    private fun parseFactor(): Double {
      if (pos < input.length && input[pos] == '+') {
        pos++
        return parseFactor()
      }
      if (pos < input.length && input[pos] == '-') {
        pos++
        return -parseFactor()
      }

      if (pos + 4 <= input.length && input.substring(pos, pos + 4).equals("sqrt", ignoreCase = true)) {
        pos += 4
        val inner = parseFactor()
        if (inner < 0) throw ArithmeticException("Cannot take square root of a negative number.")
        return sqrt(inner)
      }

      val x: Double
      val startPos = pos
      if (pos < input.length && input[pos] == '(') {
        pos++
        x = parseExpression()
        if (pos >= input.length || input[pos] != ')') {
          throw IllegalArgumentException("Missing closing parenthesis.")
        }
        pos++
      } else {
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
          pos++
        }
        if (pos == startPos) {
          throw IllegalArgumentException("Unexpected character in expression.")
        }
        x = input.substring(startPos, pos).toDoubleOrNull()
          ?: throw IllegalArgumentException("Invalid number format.")
      }
      return x
    }
  }

  companion object {
    private const val MODULE_NAME = "CalculatorManager"
    private const val MAX_HISTORY = 15
  }
}
