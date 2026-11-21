package com.autoledger.app.parser

import com.autoledger.app.data.model.TransactionType

data class ParsedTransaction(
    val type: TransactionType,
    val amount: Double,
    val timestamp: Long,
    val merchantName: String,
    val bankName: String?,
    val maskedAccount: String?,
    val utrRef: String?,
    val description: String,
    val rawText: String
)

object TransactionParser {

    private val OTP_KEYWORDS = listOf(
        "otp", "one time password", "verification code", "security code",
        "login code", "do not share", "valid for", "mpin", "cvv",
        "request for rs", "collect request", "has requested", "payment request",
        "bill is due", "due date", "minimum due", "statement generated"
    )

    private val DEBIT_KEYWORDS = listOf(
        "debited", "debit", "spent", "paid", "sent", "withdrawn", "deducted",
        "transferred to", "trf to", "purchase of", "payment of", "dr ", "dr."
    )

    private val CREDIT_KEYWORDS = listOf(
        "credited", "credit", "received", "deposited", "refund", "cashback",
        "reversed", "cr ", "cr."
    )

    private val AMOUNT_REGEX = Regex(
        """(?i)(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|(?:debited|credited|paid|sent|received)\s+(?:by|for|of)?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    )

    private val ACCOUNT_REGEX = Regex(
        """(?i)(?:a/c|acct|account|card|ac)\s*(?:no\.?)?\s*(?:xx+|x+|\*+)?([0-9]{3,4})"""
    )

    private val UTR_REGEX = Regex(
        """(?i)(?:utr|ref(?:\s*no\.?)?|upi(?:\s*ref)?|txn\s*id)[:\s#-]*([0-9]{10,16})"""
    )

    private val MERCHANT_PATTERNS = listOf(
        Regex("""(?i)(?:trf\s+to|transferred\s+to|paid\s+to|sent\s+to|to\s+vpa|to)\s+([A-Za-z0-9@._&\-\s]{2,40}?)(?:\s+(?:on|ref|utr|upi|via|from|if|not|call|bal|avl|info|credited)|\s*[.;,\n]|$)"""),
        Regex("""(?i)(?:at|info[:\s]+upi/[^/]+/|vpa\s+)([A-Za-z0-9@._&\-\s]{2,40}?)(?:\s+(?:on|ref|utr|via|from|if|not|bal|avl)|\s*[.;,\n]|$)"""),
        Regex("""(?i)([A-Za-z0-9._&\-\s]{2,35}?)\s+credited\b"""),
        Regex("""(?i)(?:received\s+from|from)\s+([A-Za-z0-9@._&\-\s]{2,40}?)(?:\s+(?:on|ref|utr|upi|via|to|in)|\s*[.;,\n]|$)""")
    )

    private val BANK_MAP = listOf(
        "HDFC" to listOf("hdfc", "hdfcbk"),
        "SBI" to listOf("sbi", "sbiupi", "sbipsg", "sbiinb"),
        "ICICI" to listOf("icici", "icicib"),
        "Axis" to listOf("axis", "axisbk"),
        "Kotak" to listOf("kotak", "kotakb"),
        "Bank of Baroda" to listOf("bob", "baroda"),
        "IndusInd" to listOf("indusind"),
        "Yes Bank" to listOf("yesbnk", "yes bank"),
        "IDFC First" to listOf("idfc"),
        "PNB" to listOf("pnb"),
        "Canara" to listOf("canara"),
        "Google Pay" to listOf("google pay", "gpay"),
        "PhonePe" to listOf("phonepe"),
        "Paytm" to listOf("paytm"),
        "CRED" to listOf("cred"),
        "Amazon Pay" to listOf("amazon pay")
    )

    fun parse(senderOrApp: String, body: String, timestamp: Long = System.currentTimeMillis()): ParsedTransaction? {
        val text = body.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (text.length < 10) return null
        val lower = text.lowercase()

        // 1. Exclude OTPs, payment requests, and bill reminders
        if (OTP_KEYWORDS.any { lower.contains(it) }) return null

        // 2. Detect transaction direction (Expense vs Income)
        val type = detectDirection(lower) ?: return null

        // 3. Extract amount
        val amount = extractAmount(text) ?: return null
        if (amount <= 0.0 || amount > 10_000_000.0) return null

        // 4. Extract bank, masked account, UTR, and merchant
        val bankName = detectBank(senderOrApp, text)
        val maskedAccount = ACCOUNT_REGEX.find(text)?.groupValues?.getOrNull(1)
        val utrRef = UTR_REGEX.find(text)?.groupValues?.getOrNull(1)
        val merchant = extractMerchant(text, type, bankName)
        val channel = detectChannel(lower)

        val descParts = buildList {
            add(channel)
            if (merchant.isNotBlank()) add(if (type == TransactionType.EXPENSE) "to $merchant" else "from $merchant")
            if (!bankName.isNullOrBlank()) add("($bankName${maskedAccount?.let { " XX$it" }.orEmpty()})")
        }

        return ParsedTransaction(
            type = type,
            amount = amount,
            timestamp = timestamp,
            merchantName = merchant,
            bankName = bankName,
            maskedAccount = maskedAccount,
            utrRef = utrRef,
            description = descParts.joinToString(" "),
            rawText = text
        )
    }

    private fun detectDirection(lower: String): TransactionType? {
        // Handle ICICI style "Acct XX123 debited ...; MERCHANT credited" -> debit comes first
        val debitIdx = DEBIT_KEYWORDS.mapNotNull { kw -> lower.indexOf(kw).takeIf { it >= 0 } }.minOrNull()
        val creditIdx = CREDIT_KEYWORDS.mapNotNull { kw -> lower.indexOf(kw).takeIf { it >= 0 } }.minOrNull()

        return when {
            debitIdx != null && creditIdx != null ->
                if (debitIdx <= creditIdx) TransactionType.EXPENSE else TransactionType.INCOME
            debitIdx != null -> TransactionType.EXPENSE
            creditIdx != null -> TransactionType.INCOME
            else -> null
        }
    }

    private fun extractAmount(text: String): Double? {
        val match = AMOUNT_REGEX.find(text) ?: return null
        val rawNum = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return null
        return rawNum.replace(",", "").toDoubleOrNull()
    }

    private fun detectBank(senderOrApp: String, text: String): String? {
        val combined = "$senderOrApp $text".lowercase()
        for ((name, keywords) in BANK_MAP) {
            if (keywords.any { combined.contains(it) }) return name
        }
        return null
    }

    private fun detectChannel(lower: String): String = when {
        lower.contains("upi") || lower.contains("vpa") || lower.contains("gpay") ||
            lower.contains("phonepe") || lower.contains("paytm") -> "UPI"
        lower.contains("credit card") || lower.contains("cc ") -> "Credit Card"
        lower.contains("debit card") || lower.contains("atm") -> "Debit Card"
        lower.contains("imps") -> "IMPS"
        lower.contains("neft") -> "NEFT"
        else -> "Bank"
    }

    private fun extractMerchant(text: String, type: TransactionType, bankName: String?): String {
        for (pattern in MERCHANT_PATTERNS) {
            val match = pattern.find(text)
            val candidate = match?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', ',', ';', '-')
            if (!candidate.isNullOrBlank() && candidate.length >= 2) {
                val clean = candidate.replace(Regex("(?i)^(a/c|acct|account|bank|hdfc|sbi|icici)\\s+"), "").trim()
                if (clean.length >= 2 && !clean.equals(bankName, ignoreCase = true) && !clean.all { it.isDigit() }) {
                    return clean.take(36)
                }
            }
        }
        return if (type == TransactionType.EXPENSE) "UPI / Bank Payee" else "Bank Credit"
    }
}
