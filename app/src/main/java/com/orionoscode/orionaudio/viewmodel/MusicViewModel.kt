package com.orionoscode.orionaudio.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.orionoscode.orionaudio.models.Music

class MusicViewModel : ViewModel() {
    private var musics : MutableLiveData<ArrayList<Music>>? = null

    fun getMusics(contentResolver: ContentResolver) : LiveData<ArrayList<Music>>? {
        if ( musics == null ) { musics = MutableLiveData() }
        loadMusic(contentResolver)
        return musics
    }

    private fun loadMusic(contentResolver: ContentResolver) {
        val list : ArrayList<Music> = ArrayList()
        val contResolver : ContentResolver = contentResolver
        val uri : Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection : String = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
        val sortOrder : String = MediaStore.Audio.Media.TITLE + " ASC"
        val cursor : Cursor = contResolver.query(uri, null, selection, null, sortOrder)
        if ( cursor.count > 0 ) {
            while (cursor.moveToNext()) {
                list.add (
                    Music (
                        cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)),
                        cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)),
                        cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)),
                        cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)) )
                )
            }
        }
        cursor.close()
        musics?.value = list
    }

}