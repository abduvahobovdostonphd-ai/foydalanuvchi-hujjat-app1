package uz.hujjat.app

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Jadval yordamchilari: katak nomlari va oddiy formulalar (SUM, AVERAGE, MAX, MIN, COUNT). */
object Sheet {

    fun pad(rows: List<List<String>>, minRows: Int = 20, minCols: Int = 6): List<List<String>> {
        val cols = maxOf(minCols, rows.maxOfOrNull { it.size } ?: 0).coerceAtMost(60)
        val n = maxOf(minRows, rows.size)
        return List(n) { r -> List(cols) { c -> rows.getOrNull(r)?.getOrNull(c) ?: "" } }
    }

    fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    fun ref(r: Int, c: Int): String = colName(c) + (r + 1)

    private fun parseRef(s: String): Pair<Int, Int>? {
        val m = Regex("^([A-Z]+)(\\d+)$").matchEntire(s) ?: return null
        var c = 0
        for (ch in m.groupValues[1]) c = c * 26 + (ch - 'A' + 1)
        val r = m.groupValues[2].toIntOrNull() ?: return null
        return (r - 1) to (c - 1)
    }

    fun fmt(d: Double): String =
        if (d == floor(d) && abs(d) < 1e15) d.toLong().toString()
        else String.format(Locale.US, "%.4f", d).trimEnd('0').trimEnd('.')

    fun eval(grid: List<List<String>>, raw: String, depth: Int = 0): String {
        if (!raw.startsWith("=")) return raw
        if (depth > 3) return "#REF"
        val m = Regex("^=([A-Za-z]+)\\((.+)\\)$").matchEntire(raw.trim()) ?: return "#XATO"
        val fn = m.groupValues[1].uppercase()
        val parts = m.groupValues[2].replace(" ", "").uppercase().split(':')
        val a = parseRef(parts.first()) ?: return "#XATO"
        val b = parseRef(parts.last()) ?: return "#XATO"
        val nums = ArrayList<Double>()
        for (r in minOf(a.first, b.first)..maxOf(a.first, b.first)) {
            for (c in minOf(a.second, b.second)..maxOf(a.second, b.second)) {
                val cell = grid.getOrNull(r)?.getOrNull(c) ?: continue
                val v = eval(grid, cell, depth + 1).replace(',', '.').toDoubleOrNull()
                if (v != null) nums.add(v)
            }
        }
        return when (fn) {
            "SUM" -> fmt(nums.sum())
            "AVERAGE" -> if (nums.isEmpty()) "#DIV/0" else fmt(nums.sum() / nums.size)
            "MAX" -> if (nums.isEmpty()) "0" else fmt(nums.max())
            "MIN" -> if (nums.isEmpty()) "0" else fmt(nums.min())
            "COUNT" -> nums.size.toString()
            else -> "#XATO"
        }
    }

    fun isNumber(grid: List<List<String>>, raw: String): Boolean =
        eval(grid, raw).replace(',', '.').toDoubleOrNull() != null
}
