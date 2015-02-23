package com.omegaup

import com.omegaup.data._
import com.omegaup.grader.Grader
import com.omegaup.grader.GraderData
import com.omegaup.broadcaster.Broadcaster

import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import net.liftweb.json
import net.liftweb.json.Serialization
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import scala.collection.mutable.HashSet
import scala.collection.mutable.HashMap
import scala.collection.mutable.MutableList

class HttpHandler(grader: Grader, broadcaster: Broadcaster,
		updateConfig: Config => Unit) extends AbstractHandler with Log {
	private val RunStatusRegex = "/run/([a-f0-9]+)/status/?".r

	@throws(classOf[IOException])
	@throws(classOf[ServletException])
	override def handle(
		target: String,
		baseRequest: Request,
		request: HttpServletRequest,
		response: HttpServletResponse
	): Unit = {
		implicit val ctx = grader.serviceCtx
		if (request.getPathInfo() == "/" && ctx.config.grader.standalone) {
			response.setContentType("text/html")
			response.setStatus(HttpServletResponse.SC_OK)
			FileUtil.copy(
				(if (new File("index.html").exists) {
					new FileInputStream("index.html")
				} else {
					getClass.getResourceAsStream("/index.html")
				}
			), response.getOutputStream)
			log.debug("{} {} {}", request.getMethod, request.getPathInfo, response.getStatus)
			baseRequest.setHandled(true)
			return
		}

		implicit val formats = OmegaUpSerialization.formats

		response.setContentType("text/json")

		Serialization.write(request.getPathInfo() match {
			case "/grader/reload-config/" => {
				try {
					val mergedConfig = ConfigMerge(ctx.config,
						json.JsonParser.parse(request.getReader(), true))
					log.info("Configuration reloaded")

					updateConfig(mergedConfig)

					response.setStatus(HttpServletResponse.SC_OK)
					new ReloadConfigOutputMessage()
				} catch {
					case e: Exception => {
						log.error(e, "Reload config")
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
						new ReloadConfigOutputMessage(status = "error", error = Some(e.getMessage))
					}
				}
			}
			case "/grader/status/" => {
				response.setStatus(HttpServletResponse.SC_OK)
				new StatusOutputMessage(
					embedded_runner = ctx.config.grader.embedded_runner_enabled,
					queue = Some(grader.runnerDispatcher.status)
				)
			}
			case "/run/grade/" => {
				try {
					val req = Serialization.read[RunGradeInputMessage](request.getReader())
					response.setStatus(HttpServletResponse.SC_OK)
					grader.grade(req)
				} catch {
					case e: IllegalArgumentException => {
						log.error(e, "Grade failed")
						response.setStatus(HttpServletResponse.SC_NOT_FOUND)
						new RunGradeOutputMessage(status = "error", error = Some(e.getMessage))
					}
					case e: Exception => {
						log.error(e, "Grade failed")
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
						log.error(e, "Register failed")
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
						log.warn(e, "Deregister failed")
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
						log.error(e, "Broadcast failed")
						new BroadcastOutputMessage(status = "error", error = Some(e.getMessage))
					}
				}
			}
			case _ => {
				if (ctx.config.grader.standalone) request.getPathInfo match {
					case "/run/new/" => {
						try {
							var req = Serialization.read[RunNewInputMessage](request.getReader())
							response.setStatus(HttpServletResponse.SC_OK)
							implicit val connection: Connection = grader.conn
							req = req.copy(ip = Some(request.getRemoteAddr))
							val outputMessage = Service.runNew(req)
							grader.grade(RunGradeInputMessage(id = List(outputMessage.id.get)))
							outputMessage
						} catch {
							case e: IllegalArgumentException => {
								log.error(e, "Submitting new run failed")
								response.setStatus(HttpServletResponse.SC_NOT_FOUND)
								new RunNewOutputMessage(status = "error", error = Some(e.getMessage))
							}
							case e: Exception => {
								log.error(e, "Submitting new run failed")
								response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
								new RunNewOutputMessage(status = "error", error = Some(e.getMessage))
							}
						}
					}
					case RunStatusRegex(id) => {
						try {
							implicit val connection: Connection = grader.conn
							val outputMessage = Service.runStatus(id)
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
								log.error(e, "Getting run status failed")
								response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
								new NullMessage()
							}
						}
					}
					case "/run/list/" => {
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
								log.error(e, "Getting run list failed")
								response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
								new NullMessage()
							}
						}
					}
					case "/problem/list/" => {
						try {
							var req = request.getMethod match {
								case "POST" => Serialization.read[ProblemListInputMessage](request.getReader())
								case _ => ProblemListInputMessage()
							}
							implicit val connection: Connection = grader.conn
							response.setStatus(HttpServletResponse.SC_OK)
							Service.problemList(req)
						} catch {
							case e: Exception => {
								log.error(e, "Getting problem list failed")
								response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
								new NullMessage()
							}
						}
					}
					case "/problem/new/" => {
						try {
							implicit val connection: Connection = grader.conn
							response.setStatus(HttpServletResponse.SC_OK)
							Service.problemNew(request)
						} catch {
							case e: Exception => {
								log.error(e, "Creating new problem failed")
								response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
								new ProblemNewOutputMessage(status = "error",
									error = Some(e.getMessage))
							}
						}
					}
					case _ => {
						response.setStatus(HttpServletResponse.SC_NOT_FOUND)
						new NullMessage()
					}
				} else {
					response.setStatus(HttpServletResponse.SC_NOT_FOUND)
					new NullMessage()
				}
			}
		}, response.getWriter())

		log.debug("{} {} {}", request.getMethod, request.getPathInfo, response.getStatus)
		baseRequest.setHandled(true)
	}
}

class HttpService(grader: Grader, broadcaster: Broadcaster,
		updateConfig: Config => Unit)(implicit var ctx: Context) extends
		ServiceInterface with Log {
	val server = new org.eclipse.jetty.server.Server

	{
		val graderConnector = (if (ctx.config.ssl.disabled) {
			new org.eclipse.jetty.server.ServerConnector(server)
		} else {
			// boilerplate code for jetty with https support
			val sslContext = new org.eclipse.jetty.util.ssl.SslContextFactory(
				ctx.config.ssl.keystore_path)
			sslContext.setKeyManagerPassword(ctx.config.ssl.password)
			sslContext.setKeyStorePassword(ctx.config.ssl.keystore_password)
			sslContext.setTrustStore(FileUtil.loadKeyStore(
				ctx.config.ssl.truststore_path,
				ctx.config.ssl.truststore_password
			))
			sslContext.setNeedClientAuth(true)

			new org.eclipse.jetty.server.ServerConnector(server, sslContext)
		})
		graderConnector.setPort(ctx.config.grader.port)

		server.setConnectors(List(graderConnector).toArray)

		server.setHandler(new HttpHandler(grader, broadcaster, updateConfig))
	}

	override def updateContext(newCtx: Context) = {
		ctx = newCtx
	}

	override def start(): Unit = {
		server.start()
		log.info("omegaUp HTTPS service started")
	}

	override def stop(): Unit = {
		log.info("omegaUp HTTPS service stopping")
		server.stop
	}

	override def join(): Unit = {
		server.join
		log.info("omegaUp HTTPS service stopped")
	}
}

object Service extends Object with Log with Using with ContextMixin {
	private val AliasRegex = "[a-zA-Z0-9][a-zA-Z0-9_]{2,}".r
	private val servers = MutableList[ServiceInterface]()

	def runNew(req: RunNewInputMessage)
	(implicit connection: Connection): RunNewOutputMessage = {
		import java.util.Date
		import java.sql.Timestamp
		import java.text.SimpleDateFormat

		val (file, guid) = FileUtil.createRandomFile(
			new java.io.File(serviceCtx.config.common.roots.submissions)
		)
		FileUtil.write(file, req.code)

		GraderData.getProblem(req.problem) match {
			case None => throw new IllegalArgumentException("Problem " + req.problem + " not found")
			case Some(problem) => {
				val run = GraderData.insertRun(new Run(
					problem = problem,
					guid = guid,
					language = Language.withName(req.language),
					status = Status.New,
					time = new Timestamp(new Date().getTime),
					ip = req.ip.getOrElse("0.0.0.0")
				))

				RunNewOutputMessage(id = Some(run.guid))
			}
		}
	}

	def runStatus(id: String)
	(implicit connection: Connection): Option[RunStatusOutputMessage] = {
		implicit val formats = OmegaUpSerialization.formats
		GraderData.getRun(id) map(
			run => {
				val sourceFile = new File(serviceCtx.config.common.roots.submissions,
					run.guid.substring(0, 2) + "/" + run.guid.substring(2))
				val groupsFile = new File(serviceCtx.config.common.roots.grade,
					run.id.toString + "/details.json")
				val compileErrorFile = new File(serviceCtx.config.common.roots.grade,
					run.id.toString + ".err")

				RunStatusOutputMessage(run.problem.alias, run.status.toString,
					run.verdict.toString, run.score, run.runtime / 1000.0,
					run.memory / 1024.0 / 1024.0,
					source = FileUtil.read(sourceFile),
					compile_error = compileErrorFile.exists match {
						case false => None
						case true => Some(FileUtil.read(compileErrorFile))
					},
					groups = groupsFile.exists match {
						case false => None
						case true => Some(Serialization.read[List[GroupVerdictMessage]](
							new FileReader(groupsFile)
						))
					}
				)
			}
		)
	}

	def runList(req: RunListInputMessage)(implicit connection: Connection):
			Iterable[RunListOutputMessageEntry] = {
		GraderData.getRuns map(
			run => RunListOutputMessageEntry(run.problem.alias, run.guid,
				run.status.toString, run.verdict.toString, run.score,
				run.runtime / 1000.0, run.memory / 1024.0 / 1024.0)
		)
	}

	def handleProblemUpload(request: HttpServletRequest):
			(Path, HashMap[String, String]) = {
		if (!ServletFileUpload.isMultipartContent(request)) {
			throw new IllegalArgumentException("Request is not multipart/form-data");
		}

		val uploadDirectory = Files.createTempDirectory(
			Paths.get("/tmp"), "omegaup-upload")

		try {
			val casesPath = uploadDirectory.resolve("cases")
			val inPath = casesPath.resolve("in")
			val outPath = casesPath.resolve("out")
			Files.createDirectory(casesPath)
			Files.createDirectory(inPath)
			Files.createDirectory(outPath)

			val upload = new ServletFileUpload
			val iter = upload.getItemIterator(request)
			val params = HashMap.empty[String, String]
			val inputs = HashSet.empty[String]
			val outputs = HashSet.empty[String]
			while (iter.hasNext) {
				val item = iter.next
				val name = item.getFieldName
				val stream = item.openStream
				if (item.isFormField) {
					params(name) = FileUtil.read(stream)
				} else if (name == "problem_contents") {
					val zipStream = new ZipArchiveInputStream(stream)
					var entry: ArchiveEntry = null
					while ( { entry = zipStream.getNextEntry ; entry != null } ) {
						val target = uploadDirectory.resolve(entry.getName).normalize
						// Make sure the target is contained within the uploaded dir.
						if (target.startsWith(uploadDirectory)) {
							if (entry.getName.startsWith("cases/")) {
								val filename = new File(entry.getName).getName
								if (filename.endsWith(".in")) {
									Files.copy(zipStream, inPath.resolve(filename))
									inputs += FileUtil.removeExtension(filename)
								} else if (filename.endsWith(".out")) {
									Files.copy(zipStream, outPath.resolve(filename))
									outputs += FileUtil.removeExtension(filename)
								}
							} else if (entry.isDirectory) {
								Files.createDirectory(target)
							} else {
								Files.copy(zipStream, target)
							}
						}
					}
					if (inputs != outputs) {
						throw new IllegalArgumentException("Mismatched inputs: [" +
							(inputs &~ outputs).mkString(", ") + "], outputs: [" +
							(outputs &~ inputs).mkString(", ") + "]")
					}
				}
			}

			uploadDirectory -> params
		} catch {
			case e: Exception => {
				FileUtil.deleteDirectory(uploadDirectory.toFile)
				throw e
			}
		}
	}

	def problemNew(request: HttpServletRequest)
	(implicit connection: Connection): ProblemNewOutputMessage = {
		val (uploadDirectory, params) = handleProblemUpload(request)
		try {
			params("alias") match {
				case AliasRegex(_*) => {
					// Everything's cool
				}
				case _ => {
					throw new IllegalArgumentException("Alias should be alphanumeric and at least three characters long")
				}
			}
			if (params("title").length == 0) {
				throw new IllegalArgumentException("Title cannot be empty")
			}
			val problem = new Problem(
				title = params("title"),
				alias = params("alias"),
				validator = Validator.withName(params("validator")),
				time_limit =
					params.get("time_limit").map(x => (x.toDouble * 1000.0).toLong),
				overall_wall_time_limit =
					params.get("overall_wall_time_limit").map(x => (x.toDouble * 1000.0).toLong),
				extra_wall_time =
					(params.getOrElse("extra_wall_time", "0").toDouble * 1000.0).toLong,
				memory_limit =
					params.get("memory_limit").map(x => (x.toDouble * 1024.0).toLong),
				output_limit =
					params.get("output_limit").map(x => (x.toDouble * 1024.0).toLong),
				stack_limit =
					params.get("stack_limit").map(x => (x.toDouble * 1024.0).toLong)
			)
			FileUtil.write(uploadDirectory.resolve("manifest.mf").toFile,
s"""alias:${problem.alias}
title:${problem.title}
validator:${problem.validator.toString}
time_limit:${problem.time_limit.getOrElse(-1)}
overall_wall_time_limit:${problem.overall_wall_time_limit.getOrElse(-1)}
extra_wall_time:${problem.extra_wall_time}
memory_limit:${problem.memory_limit.getOrElse(-1)}
output_limit:${problem.output_limit.getOrElse(-1)}
stack_limit:${problem.stack_limit.getOrElse(-1)}""")

			val problemDirectory =
				Paths.get(serviceCtx.config.common.roots.problems, problem.alias)

			val git = new Git(uploadDirectory.toFile)
			git.init
			git.commit("omegaup", "Initial commit")

			GraderData.insertProblem(problem)
			org.apache.commons.io.FileUtils.moveDirectory(
				uploadDirectory.toFile, problemDirectory.toFile)
			ProblemNewOutputMessage()
		} finally {
			FileUtil.deleteDirectory(uploadDirectory.toFile)
		}
	}

	def problemList(req: ProblemListInputMessage)
	(implicit connection: Connection):
	Iterable[ProblemListOutputMessageEntry] = {
		GraderData.getProblems map(
			problem => {
				val statementsDirectory = new File(
					new File(serviceCtx.config.common.roots.problems, problem.alias), "statements")
				ProblemListOutputMessageEntry(
					problem.alias,
					problem.title,
					problem.validator.toString,
					problem.time_limit.map(_.toDouble / 1000.0),
					problem.overall_wall_time_limit.map(_.toDouble / 1000.0),
					problem.extra_wall_time / 1000.0,
					problem.memory_limit.map(_.toDouble / 1024.0),
					problem.output_limit.map(_.toDouble / 1024.0),
					problem.stack_limit.map(_.toDouble / 1024.0),
					problem.points,
					problem.slow,
					statementsDirectory.listFiles
						.filter { f =>
							f.getName.endsWith(".markdown") || f.getName.endsWith(".md")
						}
						.map { f =>
							FileUtil.removeExtension(f.getName) -> FileUtil.read(f)
						}
						.toMap
				)
			}
		)
	}

	override def updateContext(newCtx: Context) = {
		super.updateContext(newCtx)

		servers.foreach(_.updateContext(serviceCtx))
		Logging.init
	}

	override def start() = {
		val grader = new Grader
		if (serviceCtx.config.grader.standalone) {
			val submissions = new File(serviceCtx.config.common.roots.submissions)
			for (i <- 0 until 256) {
				new File(submissions, f"$i%02x").mkdirs
			}
			new File(serviceCtx.config.common.roots.grade).mkdirs
			new File(serviceCtx.config.common.roots.problems).mkdirs
			new File(serviceCtx.config.common.roots.input).mkdirs
			new File(serviceCtx.config.common.roots.compile).mkdirs
			implicit val connection: Connection = grader.conn
			GraderData.init
		}
		servers += grader

		val broadcaster = new Broadcaster
		grader.addListener((ctx, run) => broadcaster.update()(ctx))
		servers += broadcaster

		val httpService = new HttpService(grader, broadcaster, config =>
			updateContext(new Context(config, serviceCtx.overrideLogger))
		)
		servers += httpService

		servers.foreach(_.start)

		log.info("omegaUp backend service ready")

		Runtime.getRuntime.addShutdownHook(new Thread() {
			override def run() = {
				log.info("Shutting down")
				try {
					servers.foreach(_.stop)
				} catch {
					case e: Exception => {
						log.error(e, "Error shutting down. Good night.")
					}
				}
			}
		});

		servers.foreach(_.join)
		log.info("Shut down cleanly")
	}
}

/* vim: set noexpandtab: */
