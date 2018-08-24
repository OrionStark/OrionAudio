package com.orionoscode.orionaudio.view

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import com.orionoscode.orionaudio.R
import com.orionoscode.orionaudio.models.Music
import com.orionoscode.orionaudio.services.AudioPlayerService
import com.orionoscode.orionaudio.utils.MusicPreferences
import com.orionoscode.orionaudio.utils.NEW_MUSIC_CHANGE
import com.orionoscode.orionaudio.viewmodel.MusicViewModel

class HomeActivity : AppCompatActivity() {

    private var audioPlayerService : AudioPlayerService? = null
    var serviceBound : Boolean = false

    private var serviceConn : ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        serviceConn = object : ServiceConnection {

            override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
                val binder : AudioPlayerService.LocalBinder = p1 as AudioPlayerService.LocalBinder
                audioPlayerService = binder.getService()
                serviceBound = true
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                serviceBound = false
            }
        }
        val musicViewModel: MusicViewModel = ViewModelProviders.of(this).get(MusicViewModel::class.java)
        musicViewModel.getMusics(contentResolver)!!.observe(this, Observer<ArrayList<Music>> {
            musics ->
            MusicPreferences(this@HomeActivity).storeMusic(musics!!)
        })
        playMusic(0)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putBoolean("ServiceState", serviceBound)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState!!.getBoolean("ServiceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        if ( serviceBound ) {
            unbindService(serviceConn)
            audioPlayerService?.stopSelf()
        }
    }

    private fun playMusic(index: Int) {
        val musicPreferences = MusicPreferences(this@HomeActivity)
        if ( !serviceBound ) {
            // musicPreferences.storeMusic(musicList)
            musicPreferences.storeMusicIndex(index)


            val playerIntent = Intent(this@HomeActivity, AudioPlayerService::class.java)
            startService(playerIntent)
            bindService(playerIntent, serviceConn, Context.BIND_AUTO_CREATE)
        } else {
            musicPreferences.storeMusicIndex(index)
            val broadcastIntent = Intent(NEW_MUSIC_CHANGE)
            sendBroadcast(broadcastIntent)
        }
    }
}
