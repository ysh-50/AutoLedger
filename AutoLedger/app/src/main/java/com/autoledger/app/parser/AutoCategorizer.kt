package com.autoledger.app.parser

import com.autoledger.app.data.model.CategoryEntity
import com.autoledger.app.data.model.TransactionType

object AutoCategorizer {

    private val EXPENSE_RULES: List<Pair<String, List<String>>> = listOf(
        "Food & Dining" to listOf(
            "swiggy", "zomato", "dominos", "pizza", "mcdonald", "kfc", "burger",
            "starbucks", "cafe", "coffee", "chai", "tea", "bakery", "restaurant",
            "hotel", "biryani", "eats", "dining", "sweet", "haldiram"
        ),
        "Groceries" to listOf(
            "blinkit", "zepto", "instamart", "bigbasket", "dmart", "jiomart",
            "reliance fresh", "supermarket", "kirana", "grocery", "dairy", "milk",
            "vegetable", "fruit", "mart", "bazaar", "more retail", "spencer"
        ),
        "Transport & Fuel" to listOf(
            "uber", "ola", "rapido", "metro", "irctc", "railway", "redbus",
            "indianoil", "hpcl", "bpcl", "bharat petroleum", "petrol", "fuel",
            "diesel", "fastag", "toll", "parking", "indigo", "air india", "makemytrip"
        ),
        "Shopping" to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "decathlon",
            "croma", "reliance digital", "westside", "zudio", "trends", "tata cliq",
            "ikea", "apple store", "shoppers stop"
        ),
        "Bills & Recharge" to listOf(
            "jio", "airtel", "vodafone", "vi ", "bsnl", "electricity", "mseb",
            "mahavitaran", "bescom", "tata power", "adani electricity", "gas",
            "mahanagar gas", "broadband", "fiber", "dth", "tata play", "recharge",
            "water bill", "insurance", "lic"
        ),
        "Health & Pharmacy" to listOf(
            "apollo", "pharmeasy", "1mg", "tata 1mg", "medplus", "netmeds",
            "pharmacy", "medical", "chemist", "hospital", "clinic", "diagnostic",
            "pathology", "lab", "doctor", "dental"
        ),
        "Entertainment" to listOf(
            "netflix", "spotify", "hotstar", "jiohotstar", "prime video",
            "bookmyshow", "pvr", "inox", "youtube", "google play", "steam",
            "playstation", "cinema", "movie"
        )
    )

    private val INCOME_RULES: List<Pair<String, List<String>>> = listOf(
        "Salary" to listOf("salary", "payroll", "stipend", "wages"),
        "Refund & Cashback" to listOf("refund", "cashback", "reversal", "reversed", "reward")
    )

    fun normalizeMerchantKey(merchant: String): String {
        return merchant.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(32)
    }

    fun categorize(
        merchantName: String,
        rawText: String,
        type: TransactionType,
        categories: List<CategoryEntity>
    ): Long? {
        val haystack = "$merchantName $rawText".lowercase()
        val rules = if (type == TransactionType.INCOME) INCOME_RULES else EXPENSE_RULES

        for ((categoryName, keywords) in rules) {
            if (keywords.any { haystack.contains(it) }) {
                val match = categories.firstOrNull {
                    it.type == type && it.name.equals(categoryName, ignoreCase = true)
                }
                if (match != null) return match.id
            }
        }

        val fallbackName = if (type == TransactionType.INCOME) "Other Income" else "UPI / Other"
        return categories.firstOrNull { it.type == type && it.name.equals(fallbackName, ignoreCase = true) }?.id
            ?: categories.firstOrNull { it.type == type }?.id
    }
}
