package com.orionoscode.orionaudio.view

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import com.orionoscode.orionaudio.R
import com.orionoscode.orionaudio.models.Music
import com.orionoscode.orionaudio.utils.MusicPreferences

class SplashScreen : AppCompatActivity() {
    var musicList : ArrayList<Music> = ArrayList()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        supportActionBar?.hide()
        Handler().postDelayed({
            kotlin.run {
                // askPermissionToAccessStorage()
                startActivity(Intent(this@SplashScreen, HomeActivity::class.java))
            }
        }, 3000)
    }

    private fun testLoadAudio() {
        val contResolver : ContentResolver = contentResolver
        val uri : Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection : String = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
        val sortOrder : String = MediaStore.Audio.Media.TITLE + " ASC"
        val cursor : Cursor = contResolver.query(uri, null, selection, null, sortOrder)
        if ( cursor.count > 0 ) {
            while (cursor.moveToNext()) {
                musicList.add(
                    Music (
                        cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)),
                        cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)),
                        cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)),
                        cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)) )
                )
            }
        }
        cursor.close()
        MusicPreferences(this@SplashScreen).storeMusic(musicList)
    }

    private fun askPermissionToAccessStorage() {
        if ( Build.VERSION.SDK_INT >= 23 ) {
            if ( checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ) {
                ActivityCompat.requestPermissions(this@SplashScreen,
                        Array(1){android.Manifest.permission.READ_EXTERNAL_STORAGE}, 3422)
            } else {
                testLoadAudio()
                startActivity(Intent(this@SplashScreen, HomeActivity::class.java))
                finish()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode) {
            3422 -> {
                if ( grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED ) {
                    finish()
                } else {
                    testLoadAudio()
                    startActivity(Intent(this@SplashScreen, HomeActivity::class.java))
                    finish()
                }
            }
        }
    }
}
