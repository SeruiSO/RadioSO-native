package com.seruiso.radio1

data class MusicTvChannel(
    val name: String,
    val url: String,
    val tag: String = "",
)

/** ~20 музичних TV з ухилом у dance / EDM / hits (HLS). */
object MusicTvChannels {
    private const val B = "http://b468f60e.russtv.net/iptv/TP6ZE44WPGBCMSTYY526SQYT"
    val all: List<MusicTvChannel> = listOf(
        MusicTvChannel("Deluxe Dance HD", "$B/31558/index.m3u8", "dance"),
        MusicTvChannel("Dance TV · EDM", "$B/2395/index.m3u8", "edm"),
        MusicTvChannel("FRESH Dance HD", "$B/18087/index.m3u8", "dance"),
        MusicTvChannel("FRESH Hits HD", "$B/18084/index.m3u8", "hits"),
        MusicTvChannel("Bridge Deluxe HD", "$B/236/index.m3u8", "bridge"),
        MusicTvChannel("Bridge Hits HD", "$B/19227/index.m3u8", "bridge"),
        MusicTvChannel("Bridge Hits", "$B/711/index.m3u8", "bridge"),
        MusicTvChannel("Bridge TV", "$B/122/index.m3u8", "bridge"),
        MusicTvChannel("1HD Music Television", "$B/17038/index.m3u8", "hits"),
        MusicTvChannel("Europa Plus TV HD", "$B/20029/index.m3u8", "hits"),
        MusicTvChannel("NRJ Hits HD", "$B/19199/index.m3u8", "hits"),
        MusicTvChannel("MTV HD US", "$B/2130/index.m3u8", "mtv"),
        MusicTvChannel("Deejay TV HD", "$B/10070/index.m3u8", "dance"),
        MusicTvChannel("KroneHit TV HD", "$B/10065/index.m3u8", "dance"),
        MusicTvChannel("NRG 91 HD", "$B/7223/index.m3u8", "dance"),
        MusicTvChannel("V2BEAT HD", "$B/15020/index.m3u8", "dance"),
        MusicTvChannel("VeleS Dj Set", "$B/31639/index.m3u8", "dj"),
        MusicTvChannel("FON Music HD", "$B/19223/index.m3u8", "hits"),
        MusicTvChannel("Deluxe Music HD", "$B/17047/index.m3u8", "hits"),
        MusicTvChannel("MCM Top Russia", "$B/836/index.m3u8", "hits"),
    )
}
