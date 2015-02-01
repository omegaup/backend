package com.omegaup

import com.omegaup.data._
import com.omegaup.grader.Grader
import com.omegaup.broadcaster.Broadcaster

import java.io.IOException
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import net.liftweb.json.Serialization
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler

class HttpHandler(grader: Grader, broadcaster: Broadcaster) extends AbstractHandler with Log {
	@throws(classOf[IOException])
	@throws(classOf[ServletException])
	override def handle(
		target: String,
		baseRequest: Request,
		request: HttpServletRequest,
		response: HttpServletResponse
	): Unit = {
		implicit val formats = OmegaUpSerialization.formats

		response.setContentType("text/json")

		Serialization.write(request.getPathInfo() match {
			case "/reload-config/" => {
				try {
					val req = Serialization.read[ReloadConfigInputMessage](request.getReader())
					val embeddedRunner = Config.get("grader.embedded_runner.enable", false)
					Config.load(grader.options.configPath)

					req.overrides match {
						case Some(x) => {
							info("Configuration reloaded {}", x)
							x.foreach { case (k, v) => Config.set(k, v) }
						}
						case None => info("Configuration reloaded")
					}

					Logging.init()

					grader.updateConfiguration(embeddedRunner)

					response.setStatus(HttpServletResponse.SC_OK)
					new ReloadConfigOutputMessage()
				} catch {
					case e: Exception => {
						error("Reload config: {}", e)
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
						new ReloadConfigOutputMessage(status = "error", error = Some(e.getMessage))
					}
				}
			}
			case "/status/" => {
				response.setStatus(HttpServletResponse.SC_OK)
				new StatusOutputMessage(
					embedded_runner = Config.get("grader.embedded_runner.enable", false),
					queue = Some(grader.runnerDispatcher.status)
				)
			}
			case "/grade/" => {
				try {
					val req = Serialization.read[GradeInputMessage](request.getReader())
					response.setStatus(HttpServletResponse.SC_OK)
					grader.grade(req)
				} catch {
					case e: IllegalArgumentException => {
						error("Grade failed: {}", e)
						response.setStatus(HttpServletResponse.SC_NOT_FOUND)
						new GradeOutputMessage(status = "error", error = Some(e.getMessage))
					}
					case e: Exception => {
						error("Grade failed: {}", e)
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
						new GradeOutputMessage(status = "error", error = Some(e.getMessage))
					}
				}
			}
			case "/register/" => {
				try {
					val req = Serialization.read[RegisterInputMessage](request.getReader())
					response.setStatus(HttpServletResponse.SC_OK)
					grader.runnerDispatcher.register(req.hostname, req.port)
				} catch {
					case e: Exception => {
						error("Register failed: {}", e)
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
						new RegisterOutputMessage(status = "error", error = Some(e.getMessage))
					}
				}
			}
			case "/deregister/" => {
				try {
					val req = Serialization.read[RegisterInputMessage](request.getReader())
					response.setStatus(HttpServletResponse.SC_OK)
					grader.runnerDispatcher.deregister(req.hostname, req.port)
				} catch {
					case e: Exception => {
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
						new RegisterOutputMessage(status = "error", error = Some(e.getMessage))
					}
				}
			}
			case "/broadcast/" => {
				try {
					val req = Serialization.read[BroadcastInputMessage](request.getReader())
					response.setStatus(HttpServletResponse.SC_OK)
					broadcaster.broadcast(
						req.contest,
						req.message,
						req.broadcast,
						req.targetUser,
						req.userOnly
					)
				} catch {
					case e: Exception => {
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
						error("Broadcast failed: {}", e)
						new BroadcastOutputMessage(status = "error", error = Some(e.getMessage))
					}
				}
			}
			case _ => {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND)
				new NullMessage()
			}
		}, response.getWriter())

		baseRequest.setHandled(true)
	}
}

class HttpService(grader: Grader, broadcaster: Broadcaster) extends ServiceInterface with Log {
	val server = new org.eclipse.jetty.server.Server()

	{
		// boilerplate code for jetty with https support
		val sslContext = new org.eclipse.jetty.util.ssl.SslContextFactory(
			Config.get("ssl.keystore", "omegaup.jks"))
		sslContext.setKeyManagerPassword(Config.get("ssl.password", "omegaup"))
		sslContext.setKeyStorePassword(Config.get("ssl.keystore.password", "omegaup"))
		sslContext.setTrustStore(FileUtil.loadKeyStore(
			Config.get("ssl.truststore", "omegaup.jks"),
			Config.get("ssl.truststore.password", "omegaup")
		))
		sslContext.setNeedClientAuth(true)

		val graderConnector = new org.eclipse.jetty.server.ServerConnector(
			server, sslContext)
		graderConnector.setPort(Config.get("grader.port", 21680))

		server.setConnectors(List(graderConnector).toArray)

		server.setHandler(new HttpHandler(grader, broadcaster))
		server.start()
	}

	override def stop(): Unit = {
		info("omegaUp HTTPS service stopping")
		server.stop
	}

	override def join(): Unit = {
		server.join
		info("omegaUp HTTPS service stopped")
	}
}

object Service extends Object with Log with Using {
	def main(args: Array[String]) = {
		val graderOptions = com.omegaup.grader.Service.parseOptions(args)

		// logger
		Logging.init

		val broadcaster = new Broadcaster
		val grader = new Grader(graderOptions, Some(broadcaster))
		val servers = List[ServiceInterface](
			broadcaster,
			grader,
			new HttpService(grader, broadcaster)
		)

		Runtime.getRuntime.addShutdownHook(new Thread() {
			override def run() = {
				info("Shutting down")
				try {
					servers foreach (_.stop)
				} catch {
					case e: Exception => {
						error("Error shutting down. Good night.", e)
					}
				}
			}
		});

		servers foreach (_.join)
		info("Shut down cleanly")
	}
}

/* vim: set noexpandtab: */
