package omegaup.runner

import omegaup._
import omegaup.data._
import java.io._
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.kamranzafar.jtar.{TarEntry, TarHeader, TarOutputStream}
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

			implicit val formats = Serialization.formats(NoTypeHints)
			Serialization.read[RunOutputMessage](new InputStreamReader(dis))
		}}
	}
}

class RunnerProxy(val hostname: String, port: Int) extends RunnerService with Using with Log {
	private val url = "https://" + hostname + ":" + port

	def name() = hostname

	override def port() = port

	override def toString() = "RunnerProxy(%s:%d)".format(hostname, port)

	def compile(message: CompileInputMessage): CompileOutputMessage = {
		Https.send[CompileOutputMessage, CompileInputMessage](url + "/compile/",
			message
		)
	}

	def run(message: RunInputMessage, callback: RunCaseCallback) : RunOutputMessage = {
		val reader = new OmegaUpRunstreamReader(callback)
		Https.send[RunOutputMessage, RunInputMessage](url + "/run/", message, reader.apply _)
	}
	
	def input(inputName: String, entries: Iterable[InputEntry]): InputOutputMessage = {
		Https.stream_send[InputOutputMessage](
			url + "/input/",
			"application/x-tar",
			inputName,
			{ stream => {
				using (new TarOutputStream(stream)) { tar => {
					for (entry <- entries) {
						val tarEntry = new TarEntry(new TarHeader)
						tarEntry.setSize(entry.length)
						tarEntry.setName(entry.name)
						tar.putNextEntry(tarEntry)
						using (entry.data) {
							FileUtil.copy(_, tar)
						}
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
