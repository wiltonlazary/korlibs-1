package com.soywiz.korge.scene

import com.soywiz.korge.plugin.*
import com.soywiz.korim.color.*
import com.soywiz.korim.vector.*
import com.soywiz.korinject.*
import com.soywiz.korma.geom.*
import kotlin.reflect.*

open class Module {
	open val bgcolor: Int = Colors.BLACK
	open val title: String = "Game"
	open val icon: String? = null
	open val iconImage: Context2d.SizedDrawable? = null

	open val size: SizeInt = SizeInt(640, 480)
	open val windowSize: SizeInt = size
	open val plugins: List<KorgePlugin> = listOf()

	open val mainScene: KClass<out Scene> = EmptyScene::class
	open val clearEachFrame = true

	open val targetFps: Double = 0.0

	open suspend fun init(injector: AsyncInjector) {
	}
}
