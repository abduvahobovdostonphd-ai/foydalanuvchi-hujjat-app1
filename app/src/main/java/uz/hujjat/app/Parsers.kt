package uz.hujjat.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

sealed interface Content {
    data class Text(val text: String) : Content
    data class Table(val rows: List<List<String>>) : Content
    data class Slides(val slides: List<String>) : Content
    data class Pdf(val holder: PdfHolder) : Content
}

/** uri: faylning asl manzili. Yangi (hali saqlanmagan) hujjatda null. */
data class Loaded(val name: String, val content: Content, val uri: Uri?)

/**
 * Fayllarni ichki ko'rinishga o'giradi. Hech qanday tashqi kutubxona va internet ishlatilmaydi.
 * DOCX/XLSX/PPTX/ODT/ODS/ODP fayllari ZIP ichidagi XML'dan o'qiladi.
 * Eski ikkilik formatlar (.doc, .xls, .ppt) hozircha qo'llab-quvvatlanmaydi.
 */
object Parsers {

    private const val MAX_TEXT_BYTES = 5 * 1024 * 1024
    private const val MAX_ROWS = 2000

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: "fayl"
    }

    fun load(ctx: Context, uri: Uri): Loaded {
        val name = displayName(ctx, uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        val content: Content = when (ext) {
            "pdf" -> Content.Pdf(PdfHolder(ctx, uri))

            "docx", "dotx" -> {
                val files = readZip(ctx, uri) { it == "word/document.xml" }
                val xml = files["word/document.xml"] ?: throw IllegalStateException("DOCX fayli buzilgan yoki bo'sh.")
                Content.Text(ooxmlText(xml))
            }

            "pptx", "ppsx" -> {
                val rx = Regex("ppt/slides/slide(\\d+)\\.xml")
                val files = readZip(ctx, uri) { rx.matches(it) }
                val slides = files.entries
                    .sortedBy { rx.matchEntire(it.key)!!.groupValues[1].toInt() }
                    .map { ooxmlText(it.value) }
                if (slides.isEmpty()) throw IllegalStateException("Slaydlar topilmadi.")
                Content.Slides(slides)
            }

            "xlsx" -> {
                val files = readZip(ctx, uri) {
                    it == "xl/sharedStrings.xml" || it == "xl/worksheets/sheet1.xml"
                }
                val sheet = files["xl/worksheets/sheet1.xml"]
                    ?: throw IllegalStateException("Birinchi varaq topilmadi.")
                val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
                Content.Table(sheetRows(sheet, shared))
            }

            "odt", "ott", "odp", "ods" -> {
                val files = readZip(ctx, uri) { it == "content.xml" }
                val xml = files["content.xml"] ?: throw IllegalStateException("Fayl buzilgan yoki bo'sh.")
                Content.Text(odfText(xml))
            }

            "csv", "tsv" -> {
                val lines = readPlain(ctx, uri).lineSequence().take(MAX_ROWS).toList()
                val sep = when {
                    ext == "tsv" -> '\t'
                    lines.firstOrNull()?.contains(';') == true -> ';'
                    else -> ','
                }
                Content.Table(lines.map { l -> l.split(sep).map { it.trim().trim('"') } })
            }

            "txt", "md", "json", "xml", "log", "html", "htm", "ini", "yaml", "yml" ->
                Content.Text(readPlain(ctx, uri))

            "doc", "xls", "ppt" ->
                throw IllegalStateException(
                    "Eski .$ext formati hozircha qo'llab-quvvatlanmaydi. " +
                        "Faylni .${ext}x formatida saqlab qayta urinib ko'ring."
                )

            else -> throw IllegalStateException("\".$ext\" formati hozircha qo'llab-quvvatlanmaydi.")
        }
        return Loaded(name, content, uri)
    }

    fun size(ctx: Context, uri: Uri): Long {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
            }
        } catch (_: Exception) {
        }
        return 0L
    }

    private fun readPlain(ctx: Context, uri: Uri): String {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Faylni ochib bo'lmadi.")
        val cut = if (bytes.size > MAX_TEXT_BYTES) bytes.copyOf(MAX_TEXT_BYTES) else bytes
        return String(cut, Charsets.UTF_8)
    }

    private fun readZip(ctx: Context, uri: Uri, want: (String) -> Boolean): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val ins = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Faylni ochib bo'lmadi.")
        ins.use { raw ->
            ZipInputStream(raw).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (want(e.name)) out[e.name] = zip.readBytes()
                    e = zip.nextEntry
                }
            }
        }
        return out
    }

    private fun parser(bytes: ByteArray): XmlPullParser {
        val f = XmlPullParserFactory.newInstance()
        f.isNamespaceAware = true
        val p = f.newPullParser()
        p.setInput(ByteArrayInputStream(bytes), null)
        return p
    }

    /** Word va PowerPoint: <p> abzaslar ichidagi <t> matnlari. */
    fun ooxmlText(bytes: ByteArray): String {
        val p = parser(bytes)
        val sb = StringBuilder()
        var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "t" -> inT = true
                    "tab" -> sb.append('\t')
                    "br" -> sb.append('\n')
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (inT) sb.append(p.text)
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "t" -> inT = false
                    "p" -> sb.append('\n')
                }
            }
            ev = p.next()
        }
        return sb.toString().trim()
    }

    /** OpenDocument: <text:p> va <text:h> ichidagi matn. */
    fun odfText(bytes: ByteArray): String {
        val p = parser(bytes)
        val sb = StringBuilder()
        var inPara = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "p", "h" -> inPara++
                    "line-break" -> sb.append('\n')
                    "tab" -> sb.append('\t')
                    "s" -> sb.append(' ')
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (inPara > 0) sb.append(p.text)
            } else if (ev == XmlPullParser.END_TAG) {
                if (p.name == "p" || p.name == "h") {
                    if (inPara > 0) inPara--
                    sb.append('\n')
                }
            }
            ev = p.next()
        }
        return sb.toString().trim()
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val p = parser(bytes)
        val list = ArrayList<String>()
        val cur = StringBuilder()
        var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "si" -> cur.setLength(0)
                    "t" -> inT = true
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (inT) cur.append(p.text)
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "t" -> inT = false
                    "si" -> list.add(cur.toString())
                }
            }
            ev = p.next()
        }
        return list
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch.isLetter()) n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return (n - 1).coerceAtLeast(0)
    }

    private fun sheetRows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val p = parser(bytes)
        val rows = ArrayList<List<String>>()
        var row: ArrayList<String>? = null
        var ref = ""
        var type = ""
        var inV = false
        var inT = false
        val cell = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && rows.size < MAX_ROWS) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "row" -> row = ArrayList()
                    "c" -> {
                        ref = p.getAttributeValue(null, "r") ?: ""
                        type = p.getAttributeValue(null, "t") ?: ""
                        cell.setLength(0)
                    }
                    "v" -> inV = true
                    "t" -> inT = true
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (inV || inT) cell.append(p.text)
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val r = row
                        if (r != null) {
                            val col = colIndex(ref)
                            while (r.size < col) r.add("")
                            val raw = cell.toString()
                            val value = if (type == "s") {
                                val idx = raw.trim().toIntOrNull() ?: -1
                                if (idx >= 0 && idx < shared.size) shared[idx] else ""
                            } else raw
                            r.add(value)
                        }
                    }
                    "row" -> {
                        row?.let { rows.add(it) }
                        row = null
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }
}
