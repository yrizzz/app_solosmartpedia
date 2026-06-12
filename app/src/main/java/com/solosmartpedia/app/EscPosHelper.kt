package com.solosmartpedia.app

import org.json.JSONObject

/**
 * Builds ESC/POS byte sequences for standard thermal printers.
 * Compatible with Epson, Star, GOOJPRT, Rongta, Xprinter, etc.
 */
object EscPosHelper {

    // ── Constants ──────────────────────────────────────────────────────────
    private val INIT          = byteArrayOf(0x1B, 0x40)
    private val CUT_FULL      = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    private val CUT_PARTIAL   = byteArrayOf(0x1D, 0x56, 0x42, 0x01)
    private val FEED_LINE     = byteArrayOf(0x0A)
    private val ALIGN_LEFT    = byteArrayOf(0x1B, 0x61, 0x00)
    private val ALIGN_CENTER  = byteArrayOf(0x1B, 0x61, 0x01)
    private val ALIGN_RIGHT   = byteArrayOf(0x1B, 0x61, 0x02)
    private val BOLD_ON       = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF      = byteArrayOf(0x1B, 0x45, 0x00)
    private val DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10)
    private val DOUBLE_BOTH   = byteArrayOf(0x1B, 0x21, 0x30)
    private val NORMAL_SIZE   = byteArrayOf(0x1B, 0x21, 0x00)
    private val UNDERLINE_ON  = byteArrayOf(0x1B, 0x2D, 0x01)
    private val UNDERLINE_OFF = byteArrayOf(0x1B, 0x2D, 0x00)
    private val FEED_3        = byteArrayOf(0x1B, 0x64, 0x03)

    private const val COLS = 32  // standard 58mm paper = 32 chars

    // ── Public builder ────────────────────────────────────────────────────

    /**
     * Build print data from JSON structure:
     * {
     *   "title":    "SOLOSMARTPEDIA",
     *   "subtitle": "Struk Transaksi",
     *   "date":     "09/06/2025 21:00",
     *   "items":    [{"label":"Produk","value":"Pulsa 50K"}, ...],
     *   "total":    "Rp 50.000",
     *   "footer":   "Terima kasih!",
     *   "cut":      true
     * }
     */
    fun buildFromJson(json: String): ByteArray {
        val obj = runCatching { JSONObject(json) }.getOrNull()
            ?: return buildPlain(json)

        val buf = mutableListOf<ByteArray>()

        buf += INIT

        // Header
        val title = obj.optString("title", "")
        if (title.isNotEmpty()) {
            buf += ALIGN_CENTER
            buf += BOLD_ON + DOUBLE_BOTH
            buf += text(title)
            buf += NORMAL_SIZE + BOLD_OFF
            buf += FEED_LINE
        }

        val subtitle = obj.optString("subtitle", "")
        if (subtitle.isNotEmpty()) {
            buf += ALIGN_CENTER
            buf += text(subtitle)
            buf += FEED_LINE
        }

        val date = obj.optString("date", "")
        if (date.isNotEmpty()) {
            buf += ALIGN_CENTER
            buf += text(date)
            buf += FEED_LINE
        }

        if (title.isNotEmpty() || subtitle.isNotEmpty()) {
            buf += ALIGN_LEFT
            buf += text(divider('-'))
        }

        // Items
        val items = obj.optJSONArray("items")
        if (items != null) {
            buf += ALIGN_LEFT
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val label = item.optString("label", "")
                val value = item.optString("value", "")
                val bold  = item.optBoolean("bold", false)
                if (bold) buf += BOLD_ON
                buf += text(rowLabelValue(label, value))
                if (bold) buf += BOLD_OFF
            }
        }

        // Total
        val total = obj.optString("total", "")
        if (total.isNotEmpty()) {
            buf += ALIGN_LEFT
            buf += text(divider('-'))
            buf += BOLD_ON
            buf += text(rowLabelValue("TOTAL", total))
            buf += BOLD_OFF
            buf += text(divider('='))
        }

        // Footer
        val footer = obj.optString("footer", "")
        if (footer.isNotEmpty()) {
            buf += FEED_LINE
            buf += ALIGN_CENTER
            buf += text(footer)
        }

        buf += FEED_3

        if (obj.optBoolean("cut", true)) {
            buf += CUT_PARTIAL
        }

        return buf.reduce { a, b -> a + b }
    }

    /** Print raw plain text */
    fun buildPlain(content: String): ByteArray =
        INIT + text(content) + FEED_3 + CUT_PARTIAL

    /** Test print */
    fun buildTestPage(): ByteArray {
        val buf = mutableListOf<ByteArray>()
        buf += INIT
        buf += ALIGN_CENTER + BOLD_ON + DOUBLE_BOTH
        buf += text("SOLOSMARTPEDIA")
        buf += NORMAL_SIZE + BOLD_OFF + FEED_LINE
        buf += ALIGN_CENTER
        buf += text("** TES PRINTER **")
        buf += FEED_LINE
        buf += ALIGN_LEFT
        buf += text(divider('-'))
        buf += text(rowLabelValue("Koneksi", "OK"))
        buf += text(rowLabelValue("Status", "Siap"))
        buf += text(divider('-'))
        buf += FEED_LINE
        buf += ALIGN_CENTER
        buf += text("Printer siap digunakan!")
        buf += FEED_3
        buf += CUT_PARTIAL
        return buf.reduce { a, b -> a + b }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun text(s: String): ByteArray = (s + "\n").toByteArray(Charsets.ISO_8859_1)

    private fun divider(ch: Char): String = ch.toString().repeat(COLS)

    private fun rowLabelValue(label: String, value: String): String {
        val maxLabel = COLS - value.length - 1
        val paddedLabel = if (label.length > maxLabel)
            label.substring(0, maxLabel) else label
        val spaces = COLS - paddedLabel.length - value.length
        return paddedLabel + " ".repeat(maxOf(1, spaces)) + value
    }
}

private operator fun ByteArray.plus(other: ByteArray): ByteArray {
    val result = ByteArray(size + other.size)
    copyInto(result)
    other.copyInto(result, size)
    return result
}
