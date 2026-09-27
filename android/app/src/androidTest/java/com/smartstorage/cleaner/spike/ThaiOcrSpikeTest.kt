package com.smartstorage.cleaner.spike

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.smartstorage.cleaner.media.ReceiptExtractor
import com.smartstorage.cleaner.media.ScreenshotClassifier
import com.smartstorage.cleaner.media.TextLayout
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Spike (not shipped): can Tesseract read Thai well enough on device, and how slow is it?
 * Compares ML Kit (current) with Tesseract fast/best models on Thai screenshots and receipt photos,
 * using the app's own classifier and receipt extractor. Results go to logcat (tag ThaiOcrSpike).
 *
 * Models are gitignored: run `app/src/androidTest/fetch_tessdata.sh` first.
 * Run: ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartstorage.cleaner.spike.ThaiOcrSpikeTest
 */
@RunWith(AndroidJUnit4::class)
class ThaiOcrSpikeTest {
    private data class Case(
        val file: String,
        val isPhoto: Boolean,
        val truth: List<String>,
        val kind: String,
        val merchant: String? = null,
        val amount: String? = null,
        val date: LocalDate? = null,
    )

    private val cases = listOf(
        Case("receipt_th.png", false, listOf("ใบเสร็จรับเงิน", "Central Department Store", "เสื้อ 1 ตัว 2,990.00", "ภาษีมูลค่าเพิ่ม 195.61",
            "รวมทั้งสิ้น 3,450.00 บาท", "ชำระเงินด้วยบัตร VISA"), "Receipts", "Central Department Store", "3450"),
        Case("transfer_slip.png", false, listOf("โอนเงินสำเร็จ", "27 ก.ย. 69 09:32", "จำนวนเงิน 500.00 บาท", "ไปยัง นาย สมชาย ใจดี"),
            "Receipts", "นาย สมชาย ใจดี", "500", LocalDate.of(2026, 9, 27)),
        Case("th_slip.png", false, listOf("โอนเงินสำเร็จ", "15 ก.ย. 69 14:05 น.", "จาก นาย สมศักดิ์ มั่งมี", "ไปยัง นางสาว มาลี รักไทย",
            "จำนวนเงิน 1,250.00 บาท", "ค่าธรรมเนียม 0.00 บาท", "เลขที่รายการ 2569091514051234"),
            "Receipts", "นางสาว มาลี รักไทย", "1250", LocalDate.of(2026, 9, 15)),
        Case("th_chat.png", false, listOf("แม่", "ออนไลน์", "เย็นนี้กลับบ้านกี่โมงจ๊ะ", "18:02", "ประมาณหนึ่งทุ่มค่ะ เดี๋ยวซื้อขนมไปฝาก", "18:05",
            "อ่านแล้ว", "ได้จ้ะ ขับรถดี ๆ นะลูก", "18:06"), "Chats"),
        Case("th_shopping.png", false, listOf("ตะกร้าสินค้า", "เสื้อยืดคอกลม ผ้าฝ้าย 100%", "สีขาว ไซส์ L", "฿299", "ส่งฟรีเมื่อสั่งซื้อครบ ฿500",
            "โค้ดส่วนลด ลด 10%", "รวม ฿269", "สั่งซื้อสินค้า"), "Shopping"),
        Case("IMG_5002.jpg", true, listOf("ร้านกาแฟบ้านสวน", "ใบเสร็จรับเงิน", "วันที่ 21 ก.ย. 2569", "ลาเต้เย็น 65.00", "ครัวซองต์ 55.00",
            "รวมทั้งสิ้น 120.00 บาท", "ขอบคุณค่ะ"), "Receipts", "ร้านกาแฟบ้านสวน", "120", LocalDate.of(2026, 9, 21)),
        Case("th_receipt_photo.jpg", true, listOf("เซเว่น อีเลฟเว่น", "สาขา อโศก 01234", "ใบเสร็จรับเงิน/ใบกำกับภาษีอย่างย่อ", "15/09/2569 08:12",
            "นมจืด 1 ขวด 25.00", "ขนมปังโฮลวีต 42.00", "กาแฟเย็น 45.00", "ข้าวกะเพราไก่ 75.00", "ยอดสุทธิ 4 ชิ้น 187.00", "เงินสด 200.00",
            "เงินทอน 13.00", "ขอบคุณที่ใช้บริการ"), "Receipts", "เซเว่น อีเลฟเว่น", "187", LocalDate.of(2026, 9, 15)),
        // English control: Tesseract must not make Latin receipts worse.
        Case("IMG_5001.jpg", true, listOf("TOPS MARKET", "Central Rama 9", "TAX INVOICE (ABB)", "27/09/2026 18:42", "Milk 2L 89.00", "Bread 45.00",
            "Eggs x10 72.00", "Coffee beans 250.00", "SUBTOTAL 456.00", "VAT 7% 29.83", "TOTAL 456.00", "CASH 500.00", "CHANGE 44.00", "THANK YOU"),
            "Receipts", "TOPS MARKET", "456", LocalDate.of(2026, 9, 27)),
    )

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val now = LocalDate.of(2026, 9, 28).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun bitmap(file: String, side: Int): Bitmap {
        val full = instrumentation.context.assets.open("ocr/$file").use { BitmapFactory.decodeStream(it) }
        val scale = minOf(1.0, side.toDouble() / maxOf(full.width, full.height))
        return if (scale == 1.0) full else Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true)
    }

    /** Copies a model set out of the test APK so Tesseract can open it; returns its data path. */
    private fun modelDir(name: String): String {
        val dir = File(instrumentation.targetContext.filesDir, name)
        File(dir, "tessdata").mkdirs()
        for (lang in listOf("tha", "eng")) {
            val out = File(dir, "tessdata/$lang.traineddata")
            if (!out.exists()) instrumentation.context.assets.open("$name/tessdata/$lang.traineddata").use { input -> out.outputStream().use(input::copyTo) }
        }
        return dir.absolutePath
    }

    private interface Engine {
        val name: String
        fun read(image: Bitmap, isPhoto: Boolean): String
        fun close() {}
    }

    private class MlKit : Engine {
        override val name = "ML Kit (Latin)"
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        override fun read(image: Bitmap, isPhoto: Boolean): String {
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(image, 0)))
            if (!isPhoto) return result.text
            return TextLayout.rows(result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val c = line.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
                fun p(i: Int) = TextLayout.Point(c[i].x.toDouble(), c[i].y.toDouble())
                TextLayout.Fragment(line.text, p(0), p(1), p(2), p(3))
            }).joinToString("\n")
        }
        override fun close() = recognizer.close()
    }

    /**
     * Post-OCR repairs for Tesseract's Thai output: sara am written as nikhahit + sara aa, and the
     * dot in month abbreviations ("ก.ย.") read as sara u ("กุย.").
     */
    object ThaiFix {
        private val months = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
        private val monthPatterns = months.map { m ->
            val parts = m.trimEnd('.').split('.')
            // Only between a day and a year ("27 กุย. 69"), so ordinary words are never touched.
            Regex("""(?<=\d\s{0,2})""" + parts.joinToString("""\s?[.ุ,]?\s?""") { Regex.escape(it) } + """[.,]?(?=\s*\d)""") to m
        }

        fun apply(text: String): String {
            var out = text.replace("\u0E4D\u0E32", "\u0E33")
            for ((regex, month) in monthPatterns) out = regex.replace(out) { month + " " }
            return out.replace(Regex(" {2,}"), " ")
        }
    }

    private class Tesseract(
        override val name: String,
        dataPath: String,
        private val mode: Int,
        private val fix: Boolean = false,
        languages: String = "tha+eng",
    ) : Engine {
        private val api = TessBaseAPI().apply {
            check(init(dataPath, languages, TessBaseAPI.OEM_LSTM_ONLY)) { "Tesseract init failed" }
            pageSegMode = mode
        }
        override fun read(image: Bitmap, isPhoto: Boolean): String {
            api.setImage(image)
            val raw = api.utF8Text.orEmpty()
            if (!fix) return raw.also { api.clear() }
            // Keep only lines Tesseract is reasonably sure about (drops "๕ vwya"-style noise).
            val lines = mutableListOf<String>()
            val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
            val iterator = api.resultIterator
            iterator.begin()
            do {
                val line = iterator.getUTF8Text(level)?.trim().orEmpty()
                if (line.isNotEmpty() && iterator.confidence(level) >= MIN_CONFIDENCE) lines += line
            } while (iterator.next(level))
            iterator.delete()
            api.clear()
            return ThaiFix.apply(lines.joinToString("\n"))
        }
        override fun close() = api.recycle()
    }

    @Test
    fun compareEngines() {
        val fast = modelDir("tess_fast")
        val best = modelDir("tess_best")
        val initStart = System.nanoTime()
        val engines = listOf(
            MlKit(),
            Tesseract("Tess fast PSM6", fast, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK),
            Tesseract("Tess fast PSM6+fix", fast, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, fix = true),
            Tesseract("Tess best PSM6+fix", best, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, fix = true),
            Tesseract("Tess fast tha-only+fix", fast, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, fix = true, languages = "tha"),
        )
        log("init all engines: ${(System.nanoTime() - initStart) / 1_000_000} ms")
        log("| image | engine | ms | CER | kind | merchant | amount | date |")
        log("|---|---|---|---|---|---|---|---|")
        val totals = engines.associate { it.name to IntArray(4) } // fields right, fields total, ms, cases
        for (case in cases) {
            val image = bitmap(case.file, if (case.isPhoto) 2048 else 1600)
            for (engine in engines) {
                engine.read(image, case.isPhoto) // warm-up, so timing is steady-state
                val start = System.nanoTime()
                val text = engine.read(image, case.isPhoto)
                val ms = ((System.nanoTime() - start) / 1_000_000).toInt()
                val info = ScreenshotClassifier.classify(text, false, now)
                val receipt = ReceiptExtractor.extract(text, now)
                val checks = mutableListOf(info.kind.name == case.kind)
                val merchantOk = case.merchant?.let { squash(receipt.merchant) == squash(it) }?.also { checks += it }
                val amountOk = case.amount?.let { receipt.amount?.compareTo(BigDecimal(it)) == 0 }?.also { checks += it }
                val dateOk = case.date?.let { receipt.date?.let { d -> Instant.ofEpochMilli(d).atZone(ZoneOffset.UTC).toLocalDate() } == it }?.also { checks += it }
                totals.getValue(engine.name).let { it[0] += checks.count { ok -> ok }; it[1] += checks.size; it[2] += ms; it[3]++ }
                log("| ${case.file} | ${engine.name} | $ms | ${"%.2f".format(cer(case.truth.joinToString(""), text))} | ${mark(info.kind.name == case.kind)} ${info.kind.name} | " +
                    "${mark(merchantOk)} ${receipt.merchant ?: "-"} | ${mark(amountOk)} ${receipt.amount?.toPlainString() ?: "-"} | " +
                    "${mark(dateOk)} ${receipt.date?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: "-"} |")
                log("TEXT ${case.file} / ${engine.name}: ${text.replace("\n", " ⏎ ")}")
            }
            image.recycle()
        }
        log("SUMMARY")
        for ((name, t) in totals) log("$name: fields ${t[0]}/${t[1]}, avg ${t[2] / t[3]} ms/image")
        engines.forEach { it.close() }
    }

    private companion object {
        /** Tesseract line confidence (0–100) below which a line is treated as noise. */
        const val MIN_CONFIDENCE = 50f
    }

    private fun mark(ok: Boolean?) = when (ok) { true -> "✅"; false -> "❌"; null -> "" }

    /** Compare ignoring spacing (Tesseract often splits Thai words) and honorific spacing. */
    private fun squash(s: String?) = s.orEmpty().filterNot { it.isWhitespace() }.lowercase()

    /** Character error rate, whitespace ignored: edit distance / truth length. */
    private fun cer(truth: String, text: String): Double {
        val a = squash(truth)
        val b = squash(text)
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length].toDouble() / a.length.coerceAtLeast(1)
    }

    private fun log(line: String) = Log.i("ThaiOcrSpike", line)
}
