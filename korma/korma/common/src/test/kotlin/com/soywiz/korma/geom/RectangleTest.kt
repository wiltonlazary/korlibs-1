package com.soywiz.korma.geom

import kotlin.test.*

class RectangleTest {
	@Test
	fun name() {
		val big = Rectangle.fromBounds(0, 0, 50, 50)
		val small = Rectangle.fromBounds(10, 10, 20, 20)
		val out = Rectangle.fromBounds(100, 10, 200, 20)
		assertTrue(small in big)
		assertTrue(big !in small)
		assertTrue(small == (small intersection big))
		assertTrue(small == (big intersection small))
		assertTrue(null == (big intersection out))
		assertTrue(small intersects big)
		assertTrue(big intersects small)
		assertFalse(big intersects out)
	}

	@Test
	fun name2() {
		val r1 = Rectangle(20, 0, 30, 10)
		val r2 = Rectangle(100, 0, 100, 50)
		val ro = r1.copy()
		ro.setToAnchoredRectangle(ro, Anchor.MIDDLE_CENTER, r2)
		//Assert.assertEquals(Rectangle(0, 0, 0, 0), r1)
		assertEquals(Rectangle(35, 20, 30, 10), ro)
	}
}