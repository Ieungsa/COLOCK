package com.ieungsa2.network

import com.google.gson.annotations.SerializedName

data class WhisperResponse(
    @SerializedName("text")
    val text: String
)