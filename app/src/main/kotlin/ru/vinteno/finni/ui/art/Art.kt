package ru.vinteno.finni.ui.art

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Слой из ассетов, обрезанный по непрозрачному: `left`, `top` — где он лежал на исходной канве
 * `canvasWidth × canvasHeight`. Слои Финни — полные канвы 640 × 960, почти пустые: без обрезки
 * один окрас занимал бы в памяти около 30 МБ, с обрезкой — около 2 МБ.
 */
class ArtImage(val bitmap: ImageBitmap, val left: Int, val top: Int, val canvasWidth: Int, val canvasHeight: Int)

/**
 * Картинки из `art/app` (в сборке — корень ассетов) по имени без `.png`. Декодирование — в фоне,
 * на двух потоках ниже главного; готовое лежит в памяти до конца процесса. Пока картинки нет или
 * файла нет — `null`, вызывающий рисует заглушку. Чтение [image] и [missing] из Compose подписывает
 * на готовность: экран перерисуется сам.
 */
object Art {
    // В Robolectric (снимки экранов, «ребёнок») декодируем сразу: снимок не должен зависеть от того,
    // успел ли фоновый поток.
    private val sync = Build.FINGERPRINT == "robolectric"

    @Volatile private var app: Context? = null
    @Volatile private var assets: AssetManager? = null
    @Volatile private var files: Set<String> = emptySet()

    private val ready = ConcurrentHashMap<String, ArtImage>()
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val jobs = ConcurrentHashMap<String, Deferred<ArtImage?>>()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val scope by lazy {
        val pool = Executors.newFixedThreadPool(2) { r ->
            Thread(r, "finni-art").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }
        CoroutineScope(SupervisorJob() + pool.asCoroutineDispatcher())
    }

    /** Меняется на главном потоке, когда картинка готова или не прочиталась. */
    private var version by mutableIntStateOf(0)

    /** Точки поворота и рамки Финни; `null` — файла нет или он не читается, тогда Финни — заглушка. */
    @Volatile var finni: FinniSpec? = null
        private set

    /** Дёшево и можно звать на каждой композиции: работа — только при первом вызове. */
    fun init(context: Context) {
        val a = context.applicationContext
        if (app === a) return
        synchronized(this) {
            if (app === a) return
            val am = a.assets
            files = am.list("")?.toSet().orEmpty()
            // Файл в 1 КБ читается один раз и сразу: рамка головы нужна уже для разметки.
            finni = if (FinniSpec.FILE in files) runCatching { FinniSpec.parse(am.open(FinniSpec.FILE).use { it.readBytes().decodeToString() }) }.getOrNull() else null
            assets = am
            app = a
        }
    }

    /** Файла нет в сборке или он не декодировался — рисовать заглушку. */
    fun missing(name: String): Boolean {
        version
        return "$name.png" !in files || name in failed
    }

    /** Картинка, если готова; иначе `null` и загрузка в фоне. */
    fun image(name: String): ArtImage? {
        version
        ready[name]?.let { return it }
        if (missing(name)) return null
        request(name)
        return ready[name]
    }

    /** Для проверок и предзагрузки: дождаться картинки. */
    suspend fun load(name: String): ArtImage? = if (missing(name)) null else request(name).await()

    /** Подгрузить в фоне по порядку, по одной, не мешая тому, что нужно экрану прямо сейчас. */
    fun warmUp(names: List<String>) {
        if (sync) return
        scope.launch { names.forEach { if (!missing(it)) request(it).await() } }
    }

    private fun request(name: String): Deferred<ArtImage?> = jobs.computeIfAbsent(name) {
        if (sync) CompletableDeferred(decodeSafe(name))
        else scope.async { decodeSafe(name).also { main.post { version++ } } }
    }

    private fun decodeSafe(name: String): ArtImage? {
        val img = runCatching { decode(name) }.getOrNull()
        if (img == null) failed += name else ready[name] = img
        return img
    }

    private fun decode(name: String): ArtImage {
        val am = checkNotNull(assets)
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val src = am.open("$name.png").use { BitmapFactory.decodeStream(it, null, opts) } ?: error("не картинка: $name")
        val w = src.width
        val h = src.height
        val r = opaqueBounds(src)
        val bmp = if (r == null || (r[2] - r[0] == w && r[3] - r[1] == h)) src
        else Bitmap.createBitmap(src, r[0], r[1], r[2] - r[0], r[3] - r[1]).also { if (it !== src) src.recycle() }
        // Мип-уровни: голова 24 dp из слоя в 470 px без них рябит.
        bmp.setHasMipMap(true)
        bmp.prepareToDraw()
        return ArtImage(bmp.asImageBitmap(), r?.get(0) ?: 0, r?.get(1) ?: 0, w, h)
    }

    /** Рамка непрозрачного `[left, top, right, bottom)`; `null` — обрезать нечего. */
    private fun opaqueBounds(b: Bitmap): IntArray? {
        if (!b.hasAlpha()) return null
        val w = b.width
        val row = IntArray(w)
        var top = -1; var bottom = -1; var left = w; var right = -1
        for (y in 0 until b.height) {
            b.getPixels(row, 0, w, 0, y, w, 1)
            var first = -1; var last = -1
            for (x in 0 until w) if (row[x] ushr 24 != 0) { if (first < 0) first = x; last = x }
            if (first >= 0) {
                if (top < 0) top = y
                bottom = y
                if (first < left) left = first
                if (last > right) right = last
            }
        }
        return if (top < 0) null else intArrayOf(left, top, right + 1, bottom + 1)
    }
}
