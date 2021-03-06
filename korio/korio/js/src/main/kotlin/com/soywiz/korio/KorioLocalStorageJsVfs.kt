package com.soywiz.korio

import com.soywiz.korio.vfs.*
import org.w3c.dom.*
import kotlin.browser.*

val jsLocalStorageVfs by lazy {
	MapLikeStorageVfs(object : SimpleStorage {
		override suspend fun get(key: String): String? = localStorage[key]
		override suspend fun set(key: String, value: String) = run { localStorage[key] = value }
		override suspend fun remove(key: String) = localStorage.removeItem(key)
	})
}
