package com.soywiz.korge.ext.swf

import com.soywiz.korge.animate.*
import com.soywiz.korge.plugin.*
import com.soywiz.korge.view.*

object SwfPlugin : KorgePlugin() {
	override suspend fun register(views: Views) {
		views.animateLibraryLoaders += KorgeFileLoaderTester("swf") { s, injector ->
			val MAGIC = s.readString(3)
			when (MAGIC) {
				"FWS", "CWS", "ZWS" -> KorgeFileLoader("swf") { content, views ->
					this.readSWF(views, content.ba)
				}
				else -> null
			}
		}
	}
}
