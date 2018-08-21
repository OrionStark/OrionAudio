package com.orionoscode.orionaudio.models

import java.io.Serializable

data class Music(var data : String = "",
                 var title : String = "", var album : String = "",
                 var artist : String = "", var favorite: Boolean = false,
                 var playlist_name: String = "") : Serializable