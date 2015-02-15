package com.omegaup

import com.omegaup.data._
import com.omegaup.grader.Grader
import com.omegaup.grader.GraderData
import com.omegaup.broadcaster.Broadcaster

import java.io.IOException
import java.sql.Connection
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
			case "/grader/reload-config/" => {
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
			case "/grader/status/" => {
				response.setStatus(HttpServletResponse.SC_OK)
				new StatusOutputMessage(
					embedded_runner = Config.get("grader.embedded_runner.enable", false),
					queue = Some(grader.runnerDispatcher.status)
				)
			}
			case "/run/new/" => {
				if (Config.get("grader.standalone", false)) {
					try {
						var req = Serialization.read[RunNewInputMessage](request.getReader())
						response.setStatus(HttpServletResponse.SC_OK)
						implicit val connection: Connection = grader.conn
						req = req.copy(ip = request.getRemoteAddr)
						val outputMessage = Service.runNew(req)
						grader.grade(RunGradeInputMessage(id = List(outputMessage.id.get)))
						outputMessage
					} catch {
						case e: IllegalArgumentException => {
							error("Submitting new run failed: {}", e)
							response.setStatus(HttpServletResponse.SC_NOT_FOUND)
							new RunNewOutputMessage(status = "error", error = Some(e.getMessage))
						}
						case e: Exception => {
							error("Submitting new run failed: {}", e)
							response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
							new RunNewOutputMessage(status = "error", error = Some(e.getMessage))
						}
					}
				} else {
					response.setStatus(HttpServletResponse.SC_NOT_FOUND)
					new NullMessage()
				}
			}
			case "/run/status/" => {
				if (Config.get("grader.standalone", false)) {
					try {
						var req = Serialization.read[RunStatusInputMessage](request.getReader())
						implicit val connection: Connection = grader.conn
						val outputMessage = Service.runStatus(req)
						outputMessage match {
							case None => {
								response.setStatus(HttpServletResponse.SC_NOT_FOUND)
								new NullMessage()
							}
							case Some(message) => {
								response.setStatus(HttpServletResponse.SC_OK)
								message
							}
						}
					} catch {
						case e: Exception => {
							error("Getting run status failed: {}", e)
							response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
							new NullMessage()
						}
					}
				} else {
					response.setStatus(HttpServletResponse.SC_NOT_FOUND)
					new NullMessage()
				}
			}
			case "/run/list/" => {
				if (Config.get("grader.standalone", false)) {
					try {
						var req = request.getMethod match {
							case "POST" => Serialization.read[RunListInputMessage](request.getReader())
							case _ => RunListInputMessage()
						}
						implicit val connection: Connection = grader.conn
						response.setStatus(HttpServletResponse.SC_OK)
						Service.runList(req)
					} catch {
						case e: Exception => {
							error("Getting run list failed: {}", e)
							response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
							new NullMessage()
						}
					}
				} else {
					response.setStatus(HttpServletResponse.SC_NOT_FOUND)
					new NullMessage()
				}
			}
			case "/run/grade/" => {
				try {
					val req = Serialization.read[RunGradeInputMessage](request.getReader())
					response.setStatus(HttpServletResponse.SC_OK)
					grader.grade(req)
				} catch {
					case e: IllegalArgumentException => {
						error("Grade failed: {}", e)
						response.setStatus(HttpServletResponse.SC_NOT_FOUND)
						new RunGradeOutputMessage(status = "error", error = Some(e.getMessage))
					}
					case e: Exception => {
						error("Grade failed: {}", e)
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
						new RunGradeOutputMessage(status = "error", error = Some(e.getMessage))
					}
				}
			}
			case "/endpoint/register/" => {
				try {
					val req = Serialization.read[EndpointRegisterInputMessage](request.getReader())
					response.setStatus(HttpServletResponse.SC_OK)
					grader.runnerDispatcher.register(req.hostname, req.port)
				} catch {
					case e: Exception => {
						error("Register failed: {}", e)
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
						new EndpointRegisterOutputMessage(status = "error", error = Some(e.getMessage))
					}
				}
			}
			case "/endpoint/deregister/" => {
				try {
					val req = Serialization.read[EndpointRegisterInputMessage](request.getReader())
					response.setStatus(HttpServletResponse.SC_OK)
					grader.runnerDispatcher.deregister(req.hostname, req.port)
				} catch {
					case e: Exception => {
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
						new EndpointRegisterOutputMessage(status = "error", error = Some(e.getMessage))
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

		info("{} {} {}", request.getMethod, request.getPathInfo, response.getStatus)
		baseRequest.setHandled(true)
	}
}

class HttpService(grader: Grader, broadcaster: Broadcaster) extends ServiceInterface with Log {
	val server = new org.eclipse.jetty.server.Server

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
		info("omegaUp HTTPS service started")
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
	def runNew(req: RunNewInputMessage)(implicit connection: Connection): RunNewOutputMessage = {
		import java.util.Date
		import java.sql.Timestamp
		import java.text.SimpleDateFormat

		val file = java.io.File.createTempFile(
			System.currentTimeMillis.toString,
			"",
			new java.io.File(Config.get("submissions.root", "."))
		)
		FileUtil.write(file, req.code)

		GraderData.getProblem(req.problem) match {
			case None => throw new IllegalArgumentException("Problem " + req.problem + " not found")
			case Some(problem) => {
				val run = GraderData.insert(new Run(
					problem = problem,
					contest = req.contest match {
						case None => None
						case Some(id) => Some(new Contest(id = id))
					},
					guid = file.getName,
					language = Language.withName(req.language),
					status = Status.New,
					time = new Timestamp(new Date().getTime),
					ip = req.ip
				))

				RunNewOutputMessage(id = Some(run.guid))
			}
		}
	}

	def runStatus(req: RunStatusInputMessage)(implicit connection: Connection): Option[RunStatusOutputMessage] = {
		GraderData.getRun(req.id) map(
			run => RunStatusOutputMessage(run.status.toString,
					run.verdict.toString, run.score, run.runtime / 1000.0,
					run.memory / 1024.0 / 1024.0)
		)
	}

	def runList(req: RunListInputMessage)(implicit connection: Connection): Iterable[RunListOutputMessageEntry] = {
		GraderData.getRuns map(
			run => RunListOutputMessageEntry(run.guid, run.status.toString, run.verdict.toString,
					run.score, run.runtime / 1000.0, run.memory / 1024.0 / 1024.0)
		)
	}

	def main(args: Array[String]) = {
		val graderOptions = com.omegaup.grader.Service.parseOptions(args)

		// logger
		Logging.init

		val broadcaster = new Broadcaster
		val grader = new Grader(graderOptions)
		grader.addListener((ctx, run) => broadcaster.update(ctx))
		val servers = List[ServiceInterface](
			broadcaster,
			grader,
			new HttpService(grader, broadcaster)
		)

		info("omegaUp backend service ready")

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
