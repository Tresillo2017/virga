package com.tomasps.virga.core.wearsync

/**
 * The Wear capability the Virga watch app advertises (wear/src/main/res/values/wear.xml). The phone publishes the
 * handoff only while a node advertises it, and its capability listener filters on `/` + [NAME].
 */
object WatchCapability {
    const val NAME = "virga_watch_app"
}
