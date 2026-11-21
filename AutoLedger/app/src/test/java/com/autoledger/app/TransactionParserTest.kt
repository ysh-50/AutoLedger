package com.autoledger.app

import com.autoledger.app.data.model.TransactionType
import com.autoledger.app.parser.TransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionParserTest {

    @Test
    fun parsesHdfcUpiDebitSms() {
        val sms = "Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY INSTAMART On 21/11/26 Ref 432512345678"
        val res = TransactionParser.parse("VM-HDFCBK", sms, 1000L)
        assertNotNull(res)
        assertEquals(TransactionType.EXPENSE, res!!.type)
        assertEquals(250.0, res.amount, 0.01)
        assertEquals("HDFC", res.bankName)
        assertEquals("1234", res.maskedAccount)
        assertEquals("432512345678", res.utrRef)
    }

    @Test
    fun parsesSbiUpiDebitSms() {
        val sms = "Dear UPI user A/C X5678 debited by 450.50 on date 21Nov26 trf to ZOMATO Refno 432598765432. If not u call SBI."
        val res = TransactionParser.parse("AD-SBIUPI", sms, 1000L)
        assertNotNull(res)
        assertEquals(TransactionType.EXPENSE, res!!.type)
        assertEquals(450.50, res.amount, 0.01)
        assertEquals("SBI", res.bankName)
        assertEquals("5678", res.maskedAccount)
        assertEquals("432598765432", res.utrRef)
    }

    @Test
    fun parsesIciciUpiDebitSms() {
        val sms = "ICICI Bank Acct XX9012 debited for Rs 1,299.00 on 21-Nov-26; AMAZON PAY credited. UPI:432511223344."
        val res = TransactionParser.parse("VM-ICICIB", sms, 1000L)
        assertNotNull(res)
        assertEquals(TransactionType.EXPENSE, res!!.type)
        assertEquals(1299.0, res.amount, 0.01)
        assertEquals("ICICI", res.bankName)
        assertEquals("9012", res.maskedAccount)
        assertEquals("432511223344", res.utrRef)
    }

    @Test
    fun parsesPhonePeAndGPayNotifications() {
        val gpay = TransactionParser.parse("Google Pay", "Paid ₹180.00 to Ramesh Tea Stall", 2000L)
        assertNotNull(gpay)
        assertEquals(TransactionType.EXPENSE, gpay!!.type)
        assertEquals(180.0, gpay.amount, 0.01)

        val phonepe = TransactionParser.parse("PhonePe", "Received ₹500 from Amit Sharma", 2000L)
        assertNotNull(phonepe)
        assertEquals(TransactionType.INCOME, phonepe!!.type)
        assertEquals(500.0, phonepe.amount, 0.01)
    }

    @Test
    fun ignoresOtpAndCollectRequests() {
        assertNull(TransactionParser.parse("VM-HDFCBK", "123456 is your OTP for Rs. 5000 transaction. Do not share."))
        assertNull(TransactionParser.parse("PhonePe", "Ramesh has requested Rs. 300 on PhonePe"))
    }
}
