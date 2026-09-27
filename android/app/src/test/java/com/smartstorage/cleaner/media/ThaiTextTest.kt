package com.smartstorage.cleaner.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Real OCR output from the Thai OCR spike (androidTest/THAI_OCR_SPIKE.md). Mirrors ThaiTextTests in Swift.
class ThaiTextTest {
    private val now = LocalDate.of(2026, 9, 28).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun day(millis: Long?) = millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    @Test fun normalizeRepairsSaraAmAndMonths() {
        assertEquals("โอนเงินสำเร็จ", ThaiText.normalize("โอนเงินสําเร็จ"))
        assertEquals("27 ก.ย. 69 09:32", ThaiText.normalize("27 กุย. 69 09:32"))
        assertEquals("วันที 21 ก.ย. 2569", ThaiText.normalize("วันที 21 กุย. 2569"))
        // Not between a day and a year: left alone.
        assertEquals("กุย. ของฉัน", ThaiText.normalize("กุย. ของฉัน"))
    }

    @Test fun foldIgnoresToneMarks() {
        assertEquals(ThaiText.fold("ตะกร้าสินค้า"), ThaiText.fold("ตะกราสินคา"))
        assertEquals("tops market", ThaiText.fold("TOPS MARKET"))
    }

    @Test fun tesseractSlipReadsAsReceipt() {
        val info = ScreenshotClassifier.classify("โอนเงินสําเร็จ\n27 กุย. 69 09:32\nจํานวนเงิน 500.00 บาท\nไปยัง นาย สมชาย ใจดี\n[=]\nOF", false, now)
        assertEquals(ScreenshotKind.Receipts, info.kind)
        assertEquals("นาย สมชาย ใจดี", info.receipt?.merchant)
        assertEquals(0, BigDecimal(500).compareTo(info.receipt?.amount))
        assertEquals(LocalDate.of(2026, 9, 27), day(info.receipt?.date))
    }

    @Test fun droppedToneMarksStillMatchKeywords() {
        val shopping = "ตะกราสินคา\nเสื้อยืดคอกลม ผ้าผ้าย 100%\nสีขาว lea L\n8299\nส่งฟรีเมื่อสั่งซื้อครบ @500\nโค้ดส่วนลด ลด 10%\nรวม B269\nสั่งซื้อสินค้า"
        assertEquals(ScreenshotKind.Shopping, ScreenshotClassifier.classify(shopping, false, now).kind)
        val chat = "แม\nออนไลน์\nเย็นนีกลับบานกิโมงจะ\n18:02\nประมาณหนึ่งทุมคะ เดียวซื้อขนมไปฝาก\n18:05\nอ่านแล้ว\nไดจะ ขับรถดี ๆ นะลูก\n18:06"
        assertEquals(ScreenshotKind.Chats, ScreenshotClassifier.classify(chat, false, now).kind)
        assertEquals(0, BigDecimal(120).compareTo(ReceiptExtractor.total(listOf("ลาเตเย็น 65.00", "ครัวซองต่ 55.00", "รวมทังสิน 120.00 บาท"))))
    }

    @Test fun spotsThaiReadAsLatin() {
        assertTrue(ThaiText.looksLikeMisreadThai("SNuNNuWUNuau\nluLaSaSUuEU\n21 n.g. 2569\nlaLINUEU 65.00"))
        assertTrue(ThaiText.looksLikeMisreadThai("Tauâuanỗ?\n27 n.g. 69 09:32\n500.00 UIM"))
        assertTrue(ThaiText.looksLikeMisreadThai("LắDBRADnau wndng 100%"))
        assertFalse(ThaiText.looksLikeMisreadThai("Mom\nonline\nSee you at 7?\n10:41\nOk! I'll bring dessert\nDelivered\nGreat"))
        assertFalse(ThaiText.looksLikeMisreadThai("TOPS MARKET\nCentral Rama 9\nTAX INVOICE (ABB)\nTOTAL 456.00\nCASH 500.00\nTHANK YOU"))
        assertFalse(ThaiText.looksLikeMisreadThai(""))
    }

    @Test fun ocrNoiseIsNotAMerchant() {
        assertEquals("Central Department Store", ReceiptExtractor.merchant(listOf("๕ va", "ใบเสร็จรับเงิน", "Central Department Store")))
        assertEquals("7-Eleven", ReceiptExtractor.merchant(listOf("7-Eleven")))
    }
}
