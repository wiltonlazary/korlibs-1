package com.soywiz.korim.format

import com.soywiz.korim.bitmap.*
import com.soywiz.korio.vfs.*

class HtmlImageSpecialReader : VfsSpecialReader<NativeImage>(NativeImage::class) {
	//override val available: Boolean = OS.isJs

	override suspend fun readSpecial(vfs: Vfs, path: String): NativeImage {
		//println("HtmlImageSpecialReader.readSpecial: $vfs, $path")
		val canvas = when (vfs) {
			is LocalVfs -> {
				//println("LOCAL: HtmlImageSpecialReader: $vfs, $path")
				NativeImageFormatProvider.BrowserImage.loadImage(path)
			}
			is UrlVfs -> {
				val jsUrl = vfs.getFullUrl(path)
				//println("URL: HtmlImageSpecialReader: $vfs, $path : $jsUrl")
				NativeImageFormatProvider.BrowserImage.loadImage(jsUrl)
			}
			else -> {
				//println("OTHER: HtmlImageSpecialReader: $vfs, $path")
				NativeImageFormatProvider.BrowserImage.decodeToCanvas(vfs[path].readAll())
			}
		}
		return CanvasNativeImage(canvas)
	}
}