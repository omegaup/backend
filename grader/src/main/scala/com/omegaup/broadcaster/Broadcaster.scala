package com.omegaup.broadcaster

import com.omegaup._
import com.omegaup.data.OmegaUpProtocol._
import com.omegaup.data._
import com.omegaup.grader._
import java.io._
import java.util.concurrent._
import java.util.zip._
import javax.servlet._
import javax.servlet.http._
import org.eclipse.jetty.server._
import org.eclipse.jetty.servlet._
import org.eclipse.jetty.websocket.api._
import org.eclipse.jetty.websocket.server.WebSocketHandler
import org.eclipse.jetty.websocket.servlet._
import scala.collection.JavaConversions._
import scala.collection.{mutable,immutable}

class QueuedElement(val contest: String, val broadcast: Boolean, val targetUser: Long, val userOnly: Boolean) {}
class QueuedRun(contest: String, broadcast: Boolean, targetUser: Long, userOnly: Boolean, val ctx: RunContext)
	extends QueuedElement(contest, broadcast, targetUser, userOnly) {}
class QueuedMessage(contest: String, broadcast: Boolean, targetUser: Long, userOnly: Boolean, val message: String)
	extends QueuedElement(contest, broadcast, targetUser, userOnly) {}

class Broadcaster(implicit var serviceCtx: Context) extends Object with
		ServiceInterface with Runnable with Log with Using {
	private val PathRE = "^/([a-zA-Z0-9_-]+)/?".r
	// A collection of subscribers.
	private val subscribers = new mutable.HashMap[String, mutable.ArrayBuffer[BroadcasterSession]]
	private val subscriberLock = new Object
	private val PoisonPill = new QueuedElement(null, true, -1, false)
	private val queue = new LinkedBlockingQueue[QueuedElement]
	private val broadcastThread = new Thread(this, "BroadcastThread")
	private val server = new org.eclipse.jetty.server.Server

	override def start() = {
		val broadcasterConnector = new org.eclipse.jetty.server.ServerConnector(server)
		broadcasterConnector.setPort(serviceCtx.config.broadcaster.port)
		server.addConnector(broadcasterConnector)

		val creator = new WebSocketCreator() {
			override def createWebSocket(req: ServletUpgradeRequest, resp: ServletUpgradeResponse): Object = {
				resp.setAcceptedSubProtocol("com.omegaup.events")
				new BroadcasterSocket
			}
		}

		server.setHandler(new WebSocketHandler() {
			override def configure(factory: WebSocketServletFactory): Unit = {
				factory.setCreator(creator)
			}
		})

		server.start

		log.info("Registering port {}", broadcasterConnector.getLocalPort)

		broadcastThread.start

		log.info("Broadcaster started")
	}

	override def updateContext(newCtx: Context) = {
		serviceCtx = newCtx
	}

	def subscribe(session: BroadcasterSession) = {
		subscriberLock.synchronized {
			if (!subscribers.contains(session.contest)) {
				subscribers.put(session.contest, new mutable.ArrayBuffer[BroadcasterSession])
			}
			subscribers(session.contest) += session
			log.info("Connected {}->{} ({})", session.user, session.contest, subscribers(session.contest).length)
		}
	}

	def unsubscribe(session: BroadcasterSession) = {
		if (session != null) {
			subscriberLock.synchronized {
				if (subscribers.contains(session.contest)) {
					subscribers(session.contest) -= session
					log.info("Disconnected {}->{} ({})", session.user, session.contest, subscribers(session.contest).length)
				}
			}
		}
	}

	def hashdigest(algorithm: String, s: String): String = {
		val hexdigest = new StringBuffer

		for (c <- java.security.MessageDigest.getInstance(algorithm).digest(s.getBytes)) {
			val hex = Integer.toHexString(0xFF & c)
			if (hex.length == 1) {
				hexdigest.append('0')
			}
			hexdigest.append(hex)
		}

		return hexdigest.toString
	}

	def update()(implicit ctx: RunContext): Unit = {
		ctx.run.contest match {
			case Some(contest) => {
				ctx.broadcastQueued
				queue.put(new QueuedRun(contest.alias, false,
					ctx.run.user match {
						case Some(user) => user.id
						case None => -1
					}, false, ctx))
			}
			case None => {
				ctx.finish
			}
		}
	}

	def broadcast(
		contest: String,
		message: String,
		broadcast: Boolean,
		targetUser: Long = -1,
		userOnly: Boolean = false
	): BroadcastOutputMessage = {
		queue.put(new QueuedMessage(contest, broadcast, targetUser, userOnly, message))
		new BroadcastOutputMessage(status = "ok")
	}

	private def runLoop(elm: QueuedElement): Unit = {
		val message = elm match {
			case m: QueuedRun => {
				m.ctx.broadcastDequeued
				val run = m.ctx.run

				if (!serviceCtx.config.grader.scoreboard_refresh.disabled) {
					m.ctx.trace(EventCategory.GraderRefresh) {
						try {
							log.info("Scoreboard refresh {}",
								Https.post[ScoreboardRefreshResponse](
									serviceCtx.config.grader.scoreboard_refresh.url,
									Map(
										"token" -> serviceCtx.config.grader.scoreboard_refresh.token,
										"alias" -> elm.contest,
										"run" -> run.id.toString
									),
									runner = false
								)
							)
						} catch {
							case e: Exception => log.error(e, "Scoreboard refresh")
						}
					}
				}

				m.ctx.finish

				Serialization.writeString(UpdateRunMessage("/run/update/",
					RunDetails(
						username = run.user.map(_.username),
						contest_alias = Some(elm.contest),
						alias = run.problem.alias,
						guid = run.guid,
						runtime = run.runtime,
						memory = run.memory,
						score = run.score,
						contest_score = run.contest_score,
						status = run.status.toString,
						verdict = run.verdict.toString,
						submit_delay = run.submit_delay,
						time = run.time.getTime / 1000,
						language = run.language.toString
					)
				))
			}

			case m: QueuedMessage => {
				m.message
			}
		}

		val notifyList = subscriberLock.synchronized {
			if (subscribers.contains(elm.contest)) {
				subscribers(elm.contest)
					.filter(subscriber =>
						(
							elm.broadcast ||
							subscriber.admin ||
							elm.targetUser == subscriber.user
						) && (
							!elm.userOnly ||
							!subscriber.admin
						)
					)
			} else {
				null
			}
		}

		if (notifyList != null)
			notifyList.foreach(_.send(message))
	}

	override def run(): Unit = {
		while (true) {
			try {
				val elm = queue.take
				if (elm == PoisonPill) {
					log.info("Broadcaster thread finished normally")
					return
				}
				runLoop(elm)
			} catch {
				case e: Exception => log.error(e, "runLoop")
			}
		}
	}

	class BroadcasterSession(val user: Int, val contest: String, val admin: Boolean, val session: Session) {
		def send(message: String): Unit = {
			if (!session.isOpen) return
			try {
				session.getRemote.sendString(message)
			} catch {
				case e: Exception => {
					log.error(e, "Failed to send a message")
					close
				}
			}
		}

		def close(): Unit = {
			if (!session.isOpen) return
			try {
				session.close(1000, "done")
			} catch {
				case e: Exception => {
					log.error(e, "Failed to close the socket")
				}
			}
		}

		def isOpen() = session.isOpen
	}

	class BroadcasterSocket extends WebSocketAdapter with Log {
		private var session: BroadcasterSession = null

		override def onWebSocketConnect(sess: Session): Unit = {
			log.info("Connecting from {}", sess.getRemoteAddress.getAddress)
			session = getSession(sess)
			if (session == null) {
				sess.close(new CloseStatus(1000, "forbidden"))
			} else {
				subscribe(session)
			}
		}

		private def getScoreboardSession(sess: Session, contest: String): BroadcasterSession = {
			val query = sess.getUpgradeRequest.getRequestURI.getQuery.split("=")
			if (query.length != 2) return null
			try {
				val response = Https.post[ContestRoleResponse](
					serviceCtx.config.omegaup.role_url,
					Map("token" -> query(1), "contest_alias" -> contest),
					runner = false
				)
				if (response.status == "ok") {
					return new BroadcasterSession(0, contest, response.admin, sess)
				}
			} catch {
				case e: Exception => {
					log.error(e, "Error getting role")
				}
			}
			null
		}

		private def getUserId(request: UpgradeRequest): (Int, String) = {
			// Find user ID.
			val cookies = request.getCookies.filter(_.getName == "ouat")
			val userId = if (cookies.length == 1) {
				cookies(0).getValue
			} else {
				""
			}

			val tokens = userId.split('-')

			if (tokens.length != 3) return (-1, userId)

			val entropy = tokens(0)
			val user = tokens(1)

			val digest = hashdigest("SHA-256",
				serviceCtx.config.omegaup.salt + user + entropy)
			if (tokens(2) == digest) {
				try {
					(user.toInt, userId)
				} catch {
					case e: Exception => (-1, userId)
				}
			} else {
				log.info("Hash mismatch on the auth token")
				(-1, userId)
			}
		}

		private def getSession(sess: Session): BroadcasterSession = {
			log.debug("CONN {}", sess.getUpgradeRequest.getRequestURI.getPath)
			val contest = sess.getUpgradeRequest.getRequestURI.getPath match {
				case PathRE(contest) => {
					contest
				}
				case _ => {
					null
				}
			}
			if (contest == null) return null
			if (sess.getUpgradeRequest.getRequestURI.getQuery != null) {
				return getScoreboardSession(sess, contest)
			}
			val (userId, token) = getUserId(sess.getUpgradeRequest)
			if (userId == -1) return null
			try {
				val response = Https.post[ContestRoleResponse](
					serviceCtx.config.omegaup.role_url,
					Map("auth_token" -> token, "contest_alias" -> contest),
					runner = false
				)
				if (response.status == "ok") {
					new BroadcasterSession(userId, contest, response.admin, sess)
				} else {
					null
				}
			} catch {
				case e: Exception => {
					log.error(e, "Error getting role")
					null
				}
			}
		}

		override def onWebSocketText(message: String): Unit = {
			if (session == null || !session.isOpen) return
			log.debug("Received {}", message)
		}

		override def onWebSocketClose(statusCode: Int, reason: String): Unit = {
			log.info("Closed {} {}", statusCode, reason)
			unsubscribe(session)
			if (session == null || !session.isOpen) return
		}

		override def onWebSocketError(cause: Throwable): Unit = {
			log.info(cause, "Error")
			unsubscribe(session)
			if (session == null || !session.isOpen) return
		}
	}

	override def stop(): Unit = {
		log.info("Broadcaster stopping")
		server.stop
		queue.put(PoisonPill)
	}

	override def join(): Unit = {
		server.join
		broadcastThread.join
		log.info("Broadcaster stopped")
	}
}

object Service extends Object with Log with ContextMixin {
	override def start() = {
		val server = new Broadcaster

		Runtime.getRuntime.addShutdownHook(new Thread() {
			override def run() = {
				log.info("Shutting down")

				server.stop()
			}
		})

		server.join()
	}
}

/* vim: set noexpandtab: */
