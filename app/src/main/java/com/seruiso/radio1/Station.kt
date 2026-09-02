package com.seruiso.radio1

data class Station(
    val url: String,
    val name: String,
    val genre: String = "",
    val country: String = "",
    val favicon: String = "",
    val tab: String = "",
)
