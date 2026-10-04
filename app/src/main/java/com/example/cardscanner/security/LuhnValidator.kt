package com.example.cardscanner.security

/**
 * Validates card-number candidates. Runs purely on integers; it never stores,
 * logs or transmits the candidate.
 */
object LuhnValidator {

    /** Standard Luhn (mod 10) checksum. */
    fun isValid(candidate: String): Boolean {
        val digits = candidate.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }
}

/**
 * Local, offline brand/prefix checks. The primary target is Visa; other
 * brands are recognized only to report "unsupported" accurately.
 */
object CardBrandRules {

    val VISA = "VISA"
    private val MASTERCARD = "MASTERCARD"
    private val AMEX = "AMEX"
    private val DISCOVER = "DISCOVER"
    private val UNKNOWN = "UNKNOWN"

    fun visaPrefixValid(digits: String): Boolean {
        if (digits.length !in 13..19) return false
        return when (digits.length) {
            13, 16, 19 -> digits.startsWith("4")
            else -> false
        }
    }

    /** Coarse brand detection used only to explain an "unsupported" rejection. */
    fun detectBrand(digits: String): String {
        val d = digits.filter { it.isDigit() }
        return when {
            d.isEmpty() -> UNKNOWN
            d.startsWith("4") -> VISA
            d.length >= 2 && d.take(2).toInt() in 51..55 -> MASTERCARD
            d.length >= 2 && d.take(2) == "34" || d.take(2) == "37" -> AMEX
            d.length >= 4 && d.take(4) == "6011" || d.take(2) == "65" -> DISCOVER
            else -> UNKNOWN
        }
    }
}
