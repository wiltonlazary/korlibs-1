package com.soywiz.korge.atlas

import com.soywiz.korge.render.*
import com.soywiz.korge.view.*
import com.soywiz.korio.lang.*
import com.soywiz.korio.serialization.json.*
import com.soywiz.korio.vfs.*

suspend fun VfsFile.readAtlas2(views: Views): Atlas2 {
	val json = Json.decode(this.readString())
	return DynamicContextInstance.run {
		val sprites = json["sprites"].toDynamicList()

		val entries = sprites.map {
			Atlas2.Entry(
				rotated = it["rotated"].toBool(),
				x = it["x"].toInt(),
				y = it["y"].toInt(),
				w = it["w"].toInt(),
				h = it["h"].toInt(),
				margin = it["margin"].toInt(),
				extruded = it["extruded"].toInt(),
				name = it["name"].toString()
			)
		}

		val width = json["width"].toInt()
		val height = json["height"].toInt()
		val file = json["file"].toString()

		val imageFile = this@readAtlas2.parent[file].readTexture(views, mipmaps = false)

		Atlas2(entries, entries.associate { it.name to imageFile.slice(it.x, it.y, it.w, it.h) })
	}
}

class Atlas2(val entries: List<Entry>, val textures: Map<String, Texture>) {
	data class Entry(
		val x: Int,
		val y: Int,
		val w: Int,
		val h: Int,
		val margin: Int,
		val extruded: Int,
		val name: String,
		val rotated: Boolean
	)

	operator fun get(name: String): Texture = textures[name] ?: error("Can't find '$name' in atlas")
}
