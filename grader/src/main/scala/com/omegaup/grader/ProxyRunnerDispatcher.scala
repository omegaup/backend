package com.omegaup.grader

import com.omegaup.Database._
import com.omegaup._
import com.omegaup.data._
import com.omegaup.grader.drivers._
import com.omegaup.runner._
import java.io.File
import java.io.IOException
import java.net.CookieManager
import java.net.HttpCookie
import java.net.URI
import java.nio.ByteBuffer
import java.sql.Connection
import java.text.ParseException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage
import org.eclipse.jetty.websocket.api.annotations.WebSocket
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient
import scala.collection.JavaConversions._
import scala.collection.immutable.{HashMap, HashSet}
import scala.util.parsing.combinator.syntactical._

import spray.json._
import com.omegaup.data.OmegaUpProtocol._
import DefaultJsonProtocol._

object ProxyGraderData {
	def getLocal(remote_guid: String)(implicit connection: Connection, ctx: Context): Option[(Long, String)] =
		query("""
			SELECT
				run_id, local_guid
			FROM
				Proxied_Runs
			WHERE
				remote_guid = ?;
			""",
			remote_guid
		) { rs => (rs.getLong("run_id"), rs.getString("local_guid")) }

	def getRemoteGuid(id: Long)(implicit connection: Connection, ctx: Context): Option[String] =
		query("""
			SELECT
				remote_guid
			FROM
				Proxied_Runs
			WHERE
				run_id = ?;
			""",
			id
		) { rs => rs.getString("remote_guid") }

	def save(id: Long, local_guid: String, remote_guid: String)(implicit connection: Connection, ctx: Context): Unit =
		execute(
			"REPLACE INTO Proxied_Runs (run_id, local_guid, remote_guid) VALUES(?, ?, ?);",
			id, local_guid, remote_guid
		)
}

case class LoginResponseMessage(status: String, error: Option[String], auth_token: String)
case class SubmissionResponseMessage(status: String, error: Option[String], guid: String, submission_deadline: Long)
case class RunStatusMessage(status: String, verdict: String, runtime: Int, memory: Int, score: Double, contest_score: Option[Double])
case class OmegaUpSimpleResponse(status: String)
case class OmegaUpErrorMessage(status: String, error: String, errorname: String, errorcode: Int, header: String)

object ProxyProtocol extends DefaultJsonProtocol {
	implicit val loginResponseProtocol =
		jsonFormat3(LoginResponseMessage)
	implicit val submissionResponseProtocol =
		jsonFormat4(SubmissionResponseMessage)
	implicit val runStatusMessageProtocol =
		jsonFormat6(RunStatusMessage)
	implicit val omegaUpSimpleResponseProtocol =
		jsonFormat1(OmegaUpSimpleResponse)
	implicit val omegaUpErrorMessageProtocol =
		jsonFormat5(OmegaUpErrorMessage)
}
import ProxyProtocol._

class ProxiedService(serviceName: String) extends RunnerService {
	override def name(): String = serviceName

	override def compile(message: com.omegaup.data.CompileInputMessage)
		(implicit ctx: com.omegaup.Context): com.omegaup.data.CompileOutputMessage = {
		throw new NotImplementedError
	}
	override def input(inputName: String,entries: Iterable[com.omegaup.InputEntry])
		(implicit ctx: com.omegaup.Context): com.omegaup.data.InputOutputMessage = {
		throw new NotImplementedError
	}
	override def run(message: com.omegaup.data.RunInputMessage,
		callback: com.omegaup.RunCaseCallback)
		(implicit ctx: com.omegaup.Context): com.omegaup.data.RunOutputMessage = {
		throw new NotImplementedError
	}
}

class ProxiedContext(val parent: RunContext, grader: Grader, val remote_guid: String) extends RunContext(
	parent, Some(grader), parent.run, parent.debug, parent.rejudge,
	Some(parent.overrideLogger)) with Log {
}

class ProxyRunnerDispatcher(grader: Grader)(implicit connection: Connection, var serviceCtx: Context)
		extends RunnerDispatcherInterface with Log {
	private val authTokens = scala.collection.mutable.HashMap.empty[String, String]
	private val runsInFlight = scala.collection.mutable.HashMap.empty[Long, ProxiedContext]
	private var flightIndex: Long = 0
	private val lock = new Object
	private val socketThread = new ContestWebSocket(this)
	private val injectionThread = new InjectionThread
	private val runRecoveryThread = new RunRecoveryThread
	private val resultProcessingThread = new ResultProcessingThread
	private val service = new ProxiedService(serviceCtx.config.proxy.remote_host)
	private val webSocketProtocol = if (serviceCtx.config.proxy.use_tls) { "wss" } else { "ws" }
	private val httpProtocol = if (serviceCtx.config.proxy.use_tls) { "https" } else { "http" }
	private val remoteHost = serviceCtx.config.proxy.remote_host
	private val contestAlias = serviceCtx.config.proxy.contest_alias
	private val passwords = serviceCtx.config.proxy.contestants.map(x => {
		val tokens = x.split(":")
		assert(tokens.length == 2)
		Tuple2(tokens(0), tokens(1))
	}).toMap
	private var adminAuthToken: String = null

	override def start() = {
		socketThread.start
		runRecoveryThread.start
		injectionThread.start
		resultProcessingThread.start
	}

	override def stop(): Unit = {
		socketThread.close
		runRecoveryThread.close
		injectionThread.close
		resultProcessingThread.close
	}

	override def join(): Unit = {
		try {
			socketThread.join
			runRecoveryThread.join
			injectionThread.join
			resultProcessingThread.join
		} catch {
			case _: Exception => {}
		}
	}

	override def status(): QueueStatus = lock.synchronized {
		QueueStatus(
			run_queue_length = injectionThread.queueSize,
			runner_queue_length = -1,
			runners = List(),
			running = runsInFlight.values.map(ifr => new Running(ifr.service.name, ifr.run.id.toInt)).toList
		)
	}

	override def updateContext(newCtx: Context) = {}

	override def register(hostname: String, port: Int): EndpointRegisterOutputMessage = {
		throw new NotImplementedError
	}

	override def deregister(hostname: String, port: Int): EndpointRegisterOutputMessage = {
		throw new NotImplementedError
	}

	override def addRunner(runner: RunnerService): Unit = {
		throw new NotImplementedError
	}

	override def addRun(ctx: RunContext): Unit = {
		ctx.queued()
		log.debug("Adding run {}", ctx)
		injectionThread.add(ctx)
	}

	private def getAuthToken(ctx: RunContext) = lock.synchronized {
		val username = ctx.run.user.get.username
		if (!authTokens.contains(username)) {
			assert(passwords.contains(username))
			authTokens(username) = loginLocked(username, passwords(username))
		}
		authTokens(username)
	}

	private def clearAuthToken(ctx: RunContext) = lock.synchronized {
		val username = ctx.run.user.get.username
		if (authTokens.contains(username)) {
			authTokens.remove(username)
		}
	}

	private def getAdminAuthToken(): String = lock.synchronized {
		if (adminAuthToken == null) {
			adminAuthToken = loginLocked(
				serviceCtx.config.proxy.admin_username,
				serviceCtx.config.proxy.admin_password)
		}
		adminAuthToken
	}

	private def clearAdminAuthToken() = lock.synchronized {
		adminAuthToken = null
	}

	private def loginLocked(username: String, password: String): String = {
		log.info("Remotely logging in as '{}' on '{}'", username,
			s"${httpProtocol}://${remoteHost}/api/user/login")
		val result = Https.post[LoginResponseMessage](
			s"${httpProtocol}://${remoteHost}/api/user/login/",
			Map("usernameOrEmail" -> username, "password" -> username),
			false)
		log.info("Logged in as '{}'", username)
		result.auth_token
	}

	@WebSocket(maxTextMessageSize = 64 * 1024, maxIdleTime = 30 * 1000)
	private class ContestWebSocket(dispatcher: ProxyRunnerDispatcher)(implicit var serviceCtx: Context) extends Thread with Log {
		private val client = new WebSocketClient
		private var session: Session = null
		private var closeLatch = new CountDownLatch(0)
		private var connectLatch: CountDownLatch = null
		private var running: Boolean = true

		def awaitClose() = closeLatch.await()

		@OnWebSocketClose
		def onClose(statusCode: Int, reason: String): Unit = {
			log.info("Connection closed: {} - {}", statusCode, reason)
			session = null
			closeLatch.countDown
		}

		@OnWebSocketConnect
		def onConnect(session: Session): Unit = {
			log.info("Connected: {}", session)
			this.session = session
			lock.synchronized {
				for ((id, ctx) <- runsInFlight) {
					runRecoveryThread.add(ctx)
				}
			}
			closeLatch = new CountDownLatch(1)
			connectLatch.countDown
		}

		@OnWebSocketMessage
		def onMessage(msg: String): Unit = {
			try {
				val updateMessage = Serialization.readString[UpdateRunMessage](msg)
				log.info("Message: {}", updateMessage)
				resultProcessingThread.add(updateMessage.run)
			} catch {
				case e: Exception => { log.error(e, "Error deserializing") }
			}
		}

		@OnWebSocketError
		def onError(error: Throwable): Unit = {
			log.error(error, "Unable to connect")
			connectLatch.countDown
		}

		def close(): Unit = {
			running = false
			client.stop
		}

		override def run(): Unit = {
			val uri = new URI(s"${webSocketProtocol}://${remoteHost}/api/contest/events/${contestAlias}/")

			client.start

			val pingPayload = ByteBuffer.wrap("ping".getBytes)
			
			while (running) {
				val cookieManager = new CookieManager
				cookieManager.getCookieStore.add(
					new URI(s"${httpProtocol}://${remoteHost}"),
					new HttpCookie("ouat", getAdminAuthToken))
				client.setCookieStore(cookieManager.getCookieStore)
				val request = new ClientUpgradeRequest
				log.info("Connecting to the websocket in {}", uri)
				connectLatch = new CountDownLatch(1)
				client.connect(this, uri, request)
				connectLatch.await
				try {
					while (closeLatch.getCount > 0 && running) {
						session.getRemote.sendPing(pingPayload)
						try {
							Thread.sleep(10000)
						} catch {
							case e: InterruptedException => {}
						}
					}
					closeLatch.await
				} catch {
					case _: Exception => {}
				}
				try {
					Thread.sleep(5000)
				} catch {
					case e: InterruptedException => {}
				}
			}
		}
	}

	private abstract class ProcessingThread[T] extends Thread with Log {
		private val queue = new LinkedBlockingQueue[Option[T]]

		def add(t: T) = queue.put(Some(t))

		def close() = queue.put(None)

		def queueSize() = queue.size

		override def run(): Unit = while (true) {
			queue.take match {
				case Some(t) => {
					try {
						process(t)
					} catch {
						case e: Exception => {
							log.error(e, "Exception trying to process {}. Retrying", t.toString)
							queue.put(Some(t))
							try {
								Thread.sleep(15000)
							} catch {
								case e: InterruptedException => {}
							}
						}
					}
				}
				case None => return
			}
		}

		protected def process(t: T): Unit
	}

	private class InjectionThread extends ProcessingThread[RunContext] with Log {
		override def process(ctx: RunContext): Unit = {
			val remote_guid = getRemoteGuid(ctx)
			ctx.startFlight(service)
			lock.synchronized {
				runsInFlight(ctx.run.id) = new ProxiedContext(ctx, grader, remote_guid)
			}
		}

		private def getRemoteGuid(ctx: RunContext): String = {
			ProxyGraderData.getRemoteGuid(ctx.run.id) match {
				case Some(guid) => {
					log.debug("Recovered remote guid {} for {}", guid, ctx.run.id)
					if (ctx.rejudge) {
						Https.post_error[OmegaUpSimpleResponse, OmegaUpErrorMessage](
							s"${httpProtocol}://${remoteHost}/api/run/rejudge/run_alias/${guid}/",
							Map("auth_token" -> getAdminAuthToken),
							false) match {
							case Left(result) => {
								log.info("Requested rejudge for {}", ctx.run.id)
							}
							case Right(error) => {
								if (error.errorcode == 401) {
									clearAdminAuthToken
								}
								log.error("Error submitting run: {}", error)
								throw new IOException(error.errorname)
							}
						}
					}
					guid
				}
				case None => {
					log.debug("Obtaining remote guid for {}", ctx.run.id)
					val code = FileUtil.read(
						ctx.config.common.roots.submissions + "/" +
						ctx.run.guid.substring(0, 2) + "/" + ctx.run.guid.substring(2))
					log.debug("Creating run")
					Https.post_error[SubmissionResponseMessage, OmegaUpErrorMessage](
						s"${httpProtocol}://${remoteHost}/api/run/create/",
						Map("contest_alias" -> contestAlias, "problem_alias" -> ctx.run.problem.alias,
							"language" -> ctx.run.language.toString, "source" -> code,
							"auth_token" -> getAuthToken(ctx)),
						false) match {
						case Left(result) => {
							log.debug("Run created {}", result.guid)
							ProxyGraderData.save(ctx.run.id, ctx.run.guid, result.guid)
							result.guid
						}
						case Right(error) => {
							if (error.errorcode == 401) {
								clearAuthToken(ctx)
							}
							log.error("Error submitting run: {}", error)
							throw new IOException(error.errorname)
						}
					}
				}
			}
		}
	}

	private class RunRecoveryThread extends ProcessingThread[ProxiedContext] with Log {
		override def process(ctx: ProxiedContext): Unit = {
			Https.post_error[RunStatusMessage, OmegaUpErrorMessage](
				s"${httpProtocol}://${remoteHost}/api/run/status/run_alias/${ctx.remote_guid}/",
				Map("auth_token" -> getAuthToken(ctx)),
				false) match {
				case Left(result) => {
					if (result.status == "ready") {
						resultProcessingThread.add(new RunDetails(
							verdict = result.verdict,
							runtime = result.runtime,
							memory = result.memory,
							score = result.score,
							contest_score = result.contest_score,
							status = result.status,
							// The rest we don't care.
							username = None,
							contest_alias = None,
							alias = "",
							guid = ctx.remote_guid,
							submit_delay = 0,
							time = 0,
							language = "",
							penalty = 0
						))
					} else {
						log.info("Run {} not ready yet", result)
					}
				}
				case Right(error) => {
					if (error.errorcode == 401) {
						clearAuthToken(ctx)
					}
					log.error("Error submitting run: {}", error)
					throw new IOException(error.errorname)
				}
			}
		}
	}

	private class ResultProcessingThread extends ProcessingThread[RunDetails] with Log {
		override def process(details: RunDetails): Unit = {
			getContextForDetails(details).foreach(ctx => {
				ctx.run.verdict = Verdict.withName(details.verdict)
				ctx.run.runtime = details.runtime.toLong
				ctx.run.memory = details.memory
				ctx.run.score = details.score
				ctx.run.contest_score = details.contest_score
				ctx.run.status = Status.Running
				ctx.updateVerdict(ctx.run)

				downloadRunMetadata(ctx)
				downloadRunResults(ctx)

				ctx.run.status = Status.withName(details.status)
				ctx.updateVerdict(ctx.run)
				lock.synchronized {
					runsInFlight.remove(ctx.run.id)
				}
				ctx.parent.finish
			})
		}

		def getContextForDetails(details: RunDetails): Option[ProxiedContext] = {
			ProxyGraderData.getLocal(details.guid) match {
				case Some((id, local_guid)) => {
					log.info("Run details from {} are here!: {}", id, details)
					lock.synchronized {
						if (!runsInFlight.contains(id)) {
							GraderData.getRun(local_guid) match {
								case None => {
									log.error("Received update on unknown run {}", details.guid)
									return None
								}
								case Some(run) => {
									log.error("Recreating run {}", details.guid)
									val ctx = new RunContext(serviceCtx, Some(grader), run,
										false, false, None)
									runsInFlight(id) = new ProxiedContext(ctx, grader, details.guid)
								}
							}
						}
						Some(runsInFlight(id))
					}
				}
				case None => {
					log.error("Received update on unknown run {}", details.guid)
					None
				}
			}
		}

		def downloadRunMetadata(ctx: ProxiedContext) = {
			val graderDir = new File(
				ctx.config.common.roots.grade + "/" +
				ctx.run.guid.substring(0, 2) + "/" + ctx.run.guid.substring(2))
			graderDir.mkdirs
			Https.post_string(
				s"${httpProtocol}://${remoteHost}/api/run/details/run_alias/${ctx.remote_guid}/",
				Map("auth_token" -> getAdminAuthToken),
				false) match {
				case Left(jsonString) => {
					log.debug("Got a json string: {}", jsonString)
					val json = jsonString.parseJson.asJsObject
					json.getFields("compile_error") match {
						case Seq(JsString(compile_error)) => {
							FileUtil.write(new File(graderDir, "compile_error.log"), compile_error)
						}
						case _ => {}
					}
					json.getFields("logs") match {
						case Seq(JsString(logs)) => {
							FileUtil.write(new File(graderDir, "run.log"), logs)
						}
						case _ => {}
					}
					json.getFields("judged_by") match {
						case Seq(JsString(judged_by)) => {
							ctx.run.judged_by = Some(judged_by)
						}
						case _ => {}
					}
					json.getFields("groups") match {
						case Seq(groups: JsValue) => {
							FileUtil.write(new File(graderDir, "details.json"), groups.compactPrint)
						}
						case _ => {}
					}
				}
				case Right(errorcode) => {
					if (errorcode == 401) {
						clearAdminAuthToken
					}
					log.error("Error downloading run metadata: {}", errorcode)
					throw new IOException("HTTP " + errorcode)
				}
			}
		}

		def downloadRunResults(ctx: ProxiedContext): Unit = {
			val filename = ctx.config.common.roots.grade + "/" +
				ctx.run.guid.substring(0, 2) + "/" + ctx.run.guid.substring(2) +
				"/results.zip"
			val errorcode = Https.post_zip(
				s"${httpProtocol}://${remoteHost}/api/run/download/run_alias/${ctx.remote_guid}/",
				Map("auth_token" -> getAdminAuthToken),
				filename,
				false)
			if (errorcode != 200) {
				if (errorcode == 401) {
					clearAdminAuthToken
				}
				log.error("Error downloading run metadata: {}", errorcode)
				throw new IOException("HTTP " + errorcode)
			}
		}
	}
}

/* vim: set noexpandtab: */
