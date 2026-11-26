package uz.hujjat.app

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("hujjat", Context.MODE_PRIVATE)
    fun tree(ctx: Context): Uri? = p(ctx).getString("tree", null)?.let { Uri.parse(it) }
    fun setTree(ctx: Context, u: Uri) = p(ctx).edit().putString("tree", u.toString()).apply()
    fun dark(ctx: Context): Boolean = p(ctx).getBoolean("dark", false)
    fun setDark(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("dark", v).apply()
}

data class RecentItem(val uri: String, val name: String, val time: Long, val size: Long)

object Recent {
    private const val PREFS = "recent"
    private const val KEY = "items"

    fun get(ctx: Context): List<RecentItem> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.split('\n').mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 4) null
            else RecentItem(p[0], p[1], p[2].toLongOrNull() ?: 0L, p[3].toLongOrNull() ?: 0L)
        }
    }

    fun add(ctx: Context, uri: Uri, name: String, size: Long) {
        val u = uri.toString()
        val clean = name.replace('\n', ' ').replace('|', ' ')
        val list = get(ctx).filter { it.uri != u }.toMutableList()
        list.add(0, RecentItem(u, clean, System.currentTimeMillis(), size))
        val raw = list.take(12).joinToString("\n") { "${it.uri}|${it.name}|${it.time}|${it.size}" }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }
}

fun humanSize(b: Long): String = when {
    b <= 0L -> ""
    b < 1024L -> "$b B"
    b < 1024L * 1024L -> "${b / 1024} KB"
    else -> String.format(Locale.US, "%.1f MB", b / 1048576.0).replace('.', ',')
}

fun humanDate(t: Long): String {
    if (t <= 0L) return ""
    val now = Calendar.getInstance()
    val d = Calendar.getInstance().apply { timeInMillis = t }
    val sameYear = now.get(Calendar.YEAR) == d.get(Calendar.YEAR)
    val diff = now.get(Calendar.DAY_OF_YEAR) - d.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && diff == 0 -> "Bugun"
        sameYear && diff == 1 -> "Kecha"
        else -> SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(t))
    }
}

fun joinMeta(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(" · ")

@Composable
fun HujjatTheme(forceDark: Boolean, content: @Composable () -> Unit) {
    val dark = forceDark || isSystemInDarkTheme()
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

@Composable
fun muted(): Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)

@Composable
fun Badge(name: String) {
    val ext = name.substringAfterLast('.', "").lowercase()
    val c = when (ext) {
        "doc", "docx", "dotx", "odt", "ott" -> Color(0xFF2B6CB0)
        "xls", "xlsx", "ods", "csv", "tsv" -> Color(0xFF2F855A)
        "ppt", "pptx", "ppsx", "odp" -> Color(0xFFC05621)
        "pdf" -> Color(0xFFC53030)
        else -> Color(0xFF6B7A83)
    }
    Box(
        Modifier.size(36.dp).background(c, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(ext.uppercase().take(4), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FileRow(name: String, meta: String, isDir: Boolean, onClick: () -> Unit, onShare: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Badge(if (isDir) "x.dir" else name)
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = muted())
        }
        if (onShare != null) {
            TextButton(onClick = onShare) { Text("⇪", fontSize = 20.sp) }
        }
    }
}

@Composable
fun AppBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Ko'rish oynasi: tepada nomi va orqaga tugmasi, pastda amallar paneli (Yuborish, Saqlash). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("←", fontSize = 22.sp) } },
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp) }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        },
        content = content
    )
}
