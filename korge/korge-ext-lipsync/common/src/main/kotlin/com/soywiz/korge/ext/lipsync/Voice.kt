package com.soywiz.korge.ext.lipsync

import com.soywiz.kds.*
import com.soywiz.korau.sound.*
import com.soywiz.korge.animate.*
import com.soywiz.korge.audio.*
import com.soywiz.korge.component.*
import com.soywiz.korge.resources.*
import com.soywiz.korge.view.*
import com.soywiz.korinject.*
import com.soywiz.korio.async.*
import com.soywiz.korio.util.*
import com.soywiz.korio.vfs.*

class LipSync(val lipsync: String) {
	val timeMs: Int get() = lipsync.length * 16
	operator fun get(timeMs: Int): Char = lipsync.getOrElse(timeMs / 16) { 'X' }
	fun getAF(timeMs: Int): Char {
		val c = this[timeMs]
		return when (c) {
			'G' -> 'B'
			'H' -> 'C'
			'X' -> 'A'
			else -> c
		}
	}
}

//e: java.lang.UnsupportedOperationException: Class literal annotation arguments are not yet supported: Factory
//@AsyncFactoryClass(Voice.Factory::class)
class Voice(val views: Views, val voice: NativeSound, val lipsync: LipSync) {
	val timeMs: Int get() = lipsync.timeMs
	operator fun get(timeMs: Int): Char = lipsync[timeMs]
	fun getAF(timeMs: Int): Char = lipsync.getAF(timeMs)

	suspend fun play(name: String) {
		views.lipSync.play(this, name)
	}

	class Factory(
		val path: Path,
		val resourcesRoot: ResourcesRoot,
		val views: Views
	) : AsyncFactory<Voice> {
		suspend override fun create(): Voice = resourcesRoot[path].readVoice(views)
	}
}

data class LipSyncEvent(var name: String = "", var timeMs: Int = 0, var lip: Char = 'X')

class LipSyncHandler(val views: Views) {
	val event = LipSyncEvent()

	private fun dispatch(name: String, elapsedTime: Int, lip: Char) {
		views.dispatch(event.apply {
			this.name = name
			this.timeMs = elapsedTime
			this.lip = lip
		})
	}

	suspend fun play(voice: Voice, name: String) = suspendCancellableCoroutine<Unit> { c ->
		var cancel: Cancellable? = null

		val channel = views.soundSystem.play(voice.voice)

		cancel = views.stage.addUpdatable {
			val elapsedTime = channel.position
			//println("elapsedTime:$elapsedTime, channel.length=${channel.length}")
			if (elapsedTime >= channel.length) {
				cancel?.cancel()
				dispatch(name, 0, 'X')
			} else {
				dispatch(name, channel.position, voice[elapsedTime])
			}
		}

		val cancel2 = go(c.context) {
			channel.await()
			c.resume(Unit)
		}

		c.onCancel {
			cancel?.cancel(it)
			cancel2.cancel(it)
			channel.stop()
			dispatch(name, 0, 'X')
		}
	}
}

class LipSyncComponent(view: View) : Component(view) {
	init {
		addEventListener<LipSyncEvent> { it ->
			val name = view.getPropString("lipsync")
			if (it.name == name) {
				view.play("${it.lip}")
			}
		}
	}
}

val Views.lipSync by Extra.PropertyThis<Views, LipSyncHandler> { LipSyncHandler(this) }

suspend fun VfsFile.readVoice(views: Views): Voice {
	val lipsyncFile = this.withExtension("lipsync")
	return Voice(
		views,
		this.readNativeSoundOptimized(),
		LipSync(if (lipsyncFile.exists()) lipsyncFile.readString().trim() else "")
	)
}
