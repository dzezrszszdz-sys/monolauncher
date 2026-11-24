package app.monolauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.text.format.DateFormat
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer

data class AppInfo(
    val key: String,          // ComponentName.flattenToString()
    val packageName: String,
    val label: String,
    val sortKey: String,      // ékezet nélküli, kisbetűs név
    val letter: Char,         // A–Z, vagy '#'
)

enum class Screen { HOME, ALL }

data class ScrollRequest(val letter: Char, val id: Int)

private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

class LauncherViewModel(private val app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    // ---------- UI állapot ----------
    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var screen by mutableStateOf(Screen.HOME)
    var searchOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var menuApp by mutableStateOf<AppInfo?>(null)
    var railTouch by mutableStateOf<Char?>(null)
    var activeLetter by mutableStateOf<Char?>(null)
    var scrollRequest by mutableStateOf<ScrollRequest?>(null)
    private var requestCounter = 0

    /** Igaz, amíg egy alkalmazás nyílik: a launcher ilyenkor finoman hátrébb húzódik. */
    var launching by mutableStateOf(false)

    fun open(ctx: Context, view: View, key: String, from: Rect?) {
        if (ctx.launchApp(key, view, from)) launching = true
    }

    // ---------- Beállítások ----------
    var clockStyle by mutableIntStateOf(prefs.getInt("clockStyle", 0))      // 0 = nagy, 1 = egysoros
        private set
    var clockWeight by mutableIntStateOf(prefs.getInt("clockWeight", 200))  // 100..300
        private set
    var clockSize by mutableFloatStateOf(prefs.getFloat("clockSize", 84f))
        private set
    var use24h by mutableStateOf(prefs.getBoolean("use24h", DateFormat.is24HourFormat(app)))
        private set
    var showDate by mutableStateOf(prefs.getBoolean("showDate", true))
        private set
    var showBattery by mutableStateOf(prefs.getBoolean("showBattery", true))
        private set
    var monoIcons by mutableStateOf(prefs.getBoolean("monoIcons", true))
        private set
    var swipeLeftApp by mutableStateOf(prefs.getString("swipeLeft", null))
        private set
    var swipeRightApp by mutableStateOf(prefs.getString("swipeRight", null))
        private set

    var wallpaper by mutableStateOf<ImageBitmap?>(null)
        private set
    var wallpaperDim by mutableFloatStateOf(prefs.getFloat("wallpaperDim", 0.45f))
        private set
    /** 0 = beépített alapháttér, 1 = saját kép, 2 = nincs (tiszta fekete). */
    var wallpaperMode by mutableIntStateOf(prefs.getInt("wallpaperMode", 0))
        private set
    private val wallpaperFile = File(app.filesDir, "wallpaper.jpg")

    var favorites by mutableStateOf(readList("favorites"))
        private set
    var hidden by mutableStateOf(readList("hidden").toSet())
        private set

    // ---------- Származtatott listák ----------
    val visibleApps: List<AppInfo> by derivedStateOf { apps.filter { it.key !in hidden } }
    val favoriteApps: List<AppInfo> by derivedStateOf {
        val map = visibleApps.associateBy { it.key }
        favorites.mapNotNull { map[it] }
    }
    val letters: List<Char> by derivedStateOf { visibleApps.map { it.letter }.distinct() }

    // ---------- Alkalmazások betöltése ----------
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reload()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        reload()
        loadWallpaper()
    }

    /**
     * Első indításkor (üres kedvencek mellett) az alapértelmezett kedvencek beállítása név alapján.
     * Ami nincs telepítve, azt kihagyja. Később a kedvenceid megmaradnak, nem íródnak felül.
     */
    private fun applyDefaultFavorites(list: List<AppInfo>) {
        if (list.isEmpty() || prefs.getBoolean("favoritesInit", false)) return
        if (favorites.isEmpty()) {
            val wanted = listOf(
                setOf("brave"),
                setOf("telefon", "phone"),
                setOf("messages", "uzenetek"),
                setOf("terkep", "maps"),
                setOf("tiktok lite"),
                setOf("messenger"),
                setOf("lite"),
                setOf("slovnaft move"),
                setOf("instagram"),
                setOf("vub banking"),
            )
            val picked = wanted.mapNotNull { names -> list.firstOrNull { it.sortKey in names }?.key }.distinct()
            favorites = picked
            prefs.edit { putString("favorites", picked.joinToString("\n")) }
        }
        prefs.edit { putBoolean("favoritesInit", true) }
    }

    // ---------- Háttérkép ----------
    private fun loadWallpaper() {
        viewModelScope.launch {
            val mode = wallpaperMode
            wallpaper = withContext(Dispatchers.IO) {
                when {
                    mode == 1 && wallpaperFile.exists() ->
                        BitmapFactory.decodeFile(wallpaperFile.absolutePath)?.asImageBitmap()
                    mode == 0 ->
                        BitmapFactory.decodeResource(app.resources, R.drawable.default_wallpaper)?.asImageBitmap()
                    else -> null
                }
            }
        }
    }

    /** A kiválasztott képet lekicsinyítve a saját tárhelyre másolja – nincs szükség engedélyre. */
    fun setWallpaper(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val source = ImageDecoder.createSource(app.contentResolver, uri)
                    val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val w = info.size.width
                        val h = info.size.height
                        val s = minOf(1f, 1800f / maxOf(w, h))
                        decoder.setTargetSize(
                            (w * s).toInt().coerceAtLeast(1),
                            (h * s).toInt().coerceAtLeast(1),
                        )
                    }
                    wallpaperFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    bmp.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
            if (result != null) {
                wallpaper = result
                wallpaperMode = 1
                prefs.edit { putInt("wallpaperMode", 1) }
            }
        }
    }

    /** Tiszta fekete háttér. */
    fun clearWallpaper() {
        wallpaperFile.delete()
        wallpaper = null
        wallpaperMode = 2
        prefs.edit { putInt("wallpaperMode", 2) }
    }

    /** Vissza a beépített alaphátterre. */
    fun resetWallpaper() {
        wallpaperMode = 0
        prefs.edit { putInt("wallpaperMode", 0) }
        loadWallpaper()
    }

    fun updateWallpaperDim(v: Float) { wallpaperDim = v; prefs.edit { putFloat("wallpaperDim", v) } }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(packageReceiver) }
        super.onCleared()
    }

    fun reload() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.Default) {
                val pm = app.packageManager
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0))
                    .filter { it.activityInfo.packageName != app.packageName }
                    .map { ri ->
                        val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                        val label = ri.loadLabel(pm).toString()
                        val sort = normalize(label)
                        val first = sort.firstOrNull()
                        val letter = if (first != null && first.isLetter()) first.uppercaseChar() else '#'
                        AppInfo(cn.flattenToString(), cn.packageName, label, sort, letter)
                    }
                    .distinctBy { it.key }
                    .sortedWith(compareBy<AppInfo>({ it.letter == '#' }, { it.sortKey }))
            }
            IconLoader.clear()
            apps = list
            applyDefaultFavorites(list)
            // Az első ikonok előre betöltése, hogy görgetéskor ne villogjanak.
            val mono = monoIcons
            launch(Dispatchers.Default) {
                list.take(40).forEach { IconLoader.load(app, it.key, mono) }
            }
        }
    }

    // ---------- Navigáció ----------
    fun goHome() {
        screen = Screen.HOME
        searchOpen = false
        settingsOpen = false
        menuApp = null
        railTouch = null
    }

    fun requestLetter(letter: Char) {
        scrollRequest = ScrollRequest(letter, requestCounter++)
    }

    fun swipe(ctx: Context, left: Boolean) {
        val key = if (left) swipeLeftApp else swipeRightApp
        if (key != null) {
            ctx.launchApp(key)
        } else {
            val action = if (left) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA else Intent.ACTION_DIAL
            ctx.safeStart(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---------- Kedvencek / rejtett ----------
    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.edit { putString("favorites", favorites.joinToString("\n")) }
    }

    fun moveFavoriteUp(key: String) {
        val i = favorites.indexOf(key)
        if (i <= 0) return
        val m = favorites.toMutableList()
        m[i] = m[i - 1]
        m[i - 1] = key
        favorites = m
        prefs.edit { putString("favorites", favorites.joinToString("\n")) }
    }

    fun hide(key: String) {
        hidden = hidden + key
        if (key in favorites) favorites = favorites - key
        prefs.edit {
            putString("hidden", hidden.joinToString("\n"))
            putString("favorites", favorites.joinToString("\n"))
        }
    }

    fun unhide(key: String) {
        hidden = hidden - key
        prefs.edit { putString("hidden", hidden.joinToString("\n")) }
    }

    // ---------- Beállítás-módosítók ----------
    fun updateClockStyle(v: Int) { clockStyle = v; prefs.edit { putInt("clockStyle", v) } }
    fun updateClockWeight(v: Int) { clockWeight = v; prefs.edit { putInt("clockWeight", v) } }
    fun updateClockSize(v: Float) { clockSize = v; prefs.edit { putFloat("clockSize", v) } }
    fun updateUse24h(v: Boolean) { use24h = v; prefs.edit { putBoolean("use24h", v) } }
    fun updateShowDate(v: Boolean) { showDate = v; prefs.edit { putBoolean("showDate", v) } }
    fun updateShowBattery(v: Boolean) { showBattery = v; prefs.edit { putBoolean("showBattery", v) } }
    fun updateMonoIcons(v: Boolean) { monoIcons = v; prefs.edit { putBoolean("monoIcons", v) } }
    fun updateSwipeLeft(key: String?) { swipeLeftApp = key; prefs.edit { putString("swipeLeft", key) } }
    fun updateSwipeRight(key: String?) { swipeRightApp = key; prefs.edit { putString("swipeRight", key) } }

    private fun readList(name: String): List<String> =
        prefs.getString(name, "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
}
