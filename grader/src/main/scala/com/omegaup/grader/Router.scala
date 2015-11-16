package com.omegaup.grader

import java.util.concurrent._
import java.text.ParseException
import com.omegaup._
import com.omegaup.data._
import com.omegaup.grader.drivers._
import com.omegaup.runner._
import scala.util.parsing.combinator.syntactical._
import scala.collection.immutable.{HashMap, HashSet}

trait RunRouter {
	def apply(ctx: RunContext): Int
}

class RunnerEndpoint(val hostname: String, val port: Int) {
	def ==(o: RunnerEndpoint) = hostname == o.hostname && port == o.port
	override def hashCode() = 28227 + 97 * hostname.hashCode + port
	override def equals(other: Any) = other match {
		case x:RunnerEndpoint => hostname == x.hostname && port == x.port
		case _ => false
	}
	override def toString() = "%s:%d".format(hostname, port)
}

object RoutingDescription extends StandardTokenParsers with Log {
	lexical.delimiters ++= List("(", ")", "[", "]", "{", "}", ",", ":", ";", "==", "!=", "||", "&&", "!")
	lexical.reserved += ("not", "in", "user", "slow", "problem", "true", "urgent", "contest", "practice", "rejudge")

	def parse(input: String)(implicit ctx: Context): RunRouter = {
		log.info("Parsing routing table: {}", input)
		routingTable(new lexical.Scanner(input)) match {
			case Success(rules, _) => {
				log.info("Routing rule parsed: {}", rules)
				new RunRouterImpl(rules)
			}
			case NoSuccess(msg, err) => {
				log.error(err.pos.longString)
				throw new ParseException(msg, err.offset)
			}
		}
	}

	private def routingTable = phrase(repsep(routingRule, ";"))
	private def routingRule = queueName ~ ":" ~ expr ^^ { case queueId ~ ":" ~ condition => condition -> queueId }
	private def queueName: Parser[Int] = ("urgent" ^^ { case "urgent" => 0 }) | ("contest" ^^ { case "contest" => 2 }) |
			("practice" ^^ { case "practice" => 4 }) | ("rejudge" ^^ { case "rejudge" => 6 })
	private def expr: Parser[RunMatcher] = ( "(" ~> expr <~ ")" ) | orExpr
	private def orExpr: Parser[RunMatcher] = rep1sep(andExpr, "||") ^^ { (andList: List[RunMatcher]) =>
		andList.length match {
			case 1 => andList(0)
			case _ => new OrMatcher(andList)
		}
	}
	private def andExpr: Parser[RunMatcher] = rep1sep(opExpr, "&&") ^^ { (opList: List[RunMatcher]) =>
		opList.length match {
			case 1 => opList(0)
			case _ => new AndMatcher(opList)
		}
	}
	private def opExpr: Parser[RunMatcher] = eqExpr | neqExpr | inExpr | notInExpr | slowExpr | rejudgeExpr | trueExpr | notExpr
	private def notExpr: Parser[RunMatcher] = "!" ~ opExpr ^^ { case "!" ~ cond => new NotMatcher(cond) }
	private def neqExpr: Parser[RunMatcher] = param ~ "!=" ~ stringLit ^^ { case param ~ "!=" ~ arg => new NeqMatcher(param, arg) }
	private def eqExpr: Parser[RunMatcher] = param ~ "==" ~ stringLit ^^ { case param ~ "==" ~ arg => new EqMatcher(param, arg) }
	private def inExpr: Parser[RunMatcher] = param ~ "in" ~ stringList ^^ { case param ~ "in" ~ arg => new InMatcher(param, arg) }
	private def notInExpr: Parser[RunMatcher] = param ~ "not" ~ "in" ~ stringList ^^ {
		case param ~ "not" ~ "in" ~ arg => new NotMatcher(new InMatcher(param, arg))
	}
	private def slowExpr: Parser[RunMatcher] = "slow" ^^ { case "slow" => SlowMatcher }
	private def rejudgeExpr: Parser[RunMatcher] = "rejudge" ^^ { case "rejudge" => RejudgeMatcher }
	private def trueExpr: Parser[RunMatcher] = "true" ^^ { case "true" => TrueMatcher }

	private def param: Parser[String] = "contest" | "user" | "problem"
	private def stringList: Parser[List[String]] = "[" ~> rep1sep(stringLit, ",") <~ "]"

	private class RunRouterImpl(routingMap: Iterable[(RunMatcher, Int)]) extends Object with RunRouter with Log {
		def apply(ctx: RunContext): Int = {
			implicit val loggingCtx = ctx
			val slow = if (ctx.run.problem.slow) 1 else 0
			for (entry <- routingMap) {
				log.debug("Run {} matching against {}", ctx.run, entry._1)
				if (entry._1(ctx)) {
					log.debug("Run {} matched. Priority {}", ctx.run.id, entry._2 + slow)
					return entry._2 + slow
				}
			}
			val queue = if (ctx.rejudge) {
				6
			} else if (ctx.run.contest.isEmpty) {
				4
			} else if (ctx.run.contest.get.urgent) {
				0
			} else {
				2
			}
			log.debug("Run {} matched nothing. Default priority of {}", ctx.run.id, queue + slow)
			queue + slow
		}

		override def toString(): String = "RunRouter(" + routingMap.mkString(", ") + ")"
	}

	private trait RunMatcher {
		def apply(ctx: RunContext): Boolean
		def getParam(ctx: RunContext, param: String) = param match {
			case "contest" => {
				ctx.run.contest match {
					case None => ""
					case Some(contest) => contest.alias
				}
			}
			case "problem" => ctx.run.problem.alias
			case "user" => ctx.run.user match {
				case None => ""
				case Some(user) => user.username
			}
		}
	}

	private class NeqMatcher(param: String, arg: String) extends Object with RunMatcher {
		def apply(ctx: RunContext): Boolean = getParam(ctx, param) != arg
		override def toString(): String = param + " != " + arg
	}

	private class EqMatcher(param: String, arg: String) extends Object with RunMatcher {
		def apply(ctx: RunContext): Boolean = getParam(ctx, param) == arg
		override def toString(): String = param + " == " + arg
	}

	private class InMatcher(param: String, arg: List[String]) extends Object with RunMatcher {
		private val set = new HashSet[String]() ++ arg
		def apply(ctx: RunContext): Boolean = set.contains(getParam(ctx, param))
		override def toString(): String = param + " in " + "[" + set.mkString(", ") + "]"
	}

	private class NotMatcher(expr: RunMatcher) extends Object with RunMatcher {
		def apply(ctx: RunContext): Boolean = !expr(ctx)
		override def toString(): String = "! " + expr
	}

	private class OrMatcher(arg: List[RunMatcher]) extends Object with RunMatcher {
		def apply(ctx: RunContext): Boolean = arg.exists(_(ctx))
		override def toString(): String = "(" + arg.mkString(" || ") + ")"
	}

	private class AndMatcher(arg: List[RunMatcher]) extends Object with RunMatcher {
		def apply(ctx: RunContext): Boolean = arg.forall(_(ctx))
		override def toString(): String = arg.mkString(" && ")
	}

	private object SlowMatcher extends Object with RunMatcher {
		def apply(ctx: RunContext): Boolean = ctx.run.problem.slow
		override def toString(): String = "slow"
	}

	private object RejudgeMatcher extends Object with RunMatcher {
		def apply(ctx: RunContext): Boolean = ctx.rejudge
		override def toString(): String = "rejudge"
	}

	private object TrueMatcher extends Object with RunMatcher {
		def apply(ctx: RunContext): Boolean = true
		override def toString(): String = "true"
	}
}

class RunnerDispatcher(implicit var serviceCtx: Context)
		extends RunnerDispatcherInterface with Log {
	private val registeredEndpoints = scala.collection.mutable.HashMap.empty[RunnerEndpoint, Long]
	private val runnerQueue = scala.collection.mutable.Queue.empty[RunnerService]
	private val runQueue = new Array[scala.collection.mutable.Queue[RunContext]](8)
	private val runsInFlight = scala.collection.mutable.HashMap.empty[Long, RunContext]
	private val graderExecutor = Executors.newCachedThreadPool
	private val validatorExecutor = Executors.newFixedThreadPool(1)
	private var flightIndex: Long = 0
	private var runRouter = RoutingDescription.parse("")
	private var slowThreshold: Int = 50
	private var slowRuns: Int = 0
	private val lock = new Object

	private val pruner = new java.util.Timer("Flight pruner", true)

	override def start() = {
		pruner.scheduleAtFixedRate(
			new java.util.TimerTask() {
				override def run(): Unit = pruneFlights
			},
			serviceCtx.config.grader.flight_pruner_interval * 1000,
			serviceCtx.config.grader.flight_pruner_interval * 1000
		)

		for (i <- 0 until runQueue.length) {
			runQueue(i) = scala.collection.mutable.Queue.empty[RunContext]
		}
	}

	override def status(): QueueStatus = lock.synchronized {
		QueueStatus(
			run_queue_length = runQueue.foldLeft(0)(_+_.size),
			runner_queue_length = runnerQueue.size,
			runners = registeredEndpoints.keys.map(_.hostname).toList,
			running = runsInFlight.values.map(ifr => new Running(ifr.service.name, ifr.run.id.toInt)).toList
		)
	}

	override def updateContext(newCtx: Context) = lock.synchronized {
		val description = newCtx.config.grader.routing.table
		val slowThreshold = newCtx.config.grader.routing.slow_threshold

		try {
			runRouter = RoutingDescription.parse(description)
			this.slowThreshold = slowThreshold
			serviceCtx = newCtx

			val runs = scala.collection.mutable.MutableList.empty[RunContext]
			for (i <- 0 until runQueue.length) {
				while (!runQueue(i).isEmpty) runs += runQueue(i).dequeue
			}
			runs.sortBy(_.creationTime)

			runs.foreach { ctx => {
				runQueue(runRouter(ctx)) += ctx
				dispatchLocked
			}}
		} catch {
			case ex: ParseException => {
				log.error("Unable to parse {} at character {}", description,
					ex.getErrorOffset)
			}
		}

	}

	override def register(hostname: String, port: Int): EndpointRegisterOutputMessage = {
		val endpoint = new RunnerEndpoint(hostname, port)

		lock.synchronized {
			if (!registeredEndpoints.contains(endpoint)) {
				val proxy = new RunnerProxy(endpoint.hostname, endpoint.port) 
				log.info("Registering {}", proxy)
				registeredEndpoints += endpoint -> System.currentTimeMillis
				addRunner(proxy)
			}
			registeredEndpoints(endpoint) = System.currentTimeMillis
		}

		log.debug("Runner queue register length {} known endpoints {}", runnerQueue.size, registeredEndpoints.size)

		new EndpointRegisterOutputMessage()
	}

	private def deregisterLocked(endpoint: RunnerEndpoint) = {
		if (registeredEndpoints.contains(endpoint)) {
			log.info("De-registering {}", endpoint)
			registeredEndpoints -= endpoint
		}
	}

	override def deregister(hostname: String, port: Int): EndpointRegisterOutputMessage = {
		val endpoint = new RunnerEndpoint(hostname, port)

		lock.synchronized {
			deregisterLocked(endpoint)
		}

		log.debug("Runner queue deregister length {} known endpoints {}", runnerQueue.size, registeredEndpoints.size)

		new EndpointRegisterOutputMessage()
	}

	override def addRun(ctx: RunContext): Unit = {
		ctx.queued()
		lock.synchronized {
			log.debug("Adding run {}", ctx)
			runQueue(runRouter(ctx)) += ctx
			dispatchLocked
		}
	}

	override def addRunner(runner: RunnerService) = {
		lock.synchronized {
			addRunnerLocked(runner)
		}
	}

	private class GradeTask(
		flightIndex: Long,
		driver: Driver
	)(implicit ctx: RunContext) extends Runnable {
		override def run(): Unit = {
			try {
				gradeTask
			} catch {
				case e: Exception => {
					log.error("Error while running {}: {}", ctx.run.id, e)
				}
			} finally {
				validatorExecutor.submit(new ValidateTask(driver))
			}
		}

		private def gradeTask() = {
			val future = graderExecutor.submit(new Callable[Run]() {
					override def call(): Run = ctx.trace(EventCategory.Runner, "runner" -> ctx.service.name) {
						driver.run(ctx.run.copy)
					}
			})

			ctx.run = try {
				future.get(serviceCtx.config.grader.runner_timeout, TimeUnit.SECONDS)
			} catch {
				case e: ExecutionException => {
					log.error("Submission {} {} failed - {} {}",
						ctx.run.problem.alias,
						ctx.run.id,
						e.getCause.toString,
						e.getCause.getStackTrace
					)

					e.getCause match {
						case inner: java.net.SocketException => {
							// Probably a network error of some sort. No use in re-queueing the runner.
							ctx.service match {
								case proxy: com.omegaup.runner.RunnerProxy => deregister(proxy.hostname, proxy.port)
								case _ => {}
							}

							// But do re-queue the run
							addRun(ctx)

							// And commit suicide
							throw e
						}
						case _ => {
							ctx.run.score = 0
							ctx.run.contest_score = ctx.run.contest match {
								case None => None
								case Some(x) => Some(0)
							}
							ctx.run.status = Status.Ready
							ctx.run.verdict = Verdict.JudgeError
						}
					}

					ctx.run
				}
				case e: TimeoutException => {
					log.error("Submission {} {} timed out - {} {}",
						ctx.run.problem.alias,
						ctx.run.id,
						e.toString,
						e.getStackTrace)

					// Probably a network error of some sort. No use in re-queueing the runner.
					ctx.service match {
						case proxy: com.omegaup.runner.RunnerProxy => deregister(proxy.hostname, proxy.port)
						case _ => {}
					}

					ctx.run
				}
			} finally {
				flightFinished(flightIndex)
			}
		}
	}

	private class ValidateTask(
		driver: Driver
	)(implicit ctx: RunContext) extends Runnable {
		override def run(): Unit = {
			try {
				validateTask
			} catch {
				case e: Exception => {
					log.error("Error while validating {}: {}", ctx.run.id, e)
				}
			} finally {
				ctx.updateVerdict(ctx.run)
			}
		}

		private def validateTask() = {
			if (ctx.run.status != Status.Ready) {
				ctx.run = try {
					try {
						driver.validateOutput(ctx.run.copy)
					} finally {
						driver.cleanResults(ctx.run)
					}
				} catch {
					case e: Exception => {
						log.error(e, "Error while validating")
						ctx.run.score = 0
						ctx.run.contest_score = ctx.run.contest match {
							case None => None
							case Some(x) => Some(0)
						}
						ctx.run.status = Status.Ready
						ctx.run.verdict = Verdict.JudgeError

						ctx.run
					}
				}
			}

			if (ctx.debug) {
				driver.setLogs(ctx.run, ctx.overrideLogger.toString)
			}
		}
	}

	private def pruneFlights() = lock.synchronized {
		var cutoffTime = System.currentTimeMillis -
			serviceCtx.config.grader.runner_timeout * 1000
		var pruned = false
		runsInFlight.foreach { case (i, ctx) => {
			if (ctx.flightTime < cutoffTime) {
				log.warn("Expiring stale flight {}, run {}", ctx.service, ctx.run.id)
				ctx.service match {
					case proxy: RunnerProxy => deregisterLocked(new RunnerEndpoint(proxy.hostname, proxy.port))
					case _ => {}
				}
				runsInFlight -= i
				if (ctx.run.problem.slow) {
					slowRuns -= 1
				}
				pruned = true
			}
		}}
		if (pruned) {
			dispatchLocked
		}
	}

	private def flightFinished(flightIndex: Long) = lock.synchronized {
		if (runsInFlight.contains(flightIndex)) {
			var ctx = runsInFlight(flightIndex)
			runsInFlight -= flightIndex
			if (ctx.run.problem.slow) {
				slowRuns -= 1
			}
			addRunnerLocked(ctx.service)
		} else {
			log.error("Lost track of flight {}!", flightIndex)
			throw new RuntimeException("Flight corrupted, bail out")
		}
	}

	private def addRunnerLocked(runner: RunnerService) = {
		log.debug("Adding runner {}", runner)
		if (!runnerQueue.contains(runner))
			runnerQueue += runner
		runner match {
			case proxy: com.omegaup.runner.RunnerProxy => {
				val endpoint = new RunnerEndpoint(proxy.hostname, proxy.port)
				registeredEndpoints(endpoint) = System.currentTimeMillis
			}
			case _ => {}
		}
		dispatchLocked
	}

	private def dispatchLocked(): Unit = {
		// Prune any runners that are not registered or haven't communicated in a while.
		log.debug("Before pruning the queue {}", status)
		var cutoffTime = System.currentTimeMillis -
			serviceCtx.config.grader.runner_queue_timeout * 1000
		runnerQueue.dequeueAll (
			_ match {
				case proxy: com.omegaup.runner.RunnerProxy => {
					val endpoint = new RunnerEndpoint(proxy.hostname, proxy.port)
					// Also expire stale endpoints.
					if (registeredEndpoints.contains(endpoint) &&
							registeredEndpoints(endpoint) < cutoffTime) {
						log.warn("Stale endpoint {}", proxy)
						deregister(endpoint.hostname, endpoint.port)
					}
					!registeredEndpoints.contains(endpoint)
				}
				case _ => false
			}
		)

		log.debug("After pruning the queue {}", status)

		while (!runnerQueue.isEmpty) {
			log.debug("But there's enough to run something!")
			val queue = selectRunQueueLocked
			if (queue.isEmpty) {
				log.debug("But there is nothing to run it on")
				return
			}
			runLocked(runnerQueue.dequeue, queue.get.dequeue)
		}
	}

	private def selectRunQueueLocked(): Option[scala.collection.mutable.Queue[RunContext]] = {
		val canScheduleSlowRun = slowRuns == 0 || 100 * slowRuns < slowThreshold * registeredEndpoints.size
		for (i <- 0 until runQueue.length) {
			if (!runQueue(i).isEmpty && (i % 2 == 0 || canScheduleSlowRun)) {
				return Some(runQueue(i))
			}
		}
		return None
	}

	private def runLocked(runner: RunnerService, ctx: RunContext) = {
		ctx.dequeued(runner.name)

		ctx.startFlight(runner)
		runsInFlight += flightIndex -> ctx
		if (ctx.run.problem.slow) {
			slowRuns += 1
		}
		graderExecutor.submit(new GradeTask(flightIndex, OmegaUpDriver)(ctx))

		flightIndex += 1
	}

	override def stop(): Unit = {
		graderExecutor.shutdown
		validatorExecutor.shutdown
	}

	override def join(): Unit = {
		graderExecutor.awaitTermination(Long.MaxValue, TimeUnit.NANOSECONDS)
		validatorExecutor.awaitTermination(Long.MaxValue, TimeUnit.NANOSECONDS)
	}
}

/* vim: set noexpandtab: */
