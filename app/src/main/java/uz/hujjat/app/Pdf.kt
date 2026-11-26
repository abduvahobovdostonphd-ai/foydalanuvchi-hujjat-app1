package uz.hujjat.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class PdfHolder(ctx: Context, val uri: Uri) {
    private val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("PDF faylini ochib bo'lmadi.")
    private val renderer: PdfRenderer = try {
        PdfRenderer(pfd)
    } catch (e: Exception) {
        pfd.close()
        throw IllegalStateException("PDF ochilmadi. Fayl parol bilan himoyalangan yoki buzilgan bo'lishi mumkin.")
    }
    private val lock = Any()
    private var closed = false
    val count: Int = renderer.pageCount

    fun size(index: Int): Pair<Int, Int> {
        synchronized(lock) {
            val page = renderer.openPage(index)
            try {
                return page.width to page.height
            } finally {
                page.close()
            }
        }
    }

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

    fun close() {
        synchronized(lock) {
            if (!closed) {
                closed = true
                renderer.close()
                pfd.close()
            }
        }
    }
}

/**
 * PDF asboblari. Yangi PDF sahifalar rasm ko'rinishida yoziladi, shuning uchun
 * hajmi kattaroq bo'lishi va matni belgilanmasligi mumkin.
 */
object PdfTools {
    class Builder {
        private val doc = PdfDocument()
        private var n = 0

        fun add(b: Bitmap, wPt: Int, hPt: Int) {
            val info = PdfDocument.PageInfo.Builder(wPt, hPt, ++n).create()
            val page = doc.startPage(info)
            page.canvas.drawBitmap(b, null, Rect(0, 0, wPt, hPt), null)
            doc.finishPage(page)
        }

        fun finish(): ByteArray {
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            doc.close()
            return out.toByteArray()
        }
    }

    fun extract(h: PdfHolder, pages: List<Int>): ByteArray {
        val b = Builder()
        for (i in pages) {
            val (w, hh) = h.size(i)
            val bmp = h.render(i, 1240) ?: continue
            b.add(bmp, w, hh)
            bmp.recycle()
        }
        return b.finish()
    }

    fun merge(ctx: Context, uris: List<Uri>): ByteArray {
        val b = Builder()
        for (u in uris) {
            val h = PdfHolder(ctx, u)
            try {
                for (i in 0 until h.count) {
                    val (w, hh) = h.size(i)
                    val bmp = h.render(i, 1240) ?: continue
                    b.add(bmp, w, hh)
                    bmp.recycle()
                }
            } finally {
                h.close()
            }
        }
        return b.finish()
    }

    fun photoToPdf(file: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(file.path, opts)
            ?: throw IllegalStateException("Surat o'qilmadi.")
        val rot = when (
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rot != 0f) {
            val m = Matrix().apply { postRotate(rot) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val b = Builder()
        b.add(bmp, 595, (595f * bmp.height / bmp.width).toInt().coerceAtLeast(1))
        return b.finish()
    }
}

/** PDF sahifalarini ko'rsatadi. Ikki barmoq bilan yaqinlashtirish, ikki marta bosish bilan kattalashtirish. */
@Composable
fun PdfView(holder: PdfHolder) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val wPx = constraints.maxWidth.toFloat()
        val hDp = maxHeight
        val wDp = maxWidth

        fun clampX(x: Float, s: Float): Float = x.coerceIn(-(wPx * (s - 1f)), 0f)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { p ->
                        if (scale > 1.05f) {
                            listState.dispatchRawDelta(p.y * (1f / scale - 1f))
                            scale = 1f
                            offX = 0f
                        } else {
                            val ns = 2.5f
                            listState.dispatchRawDelta(p.y * (1f - 1f / ns))
                            offX = clampX(p.x - (p.x - offX) * ns, ns)
                            scale = ns
                        }
                    })
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = ev.changes.count { it.pressed }
                            if (pressed >= 2) {
                                val z = ev.calculateZoom()
                                val pan = ev.calculatePan()
                                val c = ev.calculateCentroid()
                                val ns = (scale * z).coerceIn(1f, 5f)
                                listState.dispatchRawDelta(c.y * (1f / scale - 1f / ns))
                                offX = clampX(c.x - (c.x - offX) * (ns / scale) + pan.x, ns)
                                scale = ns
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (scale > 1f) {
                                val pan = ev.calculatePan()
                                offX = clampX(offX + pan.x, scale)
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
        ) {
            val renderWidth = if (scale > 1.3f) 2160 else 1080
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(wDp)
                    .height(hDp / scale)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offX
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                items(holder.count) { i -> PageImage(holder, i, renderWidth) }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            Text(
                "${listState.firstVisibleItemIndex + 1} / ${holder.count}   ·   ${(scale * 100).toInt()}%",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PageImage(holder: PdfHolder, index: Int, width: Int) {
    var bmp by remember(holder, index) { mutableStateOf<Bitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(holder, index, width) {
        val r = withContext(Dispatchers.Default) {
            try {
                holder.render(index, width)
            } catch (_: Exception) {
                null
            }
        }
        if (r != null) bmp = r
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
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

/** Sahifalar to'ri: ajratish uchun sahifalarni tanlash. */
@Composable
fun PagesGrid(holder: PdfHolder, selected: Set<Int>, onToggle: (Int) -> Unit) {
    val line = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
    val accent = MaterialTheme.colorScheme.primary
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(holder.count) { i ->
            val on = i in selected
            val bmp by produceState<Bitmap?>(null, holder, i) {
                value = withContext(Dispatchers.Default) {
                    try {
                        holder.render(i, 400)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .border(if (on) 3.dp else 1.dp, if (on) accent else line, RoundedCornerShape(8.dp))
                    .clickable { onToggle(i) },
                contentAlignment = Alignment.Center
            ) {
                val b = bmp
                if (b != null) {
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = "${i + 1}-sahifa",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator()
                }
                Text(
                    "${i + 1}" + if (on) "  ✓" else "",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
                    fontSize = 12.sp,
                    color = if (on) accent else MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
