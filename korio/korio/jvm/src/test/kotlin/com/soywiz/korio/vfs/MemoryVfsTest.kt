package com.soywiz.korio.vfs

import com.soywiz.korio.async.*
import kotlin.test.*

class MemoryVfsTest {
	@Test
	fun name() = suspendTest {
		val log = ArrayList<String>()
		val mem = MemoryVfs()

		mem.watch {
			log += it.toString()
		}

		mem["item.txt"].writeString("test")
		mem["test"].mkdir()
		mem["test"].delete()
		this.step(100)
		assertEquals(
			"[MODIFIED(NodeVfs[/item.txt]), CREATED(NodeVfs[/test]), DELETED(NodeVfs[/test])]",
			log.toString()
		)
	}
}