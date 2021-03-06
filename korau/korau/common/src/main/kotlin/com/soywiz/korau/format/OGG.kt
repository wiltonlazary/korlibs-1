package com.soywiz.korau.format

import com.soywiz.kmem.*
import com.soywiz.korio.error.*
import com.soywiz.korio.stream.*

object OGG : OggBase()

open class OggBase : AudioFormat("ogg") {
	suspend override fun tryReadInfo(data: AsyncStream): Info? = try {
		parse(data)
	} catch (e: Throwable) {
		//e.printStackTrace()
		null
	}

	suspend fun parse(s: AsyncStream): Info {
		var channels = 2
		var sampleRate = 44100
		var brnom = 160000
		while (s.hasAvailable()) {
			val magic = s.readString(5)
			if (magic != "OggS\u0000") invalidOp("Not an OGG file")
			val type = s.readS8()
			val cont = type.extract(0);
			val bos = type.extract(1);
			val eos = type.extract(2)
			val gpos = s.readS64_le()
			val sn = s.readS32_le()
			val psn = s.readS32_le()
			val chk = s.readS32_le()
			val pseg = s.readU8()
			val psizs = (0 until pseg).map { s.readU8() }
			val pages = psizs.map { s.readStream(it) }
			if (bos) {
				val info = pages[0]
				val packetType = info.readU8()
				if (packetType > 3) invalidOp("Unsupported OGG vorbis file")
				if (info.readString(6) != "vorbis") invalidOp("Unsupported OGG vorbis file")
				when (packetType) {
					PacketTypes.ID_HEADER -> {
						val vver = info.readS32_le()
						channels = info.readU8()
						sampleRate = info.readS32_le()
						val brmax = info.readS32_le()
						brnom = info.readS32_le()
						val brmin = info.readS32_le()
						val bsinfo = info.readU8()
					}
					PacketTypes.COMMENT_HEADER -> Unit
					PacketTypes.SETUP_HEADER -> Unit
				}
			}
			if (eos) return Info(
				lengthInMicroseconds = ((gpos.toDouble() * 1_000_000.0 / sampleRate.toDouble())).toLong(),
				channels = channels
			)
		}
		invalidOp("Cannot parse stream")
	}

	object PacketTypes {
		const val ID_HEADER = 1 // 4.2.2. Identification header
		const val COMMENT_HEADER = 3
		const val SETUP_HEADER = 5
	}
}