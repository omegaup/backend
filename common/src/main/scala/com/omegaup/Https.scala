package com.omegaup

import com.omegaup.data.OmegaUpSerialization

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import net.liftweb.json.Serialization

object Https extends Object with Log with Using {
	val defaultSocketFactory = SSLSocketFactory.getDefault().asInstanceOf[SSLSocketFactory]
	val runnerSocketFactory: Option[SSLSocketFactory] =
		Config.get("https.disable", false) match {
			case true => None
			case false => Some(new SSLSocketFactory {
				private val sslContextFactory = new org.eclipse.jetty.util.ssl.SslContextFactory(
					Config.get("ssl.keystore", "omegaup.jks"))
				sslContextFactory.setKeyManagerPassword(Config.get("ssl.password", "omegaup"))
				sslContextFactory.setKeyStorePassword(Config.get("ssl.keystore.password", "omegaup"))
				sslContextFactory.setTrustStore(FileUtil.loadKeyStore(
					Config.get("ssl.truststore", "omegaup.jks"),
					Config.get("ssl.truststore.password", "omegaup")
				))
				sslContextFactory.setNeedClientAuth(true)
				sslContextFactory.start
				private val socketFactory = sslContextFactory.getSslContext.getSocketFactory

				override def getDefaultCipherSuites(): Array[String] = defaultSocketFactory.getDefaultCipherSuites
				override def getSupportedCipherSuites(): Array[String] = defaultSocketFactory.getSupportedCipherSuites

				@throws(classOf[IOException])
				override def createSocket(host: String, port: Int): Socket =
					socketFactory.createSocket(host, port)

				@throws(classOf[IOException])
				override def createSocket(host: String, port: Int, clientAddress: InetAddress, clientPort: Int): Socket =
					socketFactory.createSocket(host, port, clientAddress, clientPort)

				@throws(classOf[IOException])
				override def createSocket(address: InetAddress, port: Int): Socket =
					socketFactory.createSocket(address, port)

				@throws(classOf[IOException])
				override def createSocket(address: InetAddress, port: Int, clientAddress: InetAddress, clientPort: Int): Socket =
					socketFactory.createSocket(address, port, clientAddress, clientPort)

				@throws(classOf[IOException])
				override def createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
					socketFactory.createSocket(socket, host, port, autoClose)

				@throws(classOf[IOException])
				override def createSocket(): Socket = socketFactory.createSocket
			})
		}

	private def connect(url: String, runner: Boolean) = {
		val conn = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
		if (url.startsWith("https://")) {
			if (runner) {
				runnerSocketFactory match {
					case None =>
						throw new IllegalStateException("Service was started with https.disable")
					case Some(factory) =>
						conn.asInstanceOf[HttpsURLConnection].setSSLSocketFactory(factory)
				}
			}
		}
		conn
	}

	def get(url: String, runner: Boolean = true):String = {
		debug("GET {}", url)

		using (connect(url, runner)) { conn => {
			conn.addRequestProperty("Connection", "close")
			conn.setDoOutput(false)
			new BufferedReader(new InputStreamReader(conn.getInputStream())).readLine
		}}
	}

	def post[T](url: String, data: scala.collection.Map[String, String], runner: Boolean = true)(implicit mf: Manifest[T]):T = {
		debug("POST {}", url)

		val postdata = data.map { case(key, value) =>
			URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
		}.mkString("&").getBytes("UTF-8")

		implicit val formats = OmegaUpSerialization.formats

		using (connect(url, runner)) { conn => {
			conn.setDoOutput(true)
			conn.setRequestMethod("POST")
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
			conn.setRequestProperty("Content-Length", postdata.length.toString)
			val stream = conn.getOutputStream()
			stream.write(postdata, 0, postdata.length)
			stream.close
			Serialization.read[T](new InputStreamReader(conn.getInputStream()))
		}}
	}

  def send[T, W <: AnyRef](url:String, request:W, responseReader: InputStream=>T, runner: Boolean)(implicit mf: Manifest[T]):T = {
		debug("Requesting {}", url)

		implicit val formats = OmegaUpSerialization.formats

		using (connect(url, runner)) { conn => {
			conn.addRequestProperty("Content-Type", "text/json")
			conn.setDoOutput(true)
			val writer = new PrintWriter(new OutputStreamWriter(conn.getOutputStream()))
			Serialization.write[W, PrintWriter](request, writer)
			writer.close()

			responseReader(conn.getInputStream)
		}}
	}

	def send[T, W <: AnyRef](url:String, request:W, runner: Boolean)(implicit mf: Manifest[T]):T = {
		debug("Requesting {}", url)

		implicit val formats = OmegaUpSerialization.formats

		using (connect(url, runner)) { conn => {
			conn.addRequestProperty("Content-Type", "text/json")
			conn.setDoOutput(true)
			val writer = new PrintWriter(new OutputStreamWriter(conn.getOutputStream()))
			Serialization.write[W, PrintWriter](request, writer)
			writer.close()

			Serialization.read[T](new InputStreamReader(conn.getInputStream()))
		}}
	}

	def zip_send[T](url:String, zipfile:String, zipname:String, runner: Boolean)(implicit mf: Manifest[T]): T = {
		val file = new File(zipfile)

		zip_send(url, new FileInputStream(zipfile), file.length.toInt, zipname, runner)
	}

	def zip_send[T](url:String, inputStream:InputStream, zipSize:Int, zipname:String, runner: Boolean)(implicit mf: Manifest[T]): T = {
		debug("Requesting {}", url)

		implicit val formats = OmegaUpSerialization.formats

		using (connect(url, runner)) { conn => {
			conn.addRequestProperty("Content-Type", "application/zip")
			conn.addRequestProperty("Content-Disposition", "attachment; filename=" + zipname + ";")
			conn.setFixedLengthStreamingMode(zipSize)
			conn.setDoOutput(true)
			using (conn.getOutputStream) { outputStream => {
				using (inputStream) {
					FileUtil.copy(_, outputStream)
				}
			}}

			Serialization.read[T](new InputStreamReader(conn.getInputStream()))
		}}
	}

	def stream_send[T](url:String, mimeType: String, filename: String, callback:OutputStream=>Unit, runner: Boolean = true)(implicit mf: Manifest[T]): T = {
		debug("Requesting {}", url)

		implicit val formats = OmegaUpSerialization.formats

		using (connect(url, runner)) { conn => {
			conn.addRequestProperty("Content-Type", mimeType)
			conn.addRequestProperty("Content-Disposition", "attachment; filename=" + filename + ";")
			conn.setDoOutput(true)
			conn.setChunkedStreamingMode(0)

			using (conn.getOutputStream) {
				callback(_)
			}

			Serialization.read[T](new InputStreamReader(conn.getInputStream()))
		}}
	}

	def receive_zip[T, W <: AnyRef](url:String, request:W, file:String, runner: Boolean = true)(implicit mf: Manifest[T]): Option[T] = {
		debug("Requesting {}", url)

		implicit val formats = OmegaUpSerialization.formats

		using (connect(url, runner)) { conn => {
			conn.addRequestProperty("Content-Type", "text/json")
			conn.setDoOutput(true)
			val writer = new PrintWriter(new OutputStreamWriter(conn.getOutputStream()))
			Serialization.write[W, PrintWriter](request, writer)
			writer.close()

			if (conn.getHeaderField("Content-Type") == "application/zip") {
				val outputStream = new FileOutputStream(file)
				val inputStream = conn.getInputStream
				val buffer = Array.ofDim[Byte](1024)
				var read = 0

				while( { read = inputStream.read(buffer) ; read > 0 } ) {
					outputStream.write(buffer, 0, read)
				}

				inputStream.close
				outputStream.close

				None
			} else {
				Some(Serialization.read[T](new InputStreamReader(conn.getInputStream())))
			}
		}}
	}
}

