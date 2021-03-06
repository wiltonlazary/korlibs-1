package com.soywiz.korge.view

import com.soywiz.korge.*
import com.soywiz.korge.bitmapfont.*
import com.soywiz.korge.render.*
import com.soywiz.korge.tests.*
import com.soywiz.korio.async.*
import com.soywiz.korma.geom.*
import kotlin.test.*

class ViewsJvmTest : ViewsForTesting() {
	val tex = Texture(views.ag.createTexture(), 10, 10)

	@Test
	fun name() = suspendTest {
		views.stage += views.container().apply {
			this += views.image(tex)
		}
		assertEquals(
			"""
				|Stage(0)
				| Container(1)
				|  Image(2)
			""".trimMargin(),
			views.stage.dumpToString()
		)
		views.render()
		assertEquals(
			"""
			createBuffer(VERTEX):0
			createBuffer(INDEX):1
			createTexture():0
			clear(-16777216, 0.0, 0, true, true, true)
			Buffer[0].afterSetMem(mem[96000])
			Buffer[1].afterSetMem(mem[12000])
			draw(vertices=Buffer[0], indices=Buffer[1], program=Program(name=BatchBuilder2D.Premultiplied.Tinted, attributes=[a_Tex, a_Col, a_Col2, a_Pos], uniforms=[u_ProjMat, u_Tex]), type=TRIANGLES, vertexLayout=VertexLayout[a_Pos, a_Tex, a_Col, a_Col2], vertexCount=6, offset=0, blending=Blending(srcRGB=SOURCE_ALPHA, dstRGB=ONE_MINUS_SOURCE_ALPHA, srcA=ONE, dstA=ONE_MINUS_SOURCE_ALPHA, eqRGB=ADD, eqA=ADD), uniforms={Uniform(u_ProjMat)=Matrix4([0.003125, 0.0, 0.0, 0.0, 0.0, -0.004166667, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, -1.0, 1.0, -0.0, 1.0]), Uniform(u_Tex)=TextureUnit(texture=Texture[0], linear=true)}, stencil=StencilState(enabled=false, triangleFace=FRONT_AND_BACK, compareMode=ALWAYS, actionOnBothPass=KEEP, actionOnDepthFail=KEEP, actionOnDepthPassStencilFail=KEEP, referenceValue=0, readMask=255, writeMask=255), colorMask=ColorMaskState(red=true, green=true, blue=true, alpha=true))
			::draw.indices=[0, 1, 2, 3, 0, 2]
			::draw.vertex[0]: a_Pos[vec2(0.0,0.0)], a_Tex[vec2(0.0,0.0)], a_Col[byte4(-1)], a_Col2[byte4(2139062143)]
			::draw.vertex[1]: a_Pos[vec2(10.0,0.0)], a_Tex[vec2(1.0,0.0)], a_Col[byte4(-1)], a_Col2[byte4(2139062143)]
			::draw.vertex[2]: a_Pos[vec2(10.0,10.0)], a_Tex[vec2(1.0,1.0)], a_Col[byte4(-1)], a_Col2[byte4(2139062143)]
			::draw.vertex[3]: a_Pos[vec2(0.0,10.0)], a_Tex[vec2(0.0,1.0)], a_Col[byte4(-1)], a_Col2[byte4(2139062143)]
			disposeTemporalPerFrameStuff()
			flipInternal()
			""".trimIndent(),
			ag.getLogAsString()
		)
	}

	@Test
	fun textGetBounds() = viewsTest {
		val font = DebugBitmapFont.getDEBUG_BMP_FONT().toKorge(views, mipmaps = false)
		val text = views.text("Hello World", font = font, textSize = 8.0)
		val text2 = views.text("Hello World", font = font, textSize = 16.0)
		assertEquals(Rectangle(0, 0, 77, 8), text.globalBounds)
		assertEquals(Rectangle(0, 0, 154, 16), text2.globalBounds)
	}
}
