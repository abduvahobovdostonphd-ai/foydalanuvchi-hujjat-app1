package uz.hujjat.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

data class Entry(
    val docId: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long,
    val isDir: Boolean
)

object Files {
    val known = setOf(
        "pdf", "docx", "dotx", "doc", "xlsx", "xls", "pptx", "ppsx", "ppt",
        "odt", "ott", "ods", "odp", "txt", "csv", "tsv", "md", "json", "xml", "log"
    )

    fun kindOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "doc", "docx", "dotx", "odt", "ott" -> "Hujjat"
        "xls", "xlsx", "ods", "csv", "tsv" -> "Jadval"
        "ppt", "pptx", "ppsx", "odp" -> "Slayd"
        "pdf" -> "PDF"
        else -> "Matn"
    }

    /** Tanlangan papka (SAF) ichidagi fayllar va ichki papkalar. */
    fun list(ctx: Context, tree: Uri, parentDocId: String?): List<Entry> {
        val docId = parentDocId ?: DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val out = ArrayList<Entry>()
        val cols = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_MIME_TYPE
        )
        ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val dir = c.getString(4) == Document.MIME_TYPE_DIR
                val ext = name.substringAfterLast('.', "").lowercase()
                if (!dir && ext !in known) continue
                out.add(
                    Entry(
                        id,
                        DocumentsContract.buildDocumentUriUsingTree(tree, id),
                        name,
                        c.getLong(2),
                        c.getLong(3),
                        dir
                    )
                )
            }
        }
        return out.sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
    }
}
