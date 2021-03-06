package com.soywiz.korio.vfs

import com.soywiz.korio.async.*
import kotlin.test.*

class IsoVfsTest {
	@Test
	fun testIso() = suspendTest {
		val isotestIso = ResourcesVfs["isotest.iso"].openAsIso()
		assertEquals(
			listOf("/HELLO", "/HELLO/WORLD.TXT"),
			isotestIso.listRecursive().toList().map { it.fullname }
		)

		// Case insensitive!
		assertEquals(
			"WORLD!",
			isotestIso["hello"]["world.txt"].readString()
		)
	}
}