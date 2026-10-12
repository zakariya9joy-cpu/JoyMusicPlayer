
package com.joymusicplayer

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var songList: LinearLayout
    private lateinit var status: TextView

    private data class Song(
        val title: String,
        val artist: String,
        val uri: Uri
    )

    private val songs = mutableListOf<Song>()
    private var currentIndex = 0

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
            setPadding(8, 16, 8, 16)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun controlButton(label: String, action: String): Button {
            return Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { sendPlaybackAction(action) }
            }
        }

        controls.addView(
            controlButton("⏮ Previous", MusicService.ACTION_PREVIOUS),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        controls.addView(
            controlButton("▶ / ⏸", MusicService.ACTION_TOGGLE),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        controls.addView(
            controlButton("Next ⏭", MusicService.ACTION_NEXT),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val refresh = Button(this).apply {
            text = "গান আবার খুঁজুন"
            setOnClickListener { loadSongs() }
        }

        songList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(songList)
        }

        root.addView(heading)
        root.addView(status)
        root.addView(controls)
        root.addView(refresh)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)
    }

    private fun sendPlaybackAction(action: String) {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }

        try {
            startService(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "আগে একটি গান চালু করো",
                Toast.LENGTH_SHORT
            ).show()
        }
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
            "কোনো গান পাওয়া যায়নি।"
        } else {
            "মোট ${songs.size}টি গান পাওয়া গেছে"
        }

        songs.forEachIndexed { index, song ->
            val button = Button(this).apply {
                text = "${song.title}\n${song.artist}"
                isAllCaps = false
                setOnClickListener {
                    currentIndex = index
                    playSong(index)
                }
            }
            songList.addView(button)
        }
    }

    private fun playSong(index: Int) {
        if (songs.isEmpty() || index !in songs.indices) return

        currentIndex = index

        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_QUEUE
            putStringArrayListExtra(
                MusicService.EXTRA_URIS,
                ArrayList(songs.map { it.uri.toString() })
            )
            putStringArrayListExtra(
                MusicService.EXTRA_TITLES,
                ArrayList(songs.map { it.title })
            )
            putStringArrayListExtra(
                MusicService.EXTRA_ARTISTS,
                ArrayList(songs.map { it.artist })
            )
            putExtra(MusicService.EXTRA_INDEX, currentIndex)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            status.text = "নির্বাচিত গান: ${songs[index].title}"
        } catch (e: Exception) {
            status.text = "গান চালু করা যায়নি: ${e.message}"
        }
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
}
