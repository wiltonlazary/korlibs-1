package com.soywiz.korio.vfs

import com.soywiz.klock.*
import com.soywiz.kmem.*
import com.soywiz.korio.async.*
import com.soywiz.korio.compression.*
import com.soywiz.korio.compression.deflate.*
import com.soywiz.korio.coroutine.*
import com.soywiz.korio.crypto.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.*
import kotlin.math.*

suspend fun ZipVfs(s: AsyncStream, zipFile: VfsFile? = null): VfsFile = ZipVfs(s, zipFile, caseSensitive = true)

suspend fun ZipVfs(s: AsyncStream, zipFile: VfsFile? = null, caseSensitive: Boolean = true): VfsFile {
	//val s = zipFile.open(VfsOpenMode.READ)
	var endBytes = EMPTY_BYTE_ARRAY

	val PK_END = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
	var pk_endIndex = -1

	for (chunkSize in listOf(0x16, 0x100, 0x1000, 0x10000)) {
		s.setPosition(max(0L, s.getLength() - chunkSize))
		endBytes = s.readBytesExact(max(chunkSize, s.getAvailable().toIntClamp()))
		pk_endIndex = endBytes.indexOf(PK_END)
		if (pk_endIndex >= 0) break
	}

	if (pk_endIndex < 0) throw IllegalArgumentException("Not a zip file")

	val data = endBytes.copyOfRange(pk_endIndex, endBytes.size).openSync()

	fun String.normalizeName() = if (caseSensitive) this.trim('/') else this.trim('/').toLowerCase()

	class ZipEntry(
		val path: String,
		val compressionMethod: Int,
		val isDirectory: Boolean,
		val time: DosFileDateTime,
		val offset: Int,
		val inode: Long,
		val headerEntry: AsyncStream,
		val compressedSize: Long,
		val uncompressedSize: Long
	)

	fun ZipEntry?.toStat(file: VfsFile): VfsStat {
		val vfs = file.vfs
		return if (this != null) {
			vfs.createExistsStat(
				file.path,
				isDirectory = isDirectory,
				size = uncompressedSize,
				inode = inode,
				createTime = this.time.utcTimestamp
			)
		} else {
			vfs.createNonExistsStat(file.path)
		}
	}

	val files = LinkedHashMap<String, ZipEntry>()
	val filesPerFolder = LinkedHashMap<String, MutableMap<String, ZipEntry>>()

	data.apply {
		//println(s)
		if (readS32_be() != 0x504B_0506) throw IllegalStateException("Not a zip file")
		val diskNumber = readU16_le()
		val startDiskNumber = readU16_le()
		val entriesOnDisk = readU16_le()
		val entriesInDirectory = readU16_le()
		val directorySize = readS32_le()
		val directoryOffset = readS32_le()
		val commentLength = readU16_le()

		//println("Zip: $entriesInDirectory")

		val ds = s.sliceWithSize(directoryOffset.toLong(), directorySize.toLong()).readAvailable().openSync()
		ds.apply {
			for (n in 0 until entriesInDirectory) {
				if (readS32_be() != 0x504B_0102) throw IllegalStateException("Not a zip file record")
				val versionMade = readU16_le()
				val versionExtract = readU16_le()
				val flags = readU16_le()
				val compressionMethod = readU16_le()
				val fileTime = readU16_le()
				val fileDate = readU16_le()
				val crc = readS32_le()
				val compressedSize = readS32_le()
				val uncompressedSize = readS32_le()
				val fileNameLength = readU16_le()
				val extraLength = readU16_le()
				val fileCommentLength = readU16_le()
				val diskNumberStart = readU16_le()
				val internalAttributes = readU16_le()
				val externalAttributes = readS32_le()
				val headerOffset = readS32_le()
				val name = readString(fileNameLength)
				val extra = readBytes(extraLength)

				val isDirectory = name.endsWith("/")
				val normalizedName = name.normalizeName()

				val baseFolder = normalizedName.substringBeforeLast('/', "")
				val baseName = normalizedName.substringAfterLast('/')

				val folder = filesPerFolder.getOrPut(baseFolder) { LinkedHashMap() }
				val entry = ZipEntry(
					path = name,
					compressionMethod = compressionMethod,
					isDirectory = isDirectory,
					time = DosFileDateTime(fileTime, fileDate),
					inode = n.toLong(),
					offset = headerOffset,
					headerEntry = s.sliceWithStart(headerOffset.toUInt()),
					compressedSize = compressedSize.toUInt(),
					uncompressedSize = uncompressedSize.toUInt()
				)
				val components = listOf("") + PathInfo(normalizedName).getFullComponents()
				for (m in 1 until components.size) {
					val f = components[m - 1]
					val c = components[m]
					if (c !in files) {
						val folder2 = filesPerFolder.getOrPut(f) { LinkedHashMap() }
						val entry2 = ZipEntry(
							path = c,
							compressionMethod = 0,
							isDirectory = true,
							time = DosFileDateTime(0, 0),
							inode = 0L,
							offset = 0,
							headerEntry = byteArrayOf().openAsync(),
							compressedSize = 0L,
							uncompressedSize = 0L
						)
						folder2[PathInfo(c).basename] = entry2
						files[c] = entry2
					}
				}
				//println(components)
				folder[baseName] = entry
				files[normalizedName] = entry
			}
		}
		files[""] = ZipEntry(
			path = "",
			compressionMethod = 0,
			isDirectory = true,
			time = DosFileDateTime(0, 0),
			inode = 0L,
			offset = 0,
			headerEntry = byteArrayOf().openAsync(),
			compressedSize = 0L,
			uncompressedSize = 0L
		)
		Unit
	}

	class Impl : Vfs() {
		val vfs = this

		override suspend fun open(path: String, mode: VfsOpenMode): AsyncStream {
			val entry = files[path.normalizeName()] ?: throw com.soywiz.korio.FileNotFoundException("Path: '$path'")
			if (entry.isDirectory) throw com.soywiz.korio.IOException("Can't open a zip directory for $mode")
			val base = entry.headerEntry.slice()
			return base.run {
				if (this.getAvailable() < 16) throw IllegalStateException("Chunk to small to be a ZIP chunk")
				if (readS32_be() != 0x504B_0304) throw IllegalStateException("Not a zip file")
				val version = readU16_le()
				val flags = readU16_le()
				val compressionType = readU16_le()
				val fileTime = readU16_le()
				val fileDate = readU16_le()
				val crc = readS32_le()
				val compressedSize = readS32_le()
				val uncompressedSize = readS32_le()
				val fileNameLength = readU16_le()
				val extraLength = readU16_le()
				val name = readString(fileNameLength)
				val extra = readBytesExact(extraLength)
				val compressedData = readSlice(entry.compressedSize)

				when (entry.compressionMethod) {
					0 -> compressedData
					else -> {
						val compressed = UncompressAsyncStream(when (entry.compressionMethod) {
							8 -> Deflate
							else -> TODO("Not implemented zip method ${entry.compressionMethod}")
						}, compressedData, uncompressedSize.toLong()).readAll()

						val computedCrc = CRC32.compute(compressed)
						if (computedCrc != crc) error("Uncompressed file crc doesn't match: expected=${crc.hex32}, actual=${computedCrc.hex32}")

						compressed.openAsync()
					}
				}
			}
		}

		override suspend fun stat(path: String): VfsStat {
			return files[path.normalizeName()].toStat(this@Impl[path])
		}

		override suspend fun list(path: String): AsyncSequence<VfsFile> {
			return asyncGenerate(getCoroutineContext()) {
				for ((name, entry) in filesPerFolder[path.normalizeName()] ?: LinkedHashMap()) {
					//yield(entry.toStat(this@Impl[entry.path]))
					yield(vfs[entry.path])
				}
			}
		}

		override fun toString(): String = "ZipVfs($zipFile)"
	}

	return Impl().root
}

private class DosFileDateTime(var dosTime: Int, var dosDate: Int) {
	val seconds: Int get() = 2 * dosTime.getBits(0, 5)
	val minutes: Int get() = dosTime.getBits(5, 6)
	val hours: Int get() = dosTime.getBits(11, 5)
	val day: Int get() = dosDate.getBits(0, 5)
	val month1: Int get() = dosDate.getBits(5, 4)
	val fullYear: Int get() = 1980 + dosDate.getBits(9, 7)

	init {
		//println("DosFileDateTime: $fullYear-$month1-$day $hours-$minutes-$seconds")
	}

	val date: DateTime by lazy {
		DateTime.createAdjusted(fullYear, month1, day, hours, minutes, seconds)
	}
	val utcTimestamp: Long by lazy { date.unix }
}

suspend fun VfsFile.openAsZip() = ZipVfs(this.open(VfsOpenMode.READ), this)
suspend fun VfsFile.openAsZip(caseSensitive: Boolean) =
	ZipVfs(this.open(VfsOpenMode.READ), this, caseSensitive = caseSensitive)

suspend fun AsyncStream.openAsZip() = ZipVfs(this)
suspend fun AsyncStream.openAsZip(caseSensitive: Boolean) = ZipVfs(this, caseSensitive = caseSensitive)
