package com.orionoscode.orionaudio.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orionoscode.orionaudio.models.Music
import java.lang.reflect.Type

class MusicPreferences(context: Context) {
    private val preferenceName: String = "orion.audio.music.preference"
    private var sharedPreferences: SharedPreferences? = null
    private var cont: Context? = context

    fun storeMusic(musics: ArrayList<Music>) {
        sharedPreferences = cont?.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.putString("MUSIC_LIST", Gson().toJson(musics))?.apply()
    }

    fun loadMusic(): ArrayList<Music> {
        sharedPreferences = cont?.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val type: Type = object : TypeToken<ArrayList<Music>>() { }.type
        return Gson().fromJson(sharedPreferences?.getString("MUSIC_LIST", null), type)
    }

    fun storeMusicIndex(index : Int) {
        sharedPreferences = cont?.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.putInt("MUSIC_INDEX", index)?.apply()
    }

    fun loadMusicIndex(): Int {
        sharedPreferences = cont?.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        return sharedPreferences!!.getInt("MUSIC_INDEX", -1)
    }

    fun changeLoopbackPref(indicator: Boolean) {
        sharedPreferences = cont?.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.putBoolean("MUSIC_LOOPBACK", indicator)?.apply()
    }

    fun getMusicLoopStatus(): Boolean {
        sharedPreferences = cont?.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        return sharedPreferences!!.getBoolean("MUSIC_LOOPBACK", false)
    }

    fun clearChache() {
        sharedPreferences = cont?.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.clear()?.apply()
    }
}