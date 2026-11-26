package uz.hujjat.app

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Oddiy DOCX va XLSX fayllarini yozish (tashqi kutubxonasiz). */
object Writers {
    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z ->
            for ((name, content) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return bo.toByteArray()
    }

    fun docx(text: String, bold: Boolean, italic: Boolean, underline: Boolean): ByteArray {
        val rpr = buildString {
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (underline) append("<w:u w:val=\"single\"/>")
        }
        val rprXml = if (rpr.isEmpty()) "" else "<w:rPr>$rpr</w:rPr>"
        val paras = text.split('\n').joinToString("") { line ->
            "<w:p><w:r>$rprXml<w:t xml:space=\"preserve\">${esc(line)}</w:t></w:r></w:p>"
        }
        val types = XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"
        val rels = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"
        val doc = XML + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" +
            paras + "</w:body></w:document>"
        return zip(listOf("[Content_Types].xml" to types, "_rels/.rels" to rels, "word/document.xml" to doc))
    }

    fun xlsx(grid: List<List<String>>, eval: (String) -> String): ByteArray {
        val sb = StringBuilder()
        for (r in grid.indices) {
            val cells = StringBuilder()
            for (c in grid[r].indices) {
                val raw = grid[r][c]
                if (raw.isEmpty()) continue
                val ref = Sheet.ref(r, c)
                if (raw.startsWith("=")) {
                    val v = eval(raw)
                    val f = esc(raw.substring(1))
                    if (v.toDoubleOrNull() != null) cells.append("<c r=\"$ref\"><f>$f</f><v>$v</v></c>")
                    else cells.append("<c r=\"$ref\" t=\"str\"><f>$f</f><v>${esc(v)}</v></c>")
                } else {
                    val n = raw.replace(',', '.')
                    val isNum = n.toDoubleOrNull() != null && !raw.contains(' ') &&
                        !(raw.length > 1 && raw.startsWith("0") && !raw.startsWith("0.") && !raw.startsWith("0,"))
                    if (isNum) cells.append("<c r=\"$ref\"><v>$n</v></c>")
                    else cells.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(raw)}</t></is></c>")
                }
            }
            if (cells.isNotEmpty()) sb.append("<row r=\"${r + 1}\">").append(cells).append("</row>")
        }
        val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val types = XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "</Types>"
        val rels = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"
        val wb = XML + "<workbook xmlns=\"$ns\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
        val wbRels = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "</Relationships>"
        val sheet = XML + "<worksheet xmlns=\"$ns\"><sheetData>$sb</sheetData></worksheet>"
        return zip(
            listOf(
                "[Content_Types].xml" to types,
                "_rels/.rels" to rels,
                "xl/workbook.xml" to wb,
                "xl/_rels/workbook.xml.rels" to wbRels,
                "xl/worksheets/sheet1.xml" to sheet
            )
        )
    }
}
