package uz.hujjat.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial: Uri? = if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        setContent { Root(initial) }
    }
}

@Composable
fun Root(initial: Uri?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dark by remember { mutableStateOf(Prefs.dark(ctx)) }
    var tab by remember { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf<Loaded?>(null) }
    var startPages by remember { mutableStateOf(false) }
    var busyMsg by remember { mutableStateOf<String?>(null) }
    var recent by remember { mutableStateOf(Recent.get(ctx)) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val toast: (String) -> Unit = { m -> Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show() }

    fun runBusy(msg: String, block: suspend () -> Unit) {
        scope.launch {
            busyMsg = msg
            try {
                block()
            } catch (e: Throwable) {
                toast(e.message ?: "Xatolik yuz berdi.")
            }
            busyMsg = null
        }
    }

    fun open(uri: Uri, pagesFirst: Boolean = false) {
        runBusy("Fayl ochilmoqda...") {
            val l = withContext(Dispatchers.IO) { Parsers.load(ctx, uri) }
            startPages = pagesFirst && l.content is Content.Pdf
            opened = l
            Recent.add(ctx, uri, l.name, Parsers.size(ctx, uri))
            recent = Recent.get(ctx)
        }
    }

    fun persistRead(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }

    fun writePending(uri: Uri?) {
        val b = pending
        pending = null
        if (uri == null || b == null) return
        try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(b) }
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val name = Parsers.displayName(ctx, uri)
            Recent.add(ctx, uri, name, b.size.toLong())
            recent = Recent.get(ctx)
            toast("Saqlandi: $name")
        } catch (e: Exception) {
            toast("Saqlab bo'lmadi")
        }
    }

    val lPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { writePending(it) }
    val lDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { writePending(it) }
    val lXlsx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { writePending(it) }
    val lTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { writePending(it) }

    val save: SaveFn = { kind, name, bytes ->
        pending = bytes
        when (kind) {
            "pdf" -> lPdf.launch(name)
            "docx" -> lDocx.launch(name)
            "xlsx" -> lXlsx.launch(name)
            else -> lTxt.launch(name)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            persistRead(u)
            open(u)
        }
    }
    val pdfSplitPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            persistRead(u)
            open(u, pagesFirst = true)
        }
    }
    val mergePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        if (list.size >= 2) {
            runBusy("PDF fayllar birlashtirilmoqda...") {
                val b = withContext(Dispatchers.IO) { PdfTools.merge(ctx, list) }
                save("pdf", "Birlashtirilgan.pdf", b)
            }
        } else if (list.isNotEmpty()) {
            toast("Kamida 2 ta PDF tanlang")
        }
    }
    val scanFile = remember { File(ctx.cacheDir, "scan/scan.jpg").also { it.parentFile?.mkdirs() } }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            runBusy("PDF yaratilmoqda...") {
                val b = withContext(Dispatchers.IO) { PdfTools.photoToPdf(scanFile) }
                save("pdf", "Skan_${System.currentTimeMillis() / 1000}.pdf", b)
            }
        }
    }

    LaunchedEffect(initial) {
        if (initial != null) open(initial)
    }

    val current = opened
    DisposableEffect(current) {
        onDispose {
            val c = current?.content
            if (c is Content.Pdf) c.holder.close()
        }
    }
    BackHandler(enabled = current != null) { opened = null }

    HujjatTheme(dark) {
        Box(Modifier.fillMaxSize()) {
            if (current != null) {
                Viewer(current, startPages, { opened = null }, save, toast)
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            val items = listOf("⌂" to "Bosh", "▤" to "Fayllar", "✦" to "Asboblar", "⚙" to "Sozlamalar")
                            items.forEachIndexed { i, (icon, label) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Text(icon) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> HomeScreen(
                                recent = recent,
                                onOpenFile = { filePicker.launch(arrayOf("*/*")) },
                                onOpenRecent = { open(Uri.parse(it)) },
                                onShare = { u, n -> Share.send(ctx, Uri.parse(u), n) },
                                onGoFiles = { tab = 1 },
                                onNewDoc = { opened = Loaded("Yangi hujjat.docx", Content.Text(""), null) },
                                onNewSheet = { opened = Loaded("Yangi jadval.xlsx", Content.Table(emptyList()), null) },
                                onNewSlide = { toast("Slayd muharriri keyingi versiyada qo'shiladi") },
                                onNewPdf = { tab = 2 }
                            )
                            1 -> FilesScreen(
                                onOpen = { open(it) },
                                onPickFile = { filePicker.launch(arrayOf("*/*")) },
                                onShare = { u, n -> Share.send(ctx, u, n) }
                            )
                            2 -> ToolsScreen(
                                onMerge = { mergePicker.launch(arrayOf("application/pdf")) },
                                onSplit = { pdfSplitPicker.launch(arrayOf("application/pdf")) },
                                onScan = {
                                    scanFile.parentFile?.mkdirs()
                                    scanLauncher.launch(FileProvider.getUriForFile(ctx, Share.AUTHORITY, scanFile))
                                },
                                onSoon = { toast("Bu funksiya keyingi versiyada qo'shiladi") }
                            )
                            else -> SettingsScreen(
                                dark = dark,
                                onDark = { dark = it; Prefs.setDark(ctx, it) },
                                onSoon = { toast("Bu til keyingi versiyada qo'shiladi") }
                            )
                        }
                    }
                }
            }
            val msg = busyMsg
            if (msg != null) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(msg)
                    }
                }
            }
        }
    }
}
