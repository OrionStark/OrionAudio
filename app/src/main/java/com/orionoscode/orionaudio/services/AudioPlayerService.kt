package com.orionoscode.orionaudio.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.orionoscode.orionaudio.R
import com.orionoscode.orionaudio.models.Music
import com.orionoscode.orionaudio.utils.*

class AudioPlayerService : Service(), MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {

    /**
     * Music props
     * musics var is for hold all available musics in the user directory
     * musicIndex var is for indicate the current index of playing music
     * currentMusic var is the on playing music object
     */
    private var musics : ArrayList<Music> = ArrayList()
    private var musicIndex : Int = -1
    private var currentMusic : Music? = null

    /* Private static notificationID */
    companion object {
        private fun getNotificationID(): Int {
            return 546
        }
    }

    /*
     * Sometimes, when you listening to a music. There's possibility that you may got a phone call
     * So, to prevent that, we will create Phone call Listener handler */
    private var callInterupt : Boolean = false
    private var phoneStateListener : PhoneStateListener? = null
    private var telephonyManager : TelephonyManager? = null

    /**
     * iBinder var is for binding the service
     * holdPosition var is for holding music's position when its on paused
     * audioManager var is the AudioManager from Android
     */
    private val iBinder: IBinder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var holdPosition: Int = 0
    private var audioManager : AudioManager? = null

    /**
     * Broadcast Receiver to prevent when user unplug the earphone/headphone
     * When earphone/headphone unattached, we created the pause notification, and paused the song
     */
    private var earPhoneJackReceiver : BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            pauseMusic()
            createStickyNotification(PAUSE_PLAYBACK)
        }
    }

    /**
     * Broadcast Receiver for listener when user choose to play next/prev music
     * When user decide to play next/prev song. It means we will play a new song
     * So, we created the receiver for listen the user state for changing the music
     */
    private var newAudioReceiver : BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            musicIndex = MusicPreferences(applicationContext).loadMusicIndex()
            if ( musicIndex != -1 && musicIndex < musics.size  ) {
                currentMusic = musics[musicIndex]
            } else {
                stopSelf()
            }

            stopMusic()
            mediaPlayer?.reset()
            initPlayer()

        }
    }

    /* Media session props */
    private var mediaSessionManager : MediaSessionManager? = null
    private var mediasession : MediaSession? = null
    private var transportControls: MediaController.TransportControls? = null

    override fun onCreate() {
        super.onCreate()
        phoneCallStateListener() // Invoke the phone call listener
        broadcastUnplugJack() // Invoke the jack state listener
        registerNewAudioListener() // Invoke the music changing listener
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val musicPreferences = MusicPreferences(applicationContext)
            musics = musicPreferences.loadMusic()
            musicIndex = musicPreferences.loadMusicIndex()
            if ( musicIndex != -1 && musicIndex < musics.size ) {
                currentMusic = musics[musicIndex]
            } else {
                stopSelf()
            }
        } catch (e : Exception) {
            stopSelf()
        }
        if ( !askingForAudioFocus() ) {
            stopSelf()
        }
        if ( mediaSessionManager == null ) {
            try {
                initMediaSession()
                initPlayer()
            } catch (e : RemoteException) {
                e.printStackTrace()
                stopSelf()
            }
            createStickyNotification(PLAYING_PLAYBACK)
        }
        handleActionPlayback(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mediasession?.release()
        killNotification()
        return super.onUnbind(intent)
    }

    private fun registerNewAudioListener() {
        val intentFilter = IntentFilter(NEW_MUSIC_CHANGE)
        registerReceiver(newAudioReceiver, intentFilter)
    }

    private fun broadcastUnplugJack() {
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(earPhoneJackReceiver, intentFilter)
    }

    private fun phoneCallStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {

                when(state) {
                    /* When phone call is on ringing state. We should pause the music */
                    TelephonyManager.CALL_STATE_RINGING -> {
                        pauseMusic()
                        callInterupt = true
                    }

                    /* When the phone call has ended and entering IDLE State. We should resume the music */
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if ( mediaPlayer != null ) {
                            if ( callInterupt ) {
                                callInterupt = false
                                resumeMusic()
                            }
                        }
                    }
                }

            }
        }

        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if ( mediaPlayer != null ) {
            stopMusic()
            mediaPlayer?.release()
        }
        removeAudioFocus()

        if ( phoneStateListener != null ) {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }

        unregisterReceiver(earPhoneJackReceiver)
        unregisterReceiver(newAudioReceiver)
        MusicPreferences(applicationContext).clearChache()
    }

    /**
     * Audio Toggle Method
     * On this section you will get basic menu option for music*/
    private fun playMusic() {
        if ( !this.mediaPlayer!!.isPlaying ) {
            this.mediaPlayer!!.start()
        }
    }

    private fun stopMusic() {
        if ( this.mediaPlayer == null ) { return }
        if ( this.mediaPlayer!!.isPlaying ) {
            this.mediaPlayer!!.stop()
        }
    }

    private fun pauseMusic() {
        if ( this.mediaPlayer!!.isPlaying ) {
            this.mediaPlayer!!.pause()
            this.holdPosition = this.mediaPlayer!!.currentPosition
        }
    }

    private fun resumeMusic() {
        if ( !this.mediaPlayer!!.isPlaying ) {
            this.mediaPlayer!!.seekTo(this.holdPosition)
            this.mediaPlayer!!.start()
        }
    }

    private fun nextMusic() {
        if ( musicIndex == musics.size - 1 ) {
            musicIndex = 0
            currentMusic = musics[musicIndex]
        } else {
            currentMusic = musics[++musicIndex]
        }

        MusicPreferences(applicationContext).storeMusicIndex(musicIndex)
        stopMusic()
        mediaPlayer?.reset()
        initPlayer()
    }

    private fun previousMusic() {
        currentMusic = if ( musicIndex == 0 ) {
            musics[musicIndex]
        } else {
            musics[--musicIndex]
        }
        MusicPreferences(applicationContext).storeMusicIndex(musicIndex)
        stopMusic()
        mediaPlayer?.reset()
        initPlayer()
    }

    private fun loopMusic() {

    }

    /**
     * This is where we gonna initialize our media player
     */
    private fun initPlayer() {
        if ( mediaPlayer == null ) {
            mediaPlayer = MediaPlayer()
        }
        mediaPlayer!!.setOnBufferingUpdateListener(this)
        mediaPlayer!!.setOnCompletionListener(this)
        mediaPlayer!!.setOnPreparedListener(this)
        mediaPlayer!!.setOnErrorListener(this)
        mediaPlayer!!.setOnSeekCompleteListener(this)
        mediaPlayer!!.setOnInfoListener(this)

        mediaPlayer!!.reset()

        mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)

        try {
          this.mediaPlayer!!.setDataSource(currentMusic?.data)
        } catch (e : Exception) {
            stopSelf()
        }

        mediaPlayer!!.prepareAsync()
    }

    override fun onBind(p0: Intent?): IBinder {
        return iBinder
    }

    override fun onCompletion(p0: MediaPlayer?) {
        stopMusic()
        killNotification()
        stopSelf()
    }

    override fun onPrepared(p0: MediaPlayer?) {
        playMusic()
    }

    override fun onError(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
        when(p1) {
            MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK
                    -> Log.d("Player Error", "NOT VALID FOR PROGRESSIVE PLAYBACK")
            MediaPlayer.MEDIA_ERROR_SERVER_DIED
                    -> Log.d("Player Error", "SERVER DIED")
            MediaPlayer.MEDIA_ERROR_UNKNOWN
                    -> Log.d("Player Error", "UNKNOWN ERROR")
            else -> {
                Log.d("Player Error", "UNKNOWN ERROR")
            }
        }
        return false
    }

    override fun onSeekComplete(p0: MediaPlayer?) {

    }

    override fun onInfo(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
        return false
    }

    override fun onBufferingUpdate(p0: MediaPlayer?, p1: Int) {

    }

    override fun onAudioFocusChange(p0: Int) {
        when(p0) {

            AudioManager.AUDIOFOCUS_LOSS -> {
                if ( mediaPlayer!!.isPlaying ) { mediaPlayer!!.stop() }
                mediaPlayer!!.release()
                mediaPlayer = null
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if ( mediaPlayer!!.isPlaying ) { mediaPlayer!!.setVolume(0.1f, 0.1f) }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if ( mediaPlayer == null ) {
                    initPlayer()
                } else if ( !mediaPlayer!!.isPlaying ) {
                    mediaPlayer!!.start()
                }

                mediaPlayer!!.setVolume(1.0f, 1.0f)
            }
        }
    }

    private fun askingForAudioFocus() : Boolean {
        this.audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager?
        return (this.audioManager!!.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }

    private fun removeAudioFocus() : Boolean {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager!!.abandonAudioFocus(this)
    }

    /* ===================================================
     * / This is the section for notification manager about the playing music.
     * / It's including MediaSession to allow us for interaction with any media controller
     * / Like volume keys, media button etc.
     * */
    private fun initMediaSession() {
        if ( mediaSessionManager == null ) {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager?
            mediasession = MediaSession(applicationContext, "OrionAudio")
            transportControls = mediasession?.controller?.transportControls
            mediasession?.isActive = true
            mediasession?.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

            mediasession?.setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    resumeMusic()
                    createStickyNotification(PLAYING_PLAYBACK)
                }

                override fun onPause() {
                    super.onPause()
                    pauseMusic()
                    createStickyNotification(PAUSE_PLAYBACK)
                }

                override fun onSkipToNext() {
                    super.onSkipToNext()
                    nextMusic()
                    updateMusicMetaData()
                    createStickyNotification(PLAYING_PLAYBACK)
                }

                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    previousMusic()
                    updateMusicMetaData()
                    createStickyNotification(PLAYING_PLAYBACK)
                }

                override fun onStop() {
                    super.onStop()
                    killNotification()
                    stopSelf()
                }
            })
        } else {
            return
        }
    }

    /**
     * We create notification while we playing the music
     * @param playbackStatus playback status of the music
     */
    private fun createStickyNotification(playbackStatus : String) {
        var notificationIcon = android.R.drawable.ic_media_pause
        var playerToggleIntent: PendingIntent? = null
        when(playbackStatus) {
            PLAYING_PLAYBACK -> {
                notificationIcon = android.R.drawable.ic_media_pause
                playerToggleIntent = playbackAction(1)
            }
            PAUSE_PLAYBACK -> {
                notificationIcon = android.R.drawable.ic_media_play
                playerToggleIntent = playbackAction(0)
            }
        }

        val large_icon : Bitmap = BitmapFactory.decodeResource(resources, R.drawable.music)
        val notificationBuilder : Notification.Builder = Notification.Builder(this)
                .setShowWhen(false)
                .setStyle(Notification.MediaStyle()
                        .setMediaSession(mediasession?.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2))
                .setColor(resources.getColor(R.color.colorPrimary))
                .setLargeIcon(large_icon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentText(currentMusic?.artist)
                .setContentTitle(currentMusic?.album)
                .setContentInfo(currentMusic?.title)
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationIcon, "pause", playerToggleIntent)
                .addAction(android.R.drawable.ic_media_ff, "stop", playbackAction(4))
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2))

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)?.notify(getNotificationID(), notificationBuilder.build())
    }

    private fun killNotification() {
        val notificationManager : NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(getNotificationID())
    }

    private fun playbackAction(actionNumber: Int) : PendingIntent? {
        val playbackAction = Intent(this, AudioPlayerService::class.java)
        when(actionNumber) {
            0 -> {
                playbackAction.action = ACTION_PLAY_MUSIC
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            1 -> {
                playbackAction.action = ACTION_PAUSE_MUSIC
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            2 -> {
                playbackAction.action = ACTION_NEXT_MUSIC
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            3 -> {
                playbackAction.action = ACTION_PREV_MUSIC
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            4 -> {
                playbackAction.action = ACTION_STOP_MUSIC
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
        }
        return null
    }

    private fun handleActionPlayback(playbackAction: Intent?) {
        if ( playbackAction == null || playbackAction.action == null ) {
            return
        }

        when {
            playbackAction.action.equals(ACTION_PLAY_MUSIC, ignoreCase = true) -> transportControls?.play()
            playbackAction.action.equals(ACTION_PAUSE_MUSIC, ignoreCase = true) -> transportControls?.pause()
            playbackAction.action.equals(ACTION_NEXT_MUSIC, ignoreCase = true) -> transportControls?.skipToNext()
            playbackAction.action.equals(ACTION_PREV_MUSIC, ignoreCase = true) -> transportControls?.skipToPrevious()
            playbackAction.action.equals(ACTION_STOP_MUSIC, ignoreCase = true) -> transportControls?.stop()
        }
    }

    private fun updateMusicMetaData() {
        val albumArt : Bitmap = BitmapFactory.decodeResource(resources, R.drawable.music)
        mediasession?.setMetadata(
            MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentMusic?.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, currentMusic?.album)
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentMusic?.title)
                .build()
        )
    }

    /**
     * This is the binder for lifecycle service */
    inner class LocalBinder : Binder() {
        fun getService() : AudioPlayerService? {
            return this@AudioPlayerService
        }
    }
}