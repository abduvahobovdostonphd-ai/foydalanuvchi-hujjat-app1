package uz.hujjat.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial: Uri? = if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        setContent {
            HujjatTheme {
                Root(initial)
            }
        }
    }
}

@Composable
fun HujjatTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF4FB6D3),
            onPrimary = Color(0xFF06222B),
            background = Color(0xFF0C1317),
            surface = Color(0xFF142027),
            onBackground = Color(0xFFE6EEF1),
            onSurface = Color(0xFFE6EEF1)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0B6E8A),
            onPrimary = Color.White,
            background = Color(0xFFEEF2F4),
            surface = Color.White,
            onBackground = Color(0xFF12232B),
            onSurface = Color(0xFF12232B)
        )
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

object Recent {
    private const val PREFS = "recent"
    private const val KEY = "items"

    fun get(ctx: Context): List<Pair<String, String>> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.split('\n').filter { it.contains('|') }.map {
            it.substringBefore('|') to it.substringAfter('|')
        }
    }

    fun add(ctx: Context, uri: Uri, name: String) {
        val u = uri.toString()
        val list = get(ctx).filter { it.first != u }.toMutableList()
        list.add(0, u to name.replace('\n', ' ').replace('|', ' '))
        val raw = list.take(10).joinToString("\n") { it.first + "|" + it.second }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }
}

@Composable
fun Root(initial: Uri?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<Loaded?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf(Recent.get(ctx)) }

    fun open(uri: Uri) {
        scope.launch {
            busy = true
            error = null
            try {
                val l = withContext(Dispatchers.IO) { Parsers.load(ctx, uri) }
                loaded = l
                Recent.add(ctx, uri, l.name)
                recent = Recent.get(ctx)
            } catch (e: Throwable) {
                error = e.message ?: "Faylni ochib bo'lmadi."
            }
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            open(uri)
        }
    }

    LaunchedEffect(initial) {
        if (initial != null) open(initial)
    }

    BackHandler(enabled = loaded != null) { loaded = null }

    val current = loaded
    if (current != null) {
        Viewer(current) { loaded = null }
    } else {
        Home(
            recent = recent,
            busy = busy,
            error = error,
            onPick = { picker.launch(arrayOf("*/*")) },
            onOpenRecent = { open(Uri.parse(it)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    recent: List<Pair<String, String>>,
    busy: Boolean,
    error: String?,
    onPick: () -> Unit,
    onOpenRecent: (String) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Hujjat") }) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Reklama yo'q. Obuna yo'q. Hisob shart emas. Ilovada internet ruxsati yo'q, fayllaringiz qurilmadan chiqmaydi.",
                    modifier = Modifier.padding(14.dp),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text("Fayl ochish")
            }
            Text(
                "PDF, DOCX, XLSX, PPTX, ODT, ODS, ODP, TXT, CSV",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                    Text("Fayl ochilmoqda...")
                }
            }
            if (error != null) {
                Text(error, color = Color(0xFFC53030), fontSize = 14.sp)
            }
            if (recent.isNotEmpty()) {
                Text("Oxirgi fayllar", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                LazyColumn {
                    items(recent) { (uri, name) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRecent(uri) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Viewer(doc: Loaded, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("←", fontSize = 20.sp) } },
                title = { Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val c = doc.content) {
                is Content.Text -> TextView(c.text)
                is Content.Table -> TableView(c.rows)
                is Content.Slides -> SlidesView(c.slides)
                is Content.Pdf -> PdfView(c.uri)
            }
        }
    }
}

@Composable
fun TextView(text: String) {
    val lines = remember(text) { text.split('\n') }
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(lines) { l ->
                Text(if (l.isEmpty()) " " else l, fontSize = 16.sp, lineHeight = 24.sp)
            }
        }
    }
}

@Composable
fun SlidesView(slides: List<String>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(slides) { i, s ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Slayd ${i + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(if (s.isBlank()) "(matn yo'q)" else s, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
fun TableView(rows: List<List<String>>) {
    val cols = (rows.maxOfOrNull { it.size } ?: 0).coerceAtMost(60)
    val line = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)
    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        LazyColumn {
            itemsIndexed(rows) { _, r ->
                Row {
                    for (c in 0 until cols) {
                        Text(
                            r.getOrElse(c) { "" },
                            modifier = Modifier
                                .width(120.dp)
                                .border(0.5.dp, line)
                                .padding(6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

class PdfHolder(ctx: Context, uri: Uri) {
    private val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("PDF faylini ochib bo'lmadi.")
    private val renderer = PdfRenderer(pfd)
    private val lock = Any()
    private var closed = false
    val count: Int = renderer.pageCount

    fun render(index: Int, width: Int): Bitmap? {
        synchronized(lock) {
            if (closed) return null
            val page = renderer.openPage(index)
            try {
                val h = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            } finally {
                page.close()
            }
        }
    }

    fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            renderer.close()
            pfd.close()
        }
    }
}

@Composable
fun PdfView(uri: Uri) {
    val ctx = LocalContext.current
    val holder = remember(uri) { PdfHolder(ctx, uri) }
    DisposableEffect(holder) { onDispose { holder.close() } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        items(holder.count) { i -> PageImage(holder, i) }
    }
}

@Composable
fun PageImage(holder: PdfHolder, index: Int) {
    var bmp by remember(holder, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(holder, index) {
        bmp = withContext(Dispatchers.Default) {
            try {
                holder.render(index, 1080)
            } catch (_: Exception) {
                null
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = "${index + 1}-sahifa",
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.7f),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
    }
}
