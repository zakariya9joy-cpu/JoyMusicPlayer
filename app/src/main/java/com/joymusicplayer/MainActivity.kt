package com.joymusicplayer

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private var player: MediaPlayer? = null
    private var currentUri: Uri? = null
    private lateinit var songList: LinearLayout
    private lateinit var status: TextView

    private data class Song(
        val title: String,
        val artist: String,
        val uri: Uri
    )

    private val songs = mutableListOf<Song>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showInterface()

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            loadSongs()
        } else {
            requestPermissions(arrayOf(permission), 100)
        }
    }

    private fun showInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 16)
            setBackgroundColor(0xFF101010.toInt())
        }

        val heading = TextView(this).apply {
            text = "JOY MUSIC"
            textSize = 27f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "তোমার গান খোঁজা হচ্ছে..."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(8, 20, 8, 20)
        }

        val refresh = Button(this).apply {
            text = "গান আবার খুঁজুন"
            setOnClickListener { loadSongs() }
        }

        songList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = android.widget.ScrollView(this).apply {
            addView(songList)
        }

        root.addView(heading)
        root.addView(status)
        root.addView(refresh)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun loadSongs() {
        songs.clear()
        songList.removeAllViews()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )

        try {
            contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID
                )
                val titleColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE
                )
                val artistColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST
                )

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)
                        ?: "Unknown title"
                    val artist = cursor.getString(artistColumn)
                        ?: "Unknown artist"

                    val uri = ContentUris.withAppendedId(
                        collection, id
                    )

                    songs.add(Song(title, artist, uri))
                }
            }
        } catch (e: Exception) {
            status.text = "গান লোড করা যায়নি: ${e.message}"
            return
        }

        status.text = if (songs.isEmpty()) {
            "কোনো গান পাওয়া যায়নি। ফোনে অডিও ফাইল রাখো।"
        } else {
            "মোট ${songs.size}টি গান পাওয়া গেছে"
        }

        songs.forEach { song ->
            val button = Button(this).apply {
                text = "${song.title}\n${song.artist}"
                isAllCaps = false
                setOnClickListener { playSong(song) }
            }

            songList.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun playSong(song: Song) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, song.uri)
                setOnPreparedListener { mp ->
                    mp.start()
                    currentUri = song.uri
                    status.text = "এখন চলছে: ${song.title}"
                }
                setOnCompletionListener { mp ->
                    mp.release()
                    player = null
                    playNext()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    player = null
                    status.text = "গান চালানো যায়নি"
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            status.text = "প্লেব্যাক ত্রুটি"
            Toast.makeText(
                this, "গান চালানো যায়নি", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun playNext() {
        if (songs.isEmpty()) return
        val index = songs.indexOfFirst { it.uri == currentUri }
        playSong(songs[(index + 1).mod(songs.size)])
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            loadSongs()
        } else {
            status.text = "গান দেখাতে অডিও অনুমতি প্রয়োজন"
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
