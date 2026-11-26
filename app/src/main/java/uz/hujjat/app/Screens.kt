package uz.hujjat.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    recent: List<RecentItem>,
    onOpenFile: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onShare: (String, String) -> Unit,
    onGoFiles: () -> Unit,
    onNewDoc: () -> Unit,
    onNewSheet: () -> Unit,
    onNewSlide: () -> Unit,
    onNewPdf: () -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        AppBar("Hujjat") {
            TextButton(onClick = onOpenFile) { Text("Ochish") }
            TextButton(onClick = onGoFiles) { Text("⌕", fontSize = 20.sp) }
        }
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
                Text(
                    "Reklama yo'q, obuna yo'q. Barcha asboblar bepul va internetsiz ishlaydi.",
                    modifier = Modifier.padding(14.dp),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            Text("Yangi yaratish", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NewButton("x.docx", "Hujjat", Modifier.weight(1f), onNewDoc)
                NewButton("x.xlsx", "Jadval", Modifier.weight(1f), onNewSheet)
                NewButton("x.pptx", "Slayd", Modifier.weight(1f), onNewSlide)
                NewButton("x.pdf", "PDF", Modifier.weight(1f), onNewPdf)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Oxirgi fayllar", fontWeight = FontWeight.SemiBold)
                Text("${recent.size} ta", fontSize = 12.sp, color = muted())
            }
            if (recent.isEmpty()) {
                Text("Hozircha fayl yo'q. \"Ochish\" tugmasi bilan fayl tanlang.", fontSize = 14.sp, color = muted())
            }
            recent.take(8).forEach { r ->
                FileRow(
                    name = r.name,
                    meta = joinMeta(humanDate(r.time), humanSize(r.size)),
                    isDir = false,
                    onClick = { onOpenRecent(r.uri) },
                    onShare = { onShare(r.uri, r.name) }
                )
            }
        }
    }
}

@Composable
private fun NewButton(fileName: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.padding(vertical = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Badge(fileName)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun FilesScreen(onOpen: (Uri) -> Unit, onPickFile: () -> Unit, onShare: (Uri, String) -> Unit) {
    val ctx = LocalContext.current
    var tree by remember { mutableStateOf(Prefs.tree(ctx)) }
    var stack by remember { mutableStateOf(listOf<String>()) }
    var entries by remember { mutableStateOf(listOf<Entry>()) }
    var filter by remember { mutableStateOf("Hammasi") }
    var q by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            Prefs.setTree(ctx, u)
            tree = u
            stack = emptyList()
        }
    }

    LaunchedEffect(tree, stack) {
        val t = tree
        failed = false
        entries = if (t == null) emptyList() else withContext(Dispatchers.IO) {
            try {
                Files.list(ctx, t, stack.lastOrNull())
            } catch (e: Exception) {
                failed = true
                emptyList()
            }
        }
    }
    BackHandler(enabled = stack.isNotEmpty()) { stack = stack.dropLast(1) }

    val cats = listOf("Hammasi", "Hujjat", "Jadval", "Slayd", "PDF", "Matn")
    val shown = entries.filter { e ->
        (e.isDir || filter == "Hammasi" || Files.kindOf(e.name) == filter) &&
            (q.isBlank() || e.name.contains(q.trim(), ignoreCase = true))
    }

    Column {
        AppBar("Fayllar") {
            TextButton(onClick = onPickFile) { Text("Fayl tanlash") }
        }
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            singleLine = true,
            placeholder = { Text("Fayl nomi bo'yicha qidirish") }
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cats.forEach { c -> FilterChip(selected = filter == c, onClick = { filter = c }, label = { Text(c) }) }
        }
        LazyColumn(Modifier.padding(horizontal = 18.dp)) {
            if (tree == null) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Fayllaringiz turgan papkani tanlang (masalan, Download yoki Documents). Ilova faqat shu papkani o'qiydi.", fontSize = 14.sp, lineHeight = 20.sp)
                            Button(onClick = { treePicker.launch(null) }) { Text("Papka tanlash") }
                        }
                    }
                }
            } else {
                if (stack.isNotEmpty()) {
                    item {
                        FileRow("Orqaga", "", true, { stack = stack.dropLast(1) }, null)
                    }
                }
                if (failed) {
                    item { Text("Papkani o'qib bo'lmadi. Qayta tanlang.", color = muted(), modifier = Modifier.padding(vertical = 12.dp)) }
                } else if (shown.isEmpty()) {
                    item { Text("Bu yerda mos fayl topilmadi.", color = muted(), modifier = Modifier.padding(vertical = 12.dp)) }
                }
                items(shown) { e ->
                    FileRow(
                        name = e.name,
                        meta = if (e.isDir) "Papka" else joinMeta(humanDate(e.modified), humanSize(e.size)),
                        isDir = e.isDir,
                        onClick = { if (e.isDir) stack = stack + e.docId else onOpen(e.uri) },
                        onShare = if (e.isDir) null else ({ onShare(e.uri, e.name) })
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Boshqa papka tanlash", fontSize = 13.sp, color = muted())
                        OutlinedButton(onClick = { treePicker.launch(null) }) { Text("Papka") }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsScreen(
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onScan: () -> Unit,
    onSoon: () -> Unit
) {
    data class Tool(val title: String, val hint: String, val ready: Boolean, val action: () -> Unit)
    val tools = listOf(
        Tool("PDF birlashtirish", "Bir nechta PDF'ni bitta qilish", true, onMerge),
        Tool("PDF ajratish", "Sahifalarni alohida fayl qilish", true, onSplit),
        Tool("Skaner", "Kamera bilan hujjatni PDF'ga olish", true, onScan),
        Tool("Konvertor", "DOCX ⇄ PDF, XLSX ⇄ CSV", false, onSoon),
        Tool("Rasmdan matn", "Surat ichidagi matnni ajratib olish", false, onSoon),
        Tool("Parol qo'yish", "PDF va ofis fayllarini himoyalash", false, onSoon)
    )
    Column(Modifier.verticalScroll(rememberScrollState())) {
        AppBar("Asboblar")
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tools.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable(onClick = t.action),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(t.hint, fontSize = 12.sp, color = muted(), lineHeight = 16.sp)
                                if (!t.ready) Text("Tez orada", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(dark: Boolean, onDark: (Boolean) -> Unit, onSoon: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        AppBar("Sozlamalar")
        Column(Modifier.padding(horizontal = 18.dp)) {
            SettingRow("Til", "Ilova interfeysi") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("O'zbekcha") })
                    FilterChip(selected = false, onClick = onSoon, label = { Text("Русский") })
                    FilterChip(selected = false, onClick = onSoon, label = { Text("English") })
                }
            }
            SettingRow("Qorong'i mavzu", "O'chiq bo'lsa, tizim sozlamasiga ergashadi") {
                Switch(checked = dark, onCheckedChange = onDark)
            }
            SettingRow("Reklama", null) { Text("Yo'q", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
            SettingRow("Obuna va to'lov", null) { Text("Yo'q", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
            SettingRow("Hisob bilan kirish", "Talab qilinmaydi") { Switch(checked = false, onCheckedChange = null, enabled = false) }
            SettingRow("Faqat oflayn rejim", "Ilovada internet ruxsati yo'q") { Switch(checked = true, onCheckedChange = null, enabled = false) }
            SettingRow("Versiya", "Prototip asosida") { Text("0.2", color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun SettingRow(title: String, hint: String?, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontSize = 15.sp)
            if (hint != null) Text(hint, fontSize = 12.sp, color = muted())
        }
        trailing()
    }
}
