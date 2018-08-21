package com.orionoscode.orionaudio.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orionoscode.orionaudio.models.Music
import java.lang.reflect.Type

class MusicPreferences(context: Context) {
    private val STORAGE_NAME: String = "orion.audio.music.preference"
    private var sharedPreferences: SharedPreferences? = null
    private var cont: Context? = context

    public fun storeMusic(musics: ArrayList<Music>) {
        sharedPreferences = cont?.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.putString("MUSIC_LIST", Gson().toJson(musics))?.apply()
    }

    public fun loadMusic(): ArrayList<Music> {
        sharedPreferences = cont?.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        val type: Type = object : TypeToken<ArrayList<Music>>() { }.type
        return Gson().fromJson(sharedPreferences?.getString("MUSIC_LIST", null), type)
    }

    public fun storeMusicIndex(index : Int) {
        sharedPreferences = cont?.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.putInt("MUSIC_INDEX", index)?.apply()
    }

    public fun loadMusicIndex(): Int {
        sharedPreferences = cont?.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        return sharedPreferences!!.getInt("MUSIC_INDEX", -1)
    }

    public fun clearChache() {
        sharedPreferences = cont?.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        sharedPreferences?.edit()?.clear()?.apply()
    }
}