package com.xiaokai.live

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class Channel(val name: String, val url: String, val group: String = "未分类", var favorite: Boolean = false)

class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(8, 13, 25)
    private val panel = Color.rgb(17, 27, 47)
    private val accent = Color.rgb(91, 140, 255)
    private val mint = Color.rgb(0, 215, 194)
    private val channels = mutableListOf<Channel>()
    private var visibleChannels = listOf<Channel>()
    private var current: Channel? = null
    private var favoritesOnly = false
    private var fullScreen = false
    private var searchText = ""
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var listView: ListView
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var listAdapter: ArrayAdapter<String>
    private lateinit var root: LinearLayout

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        player = ExoPlayer.Builder(this).build()
        showSplash()
    }

    private fun showSplash() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            setPadding(28)
        }
        val mark = TextView(this).apply {
            text = "▶"
            textSize = 38f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(accent)
            val size = (88 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { bottomMargin = 22 }
        }
        val title = text("小凯直播", 30f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        val subtitle = text("LIVE  ·  YOUR CHANNELS", 11f, mint, true).apply { gravity = Gravity.CENTER; letterSpacing = .18f }
        val loading = text("正在启动…", 12f, Color.LTGRAY).apply { gravity = Gravity.CENTER; setPadding(0, 32, 0, 0) }
        splash.addView(mark); splash.addView(title); splash.addView(subtitle); splash.addView(loading)
        setContentView(splash)
        Handler(Looper.getMainLooper()).postDelayed({ buildHome() }, 850)
    }

    private fun buildHome() {
        loadSaved()
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(12) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = text("小凯直播", 22f, Color.WHITE, true)
        val spacer = Space(this)
        val full = button(if (fullScreen) "退出全屏" else "⛶ 全屏", panel) { fullScreen = !fullScreen; buildHome(); applyImmersive() }
        val add = button("＋ 导入", accent) { showImportDialog() }
        header.addView(brand); header.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f)); header.addView(full); header.addView(add)
        root.addView(header, LinearLayout.LayoutParams(-1, 52))

        val playerCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            player = this@MainActivity.player
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
        }
        playerCard.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
        status = text("选择频道开始播放", 12f, Color.LTGRAY).apply { setPadding(12, 8, 12, 8); setBackgroundColor(panel) }
        playerCard.addView(status)

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 10, 0, 8) }
        val search = EditText(this).apply {
            hint = "搜索频道"
            setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); textSize = 13f
            setSingleLine(true); setPadding(12)
            setBackgroundColor(panel)
            addTextChangedListener(SimpleTextWatcher { searchText = it; refreshList() })
        }
        val fav = button("☆ 收藏", panel) { favoritesOnly = !favoritesOnly; refreshList() }
        tools.addView(search, LinearLayout.LayoutParams(0, 44, 1f).apply { rightMargin = 8 })
        tools.addView(fav, LinearLayout.LayoutParams(-2, 44))

        count = text("频道列表", 13f, mint, true)
        listView = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.rgb(34, 47, 70))
            dividerHeight = 1
            setBackgroundColor(panel)
            setOnItemClickListener { _, _, position, _ -> play(visibleChannels[position]) }
            setOnItemLongClickListener { _, _, position, _ -> toggleFavorite(visibleChannels[position]); true }
        }
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = listAdapter

        val listArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listArea.addView(tools)
        listArea.addView(count, LinearLayout.LayoutParams(-1, 30))
        listArea.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (fullScreen) {
            root.addView(playerCard, LinearLayout.LayoutParams(-1, 0, 1f))
        } else if (isLandscape) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(playerCard, LinearLayout.LayoutParams(0, 0, 1.45f).apply { rightMargin = 10 })
            row.addView(listArea, LinearLayout.LayoutParams(0, 0, 1f))
            root.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            root.addView(playerCard, LinearLayout.LayoutParams(-1, 0, 0.9f).apply { bottomMargin = 8 })
            root.addView(listArea, LinearLayout.LayoutParams(-1, 0, 1.1f))
        }
        setContentView(root)
        applyImmersive()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                status.text = "播放失败：${error.errorCodeName}，请检查直播源或网络"
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) status.text = "正在缓冲…"
                if (playbackState == Player.STATE_READY) status.text = "正在播放：${current?.name ?: "直播"}"
            }
        })
        refreshList()
        if (channels.isEmpty()) status.text = "欢迎使用小凯直播｜点击右上角导入 TXT / M3U 直播源"
    }

    private fun showImportDialog() {
        val options = arrayOf("从手机导入 TXT / M3U 文件", "从网络 URL 导入")
        AlertDialog.Builder(this).setTitle("添加直播源").setItems(options) { _, which ->
            if (which == 0) openFile.launch(arrayOf("text/*", "application/octet-stream", "application/x-mpegURL", "*/*"))
            else showUrlDialog()
        }.show()
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply { hint = "https://example.com/playlist.m3u"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("网络直播源地址").setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("导入") { _, _ ->
                val address = input.text.toString().trim()
                if (address.startsWith("http://") || address.startsWith("https://")) fetchPlaylist(address)
                else toast("请输入有效的 http 或 https 地址")
            }.show()
    }

    private fun importFile(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()
            val content = decodeText(bytes)
            val parsed = parsePlaylist(content)
            if (parsed.isEmpty()) toast("没有识别到频道，请检查文件格式")
            else { channels.addAll(parsed); save(); refreshList(); toast("成功导入 ${parsed.size} 个频道") }
        } catch (e: Exception) { toast("导入失败：${e.message ?: "文件读取错误"}") }
    }

    private fun fetchPlaylist(address: String) {
        status.text = "正在下载直播列表…"
        Thread {
            try {
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout = 12000; connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "XiaoKaiLive/1.0")
                val bytes = connection.inputStream.use { it.readBytes() }
                val body = decodeText(bytes)
                connection.disconnect()
                val parsed = parsePlaylist(body)
                runOnUiThread {
                    if (parsed.isEmpty()) toast("列表为空或格式不支持")
                    else { channels.addAll(parsed); save(); refreshList(); toast("成功导入 ${parsed.size} 个频道") }
                }
            } catch (e: Exception) { runOnUiThread { toast("网络导入失败：${e.message ?: "连接错误"}") } }
        }.start()
    }

    private fun parsePlaylist(raw: String): List<Channel> {
        val result = mutableListOf<Channel>()
        val lines = raw.removePrefix("\uFEFF").lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.any { it.startsWith("#EXTM3U", true) || it.startsWith("#EXTINF", true) }) {
            var name = "未知频道"; var group = "未分类"
            for (line in lines) {
                if (line.startsWith("#EXTINF", true)) {
                    name = line.substringAfterLast(',').trim().ifBlank { "未知频道" }
                    group = Regex("group-title=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1) ?: "未分类"
                } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                    result.add(Channel(name, line, group)); name = "未知频道"; group = "未分类"
                }
            }
        } else {
            var group = "未分类"
            for (line in lines) {
                if (line.contains("#genre#", true)) { group = line.substringBefore(",").trim().ifBlank { line.substringAfter("#genre#").trim() }.ifBlank { "未分类" }; continue }
                if (line.startsWith("#") || line.startsWith("//")) continue
                val parts = line.split(',', limit = 2)
                if (parts.size == 2) {
                    val name = parts[0].trim(); val link = parts[1].trim()
                    if (name.isNotBlank() && (link.startsWith("http://") || link.startsWith("https://"))) result.add(Channel(name, link, group))
                } else if (line.startsWith("http://") || line.startsWith("https://")) {
                    result.add(Channel("频道 ${result.size + 1}", line, group))
                }
            }
        }
        return result.distinctBy { it.name + "|" + it.url }
    }

    private fun play(channel: Channel) {
        current = channel
        status.text = "正在连接：${channel.name}"
        player.setMediaItem(MediaItem.fromUri(channel.url)); player.prepare(); player.play()
        refreshList()
    }

    private fun toggleFavorite(channel: Channel) {
        channel.favorite = !channel.favorite; save(); refreshList()
        toast(if (channel.favorite) "已收藏 ${channel.name}" else "已取消收藏")
    }

    private fun refreshList() {
        if (!::listAdapter.isInitialized) return
        visibleChannels = channels.filter {
            (!favoritesOnly || it.favorite) && (searchText.isBlank() || it.name.contains(searchText, true) || it.group.contains(searchText, true))
        }
        listAdapter.clear()
        listAdapter.addAll(visibleChannels.map { (if (it.favorite) "★ " else "") + it.name + "   ·   " + it.group })
        listAdapter.notifyDataSetChanged()
        count.text = "频道列表  ${visibleChannels.size} / ${channels.size}    （长按频道收藏）"
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        return if (utf8.count { it == '\uFFFD' } > 2) runCatching { String(bytes, charset("GB18030")) }.getOrDefault(utf8) else utf8
    }

    private fun applyImmersive() {
        if (fullScreen) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun save() {
        val array = JSONArray()
        channels.forEach { c -> array.put(JSONObject().put("name", c.name).put("url", c.url).put("group", c.group).put("favorite", c.favorite)) }
        getSharedPreferences("xiaokai", MODE_PRIVATE).edit().putString("channels", array.toString()).apply()
    }

    private fun loadSaved() {
        val raw = getSharedPreferences("xiaokai", MODE_PRIVATE).getString("channels", null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                channels.add(Channel(o.optString("name"), o.optString("url"), o.optString("group", "未分类"), o.optBoolean("favorite")))
            }
        } catch (_: Exception) { }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        setOnClickListener { action() }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

private class SimpleTextWatcher(val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
    override fun afterTextChanged(s: android.text.Editable?) {}
}
