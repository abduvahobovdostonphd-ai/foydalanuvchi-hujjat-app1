package uz.hujjat.app

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias SaveFn = (kind: String, name: String, bytes: ByteArray) -> Unit

@Composable
fun Viewer(
    doc: Loaded,
    startPages: Boolean,
    onBack: () -> Unit,
    save: SaveFn,
    toast: (String) -> Unit
) {
    when (val c = doc.content) {
        is Content.Text -> DocScreen(doc, c, onBack, save)
        is Content.Table -> SheetScreen(doc, c, onBack, save, toast)
        is Content.Slides -> SlidesScreen(doc, c, onBack)
        is Content.Pdf -> PdfScreen(doc, c.holder, startPages, onBack, save, toast)
    }
}

@Composable
fun DocScreen(doc: Loaded, content: Content.Text, onBack: () -> Unit, save: SaveFn) {
    val ctx = LocalContext.current
    var text by remember(doc) { mutableStateOf(content.text) }
    var bold by remember(doc) { mutableStateOf(false) }
    var italic by remember(doc) { mutableStateOf(false) }
    var underline by remember(doc) { mutableStateOf(false) }
    var dirty by remember(doc) { mutableStateOf(doc.uri == null) }

    val ext = doc.name.substringAfterLast('.', "").lowercase()
    val isWord = ext in setOf("docx", "dotx", "odt", "ott")
    val base = doc.name.substringBeforeLast('.')
    val outName = if (isWord) "$base.docx" else doc.name
    fun bytes(): ByteArray = if (isWord) Writers.docx(text, bold, italic, underline) else text.toByteArray()

    ViewerScaffold(
        title = doc.name,
        onBack = onBack,
        actions = {
            Button(
                onClick = {
                    if (dirty || doc.uri == null) Share.sendBytes(ctx, bytes(), outName)
                    else Share.send(ctx, doc.uri, doc.name)
                },
                modifier = Modifier.weight(1f)
            ) { Text("⇪  Yuborish") }
            OutlinedButton(
                onClick = { save(if (isWord) "docx" else "txt", outName, bytes()) },
                modifier = Modifier.weight(1f)
            ) { Text("Saqlash") }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (isWord) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = bold, onClick = { bold = !bold; dirty = true }, label = { Text("B", fontWeight = FontWeight.Bold) })
                    FilterChip(selected = italic, onClick = { italic = !italic; dirty = true }, label = { Text("I", fontStyle = FontStyle.Italic) })
                    FilterChip(selected = underline, onClick = { underline = !underline; dirty = true }, label = { Text("U", textDecoration = TextDecoration.Underline) })
                }
            }
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it; dirty = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp).padding(16.dp),
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (underline) TextDecoration.Underline else null
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun SheetScreen(doc: Loaded, content: Content.Table, onBack: () -> Unit, save: SaveFn, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    var grid by remember(doc) { mutableStateOf(Sheet.pad(content.rows)) }
    var sel by remember(doc) { mutableStateOf(0 to 0) }
    var dirty by remember(doc) { mutableStateOf(doc.uri == null) }
    val line = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)
    val accent = MaterialTheme.colorScheme.primary
    val base = doc.name.substringBeforeLast('.')
    val outName = "$base.xlsx"
    val cols = grid.firstOrNull()?.size ?: 0

    fun bytes(): ByteArray = Writers.xlsx(grid) { raw -> Sheet.eval(grid, raw) }
    fun setCell(r: Int, c: Int, v: String) {
        grid = grid.mapIndexed { ri, row ->
            if (ri == r) row.mapIndexed { ci, old -> if (ci == c) v else old } else row
        }
        dirty = true
    }
    fun applyFn(fn: String) {
        val (r, c) = sel
        var top = r - 1
        while (top >= 0 && grid[top][c].isNotEmpty() && Sheet.isNumber(grid, grid[top][c])) top--
        top++
        if (top >= r) {
            toast("Formula uchun yuqoridagi kataklarda raqam bo'lishi kerak")
            return
        }
        setCell(r, c, "=$fn(${Sheet.ref(top, c)}:${Sheet.ref(r - 1, c)})")
    }

    ViewerScaffold(
        title = doc.name,
        onBack = onBack,
        actions = {
            Button(
                onClick = {
                    if (dirty || doc.uri == null) Share.sendBytes(ctx, bytes(), outName)
                    else Share.send(ctx, doc.uri, doc.name)
                },
                modifier = Modifier.weight(1f)
            ) { Text("⇪  Yuborish") }
            OutlinedButton(onClick = { save("xlsx", outName, bytes()) }, modifier = Modifier.weight(1f)) { Text("Saqlash") }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("fx", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, color = accent)
                OutlinedTextField(
                    value = grid[sel.first][sel.second],
                    onValueChange = { setCell(sel.first, sel.second, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Katak mazmuni") }
                )
                Text(Sheet.ref(sel.first, sel.second), color = accent, fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (fn in listOf("SUM", "AVERAGE", "MAX", "MIN", "COUNT")) {
                    FilterChip(selected = false, onClick = { applyFn(fn) }, label = { Text(fn) })
                }
                FilterChip(
                    selected = false,
                    onClick = {
                        grid = grid + listOf(List(cols) { "" })
                        dirty = true
                    },
                    label = { Text("+ Qator") }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxHeight()) {
                    Row {
                        Box(Modifier.width(40.dp).border(0.5.dp, line).padding(6.dp)) { Text("") }
                        for (c in 0 until cols) {
                            Text(
                                Sheet.colName(c),
                                modifier = Modifier.width(110.dp).border(0.5.dp, line).padding(6.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = muted()
                            )
                        }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(grid) { r, row ->
                            Row {
                                Text(
                                    "${r + 1}",
                                    modifier = Modifier.width(40.dp).border(0.5.dp, line).padding(6.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    color = muted()
                                )
                                for (c in 0 until cols) {
                                    val isSel = sel.first == r && sel.second == c
                                    val shown = Sheet.eval(grid, row[c])
                                    val numeric = shown.replace(',', '.').toDoubleOrNull() != null
                                    Text(
                                        shown,
                                        modifier = Modifier
                                            .width(110.dp)
                                            .border(if (isSel) 2.dp else 0.5.dp, if (isSel) accent else line)
                                            .background(if (isSel) accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                                            .clickable { sel = r to c }
                                            .padding(6.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 13.sp,
                                        textAlign = if (numeric) TextAlign.End else TextAlign.Start
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SlidesScreen(doc: Loaded, content: Content.Slides, onBack: () -> Unit) {
    val ctx = LocalContext.current
    ViewerScaffold(
        title = doc.name,
        onBack = onBack,
        actions = {
            Button(
                onClick = { doc.uri?.let { Share.send(ctx, it, doc.name) } },
                modifier = Modifier.weight(1f)
            ) { Text("⇪  Yuborish") }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(content.slides) { i, s ->
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
}

@Composable
fun PdfScreen(
    doc: Loaded,
    holder: PdfHolder,
    startPages: Boolean,
    onBack: () -> Unit,
    save: SaveFn,
    toast: (String) -> Unit
) {
    val ctx: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pagesMode by remember(doc) { mutableStateOf(startPages) }
    var sel by remember(doc) { mutableStateOf(setOf<Int>()) }
    var busy by remember(doc) { mutableStateOf(false) }
    val base = doc.name.substringBeforeLast('.')
    val soon = { toast("Bu funksiya keyingi versiyada qo'shiladi") }

    ViewerScaffold(
        title = doc.name,
        onBack = onBack,
        actions = {
            Button(
                onClick = { doc.uri?.let { Share.send(ctx, it, doc.name) } },
                modifier = Modifier.weight(1f)
            ) { Text("⇪  Yuborish") }
            if (pagesMode) {
                OutlinedButton(
                    enabled = sel.isNotEmpty() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val b = withContext(Dispatchers.IO) { PdfTools.extract(holder, sel.sorted()) }
                                save("pdf", base + "_ajratma.pdf", b)
                            } catch (e: Throwable) {
                                toast("PDF yaratib bo'lmadi")
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (busy) "Kuting..." else "PDF yaratish (${sel.size})") }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = !pagesMode, onClick = { pagesMode = false }, label = { Text("Ko'rish") })
                FilterChip(selected = pagesMode, onClick = { pagesMode = true }, label = { Text("Ajratish") })
                FilterChip(selected = false, onClick = soon, label = { Text("Izoh") })
                FilterChip(selected = false, onClick = soon, label = { Text("Imzo") })
                FilterChip(selected = false, onClick = soon, label = { Text("Matn") })
                FilterChip(selected = false, onClick = soon, label = { Text("Qulflash") })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (pagesMode) {
                    PagesGrid(holder, sel) { i -> sel = if (i in sel) sel - i else sel + i }
                } else {
                    PdfView(holder)
                }
            }
        }
    }
}
