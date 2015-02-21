package com.omegaup.runner

import com.omegaup._
import com.omegaup.data._
import java.io._
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.{TarArchiveOutputStream, TarArchiveEntry}
import net.liftweb.json._

class OmegaUpRunstreamReader(callback: RunCaseCallback) extends Object with Using with Log {
	def apply(inputStream: InputStream): RunOutputMessage = {
		using (new BZip2CompressorInputStream(inputStream)) { bzip2 => {
			val dis = new DataInputStream(bzip2)

			while (dis.readBoolean) {
				val filename = dis.readUTF
				val length = dis.readLong
				using (new ChunkInputStream(dis, length.toInt)) {
					callback(filename, length, _)
				}
			}

			implicit val formats = OmegaUpSerialization.formats
			Serialization.read[RunOutputMessage](new InputStreamReader(dis))
		}}
	}
}

class RunnerProxy(val hostname: String, port: Int) extends RunnerService
with Using with Log {
	private def url()(implicit ctx: Context) = {
		(ctx.config.get("https.disable", false) match {
			case false => "https://"
			case true => "http://"
		}) + hostname + ":" + port
	}

	def name() = hostname

	override def port() = port

	override def toString() = "RunnerProxy(%s:%d)".format(hostname, port)

	def compile(message: CompileInputMessage)(implicit ctx: Context):
	CompileOutputMessage = {
		Https.send[CompileOutputMessage, CompileInputMessage](url + "/compile/",
			message,
			true
		)
	}

	def run(message: RunInputMessage, callback: RunCaseCallback)(implicit ctx: Context):
	RunOutputMessage = {
		val reader = new OmegaUpRunstreamReader(callback)
		Https.send[RunOutputMessage, RunInputMessage](url + "/run/",
			message,
			reader.apply _,
			true
		)
	}
	
	def input(inputName: String, entries: Iterable[InputEntry])(implicit ctx: Context):
	InputOutputMessage = {
		Https.stream_send[InputOutputMessage](
			url + "/input/",
			"application/x-tar",
			inputName,
			{ stream => {
				using (new TarArchiveOutputStream(stream)) { tar => {
					for (entry <- entries) {
						val tarEntry = new TarArchiveEntry(entry.name)
						tarEntry.setSize(entry.length)
						tar.putArchiveEntry(tarEntry)
						using (entry.data) {
							FileUtil.copy(_, tar)
						}
						tar.closeArchiveEntry
					}
				}}
			}}
		)
	}
	
	override def hashCode() = 28227 + 97 * hostname.hashCode + port
	override def equals(other: Any) = other match {
		case x:RunnerProxy => hostname == x.hostname && port == x.port
		case _ => false
	}
}

/* vim: set noexpandtab: */
