package com.soywiz.korge.ext.spriter

import com.soywiz.korge.atlas.*
import com.soywiz.korge.ext.spriter.com.brashmonkey.spriter.*
import com.soywiz.korge.render.*
import com.soywiz.korge.resources.*
import com.soywiz.korge.view.*
import com.soywiz.korinject.*
import com.soywiz.korio.vfs.*
import kotlin.collections.set

//e: java.lang.UnsupportedOperationException: Class literal annotation arguments are not yet supported: Factory
//@AsyncFactoryClass(SpriterLibrary.Loader::class)
class SpriterLibrary(val views: Views, val data: Data, val atlas: Map<String, TransformedTexture>) {
	val entityNames = data.entities.map { it.name }

	fun create(
		entityName: String,
		animationName1: String? = null,
		animationName2: String? = animationName1
	): SpriterView {
		val entity = data.getEntity(entityName)
		return SpriterView(
			views,
			this,
			entity!!,
			animationName1 ?: entity.getAnimation(0).name,
			animationName2 ?: entity.getAnimation(0).name
		)
	}

	class Loader(
		val views: Views,
		val path: Path,
		val resourcesRoot: ResourcesRoot
	) : AsyncFactory<SpriterLibrary> {
		suspend override fun create(): SpriterLibrary = resourcesRoot[path].readSpriterLibrary(views)
	}
}

suspend fun VfsFile.readSpriterLibrary(views: Views): SpriterLibrary {
	val file = this
	val scmlContent = file.readString()
	val reader = SCMLReader(scmlContent)
	val data = reader.data

	val textures = hashMapOf<String, TransformedTexture>()

	for (atlasName in data.atlases) {
		val atlas = file.parent[atlasName].readAtlas(views)
		textures += atlas.textures
	}

	for (folder in data.folders) {
		for (f in folder.files) {
			if (f.name in textures) continue
			val image = file.parent[f.name]
			val tex = image.readTexture(views.ag)
			textures[f.name] = TransformedTexture(tex)
		}
	}

	return SpriterLibrary(views, data, textures)
}
