package uz.hujjat.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Share {
    const val AUTHORITY = "uz.hujjat.app.fileprovider"

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
        "odp" -> "application/vnd.oasis.opendocument.presentation"
        "csv" -> "text/csv"
        "txt", "md" -> "text/plain"
        else -> "*/*"
    }

    /** Telefondagi mavjud faylni boshqa ilova (Telegram, WhatsApp, pochta...) orqali yuborish. */
    fun send(ctx: Context, uri: Uri, name: String) {
        val mime = ctx.contentResolver.getType(uri) ?: mimeOf(name)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Yuborish").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    /** Hali saqlanmagan yoki tahrirlangan hujjatni vaqtinchalik fayl qilib yuborish. */
    fun sendBytes(ctx: Context, bytes: ByteArray, name: String) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, name.replace('/', '_'))
        f.writeBytes(bytes)
        send(ctx, FileProvider.getUriForFile(ctx, AUTHORITY, f), name)
    }
}
