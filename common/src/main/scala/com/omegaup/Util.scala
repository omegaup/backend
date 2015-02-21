package com.omegaup

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.io.Reader
import java.io.Writer
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Random
import org.apache.commons.codec.binary.Base64InputStream
import scala.collection.mutable
import scala.language.implicitConversions

trait Using {
	def using[A, B <: java.io.Closeable] (closeable: B) (f: B => A): A =
		 try { f(closeable) } finally { closeable.close }

	def susing[A, B <: java.sql.Statement] (closeable: B) (f: B => A): A =
		 try { f(closeable) } finally { closeable.close }

	def rusing[A, B <: java.sql.ResultSet] (closeable: B) (f: B => A): A =
		 try { f(closeable) } finally { closeable.close }

	def cusing[A, B <: java.net.HttpURLConnection] (connection: B) (f: B => A): A = {
		// This tries to leverage HttpURLConnection's Keep-Alive facilities.
		try {
			f(connection)
		} catch {
			case e: IOException => {
				try {
					val err = connection.getErrorStream
					val buf = Array.ofDim[Byte](1024)
					var ret: Int = 0
					while ({ ret = err.read(buf); ret > 0}){}
					err.close
				} catch {
					case _: Throwable => {}
				}
				throw e
			}
		} finally {
			try {
				connection.getInputStream.close
			} catch {
				case _: Throwable => {}
			}
		}
	 }

	def pusing[A] (process: Process) (f: Process => A): A =
		 try {
			f(process)
		} finally {
			if (process != null) {
				process.getInputStream.close()
				process.getOutputStream.close()
				process.getErrorStream.close()
			}
		}
}

object FileUtil extends Object with Using {
	@throws(classOf[IOException])
	def read(filename: String): String = read(new File(filename))

	@throws(classOf[IOException])
	def read(file: File): String = {
		val contents = new StringBuffer
		var ch: Int = 0

		using (new FileReader(file)) { fileReader => {
			while( {ch = fileReader.read(); ch != -1} ) {
				contents.appendCodePoint(ch)
			}

			contents.toString.trim
		}}
	}

	@throws(classOf[IOException])
	def read(stream: InputStream) = {
		val contents = new StringBuffer
		var ch: Int = 0

		using (new InputStreamReader(stream)) { reader => {
			while( {ch = reader.read(); ch != -1} ) {
				contents.appendCodePoint(ch)
			}

			contents.toString.trim
		}}
	}

	@throws(classOf[IOException])
	def write(filename: String, data: String): Unit = write(new File(filename), data)

	@throws(classOf[IOException])
	def write(file: File, data: String): Unit = {
		using (new FileWriter(file)) { fileWriter =>
			fileWriter.write(data)
		}
	}

	@throws(classOf[IOException])
	def copy(src: File, dest: File): Unit = {
		using (new FileInputStream(src)) { inputStream => {
			using (new FileOutputStream(dest)) { outputStream => {
				copy(inputStream, outputStream)
			}}
		}}
	}

	@throws(classOf[IOException])
	def copy_sha1(src: InputStream, dest: OutputStream): String = {
		val md = MessageDigest.getInstance("SHA1")
		val buffer = Array.ofDim[Byte](1024)
		var read = 0

		while( { read = src.read(buffer) ; read > 0 } ) {
			md.update(buffer, 0, read)
			dest.write(buffer, 0, read)
		}

		val digest = md.digest()
		val sb = new StringBuilder(digest.length * 2)
		for (b <- digest) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16))
			sb.append(Character.forDigit(b & 0xF, 16))
		}
		sb.toString
	}

	@throws(classOf[IOException])
	def copy(src: InputStream, dest: OutputStream): Long = {
		val buffer = Array.ofDim[Byte](1024)
		var read = 0
		var written: Long = 0

		while( { read = src.read(buffer) ; read != -1 } ) {
			dest.write(buffer, 0, read)
			written += read
		}

		written
	}

	@throws(classOf[IOException])
	def deleteDirectory(dir: String): Boolean = FileUtil.deleteDirectory(new File(dir))

	@throws(classOf[IOException])
	def deleteDirectory(dir: File): Boolean = {
		if(dir.exists) {
			if (dir.isDirectory)
				dir.listFiles.foreach { FileUtil.deleteDirectory(_) }
			dir.delete
		}
		false
	}

	def extension(name: String): String = {
		val pos = name.lastIndexOf('.')
		if (pos != -1) {
			name.substring(pos + 1)
		} else {
			""
		}
	}

	def extension(file: File): String = extension(file.getName)

	def removeExtension(name: String): String = {
		val pos = name.lastIndexOf('.')
		if (pos != -1) {
			name.substring(0, pos)
		} else {
			name
		}
	}

	def removeExtension(file: File): String = removeExtension(file.getName)

	def basename(path: String): String = {
		val sep = path.lastIndexOf('/')
		if (sep != -1) {
			return path.substring(sep + 1)
		} else {
			return path
		}
	}

	def loadKeyStore(path: String, password: String): KeyStore = {
		val keystore = KeyStore.getInstance(KeyStore.getDefaultType)
		using (new FileInputStream(path)) { (in) => {
			keystore.load(in, password.toCharArray)
			keystore
		}}
	}

	def createRandomFile(rootFile: File, length: Int = 16, split: Option[Int] = Some(2)): (File, String) = {
		val random = new Random
		val bytes = new Array[Byte](length)
		val root = rootFile.toPath
		var target: Path = null
		var guid: String = null
		while (target == null) {
			random.nextBytes(bytes)
			val sb = new StringBuilder(2 * length)
			for (b <- bytes) {
				sb.append(f"${b & 0xff}%02x")
			}
			guid = sb.toString
			target = root.resolve(split match {
				case None => guid
				case Some(off) => guid.substring(0, off) + "/" + guid.substring(off)
			})
			try {
				Files.createFile(target)
			} catch {
				case e: FileAlreadyExistsException => {
					// Ignore, let's try again.
					target = null
				}
			}
		}
		(target.toFile, guid)
	}
}

object MetaFile extends Object with Using {
	@throws(classOf[IOException])
	def load(path: String): scala.collection.Map[String,String] = {
		using (new FileReader(path)) { reader =>
			load(reader)
		}
	}

	@throws(classOf[IOException])
	def load(reader: Reader): scala.collection.Map[String,String] = {
		val meta = new mutable.ListMap[String,String]
		using (new BufferedReader(reader)) { bReader => {
			var line: String = null

			while( { line = bReader.readLine(); line != null} ) {
				val idx = line.indexOf(':')

				if(idx > 0) {
					meta += (line.substring(0, idx) -> line.substring(idx+1))
				}
			}

			meta
		}}
	}

	@throws(classOf[IOException])
	def save(path: String, meta: scala.collection.Map[String,String]) = {
		using (new PrintWriter(new FileWriter(path))) { writer => {
			for ((key, value) <- meta) writer.printf("%s:%s\n", key, value)
		}}
	}
}

object DataUriStream extends Object with Log {
	def apply(stream: InputStream) = {
		debug("Reading data URI")

		val buffer = Array.ofDim[Byte](1024)
		var bytesRead = 0
		var ch = 0

		bytesRead = stream.read(buffer, 0, 5)

		if (bytesRead != 5 || new String(buffer, 0, bytesRead) != "data:") {
			debug("Illegal data URI: No \"data\"")
			throw new IOException("Illegal data uri stream")
		}

		while ({ch = stream.read ; bytesRead < buffer.length && ch != -1 && ch != ','}) {
			buffer(bytesRead) = ch.toByte
			bytesRead += 1
		}

		if (ch == -1) {
			debug("Illegal data URI: No comma")
			throw new IOException("Illegal data uri stream")
		}

		if (new String(buffer, 0, bytesRead).contains("base64")) {
			debug("Using base64")
			new Base64InputStream(stream)
		} else {
			debug("Using regular stream")
			stream
		}
	}
}

class DataUriInputStream(stream: InputStream) extends FilterInputStream(DataUriStream(stream)) with Log {}

class ChunkInputStream(stream: InputStream, length: Long) extends InputStream {
	var remaining = length

	override def available(): Int = Math.min(remaining.toInt, stream.available)
	override def close(): Unit = {
		while (remaining > 0) {
			if (skip(remaining) == 0) {
				throw new IOException("Cannot close current chunk: " + remaining + " bytes remaining")
			}
		}
	}
	override def markSupported(): Boolean = false
	override def reset(): Unit = throw new IOException("Mark is not supported")
	override def mark(readLimit: Int): Unit = {}
	override def read(): Int = {
		if (remaining == 0) {
			-1
		}	else {
			val r = stream.read
			if (r == -1) {
				throw new IOException("Premature EOF while reading a chunk with " + remaining + " bytes remaining")
			}
			remaining -= 1
			r
		}
	}
	override def read(b: Array[Byte]): Int = read(b, 0, b.length)
	override def read(b: Array[Byte], off: Int, len: Int): Int = {
		if (remaining == 0) return -1
		val r = stream.read(b, off, Math.min(remaining.toInt, len))
		if (r == -1) {
			throw new IOException("Premature EOF while reading a chunk with " + remaining + " bytes remaining")
		}
		remaining -= r
		r
	}
	override def skip(n: Long): Long = {
		if (remaining == 0) return 0
		val r = stream.skip(Math.min(remaining, n))
		remaining -= r
		r
	}
}

/* vim: set noexpandtab: */
