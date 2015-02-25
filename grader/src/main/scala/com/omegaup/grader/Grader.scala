package com.omegaup.grader

import java.text.ParseException
import com.omegaup._
import com.omegaup.data._
import com.omegaup.runner._
import Status._
import Verdict._
import Validator._

class Grader(implicit var serviceCtx: Context)
extends Object with GraderService with ServiceInterface with Log {
	type Listener = (RunContext, Run) => Unit
	private val listeners = scala.collection.mutable.ListBuffer.empty[Listener]
	val runnerDispatcher = new RunnerDispatcher
	private var embeddedRunner: Boolean = false

	// Loading SQL connector driver
	Class.forName(serviceCtx.config.db.driver)
	implicit val conn = java.sql.DriverManager.getConnection(
		serviceCtx.config.db.url,
		serviceCtx.config.db.user,
		serviceCtx.config.db.password
	)

	override def start() = {
		runnerDispatcher.start
		updateContext(serviceCtx)
		recoverQueue
		log.info("omegaUp Grader service started")
	}

	def addListener(listener: Listener) = listeners += listener

	def removeListener(listener: Listener) = listeners -= listener

	def recoverQueue() = {
		val pendingRuns = GraderData.pendingRuns

		log.info("Recovering previous queue: {} runs re-added", pendingRuns.size)

		pendingRuns foreach(run => grade(new RunContext(serviceCtx, Some(this),
			run, false, false)))
	}

	def grade(ctx: RunContext): Unit = {
		log.info("Judging {}", ctx.run.id)

		if (ctx.run.status != Status.Waiting) {
			ctx.run.status = Status.Waiting
			ctx.run.verdict = Verdict.JudgeError
			ctx.run.judged_by = None
			ctx.trace(EventCategory.UpdateVerdict) {
				GraderData.update(ctx.run)
			}
		}

		runnerDispatcher.addRun(ctx)
	}

	def grade(message: RunGradeInputMessage): RunGradeOutputMessage = {
		for (id <- message.id) {
			GraderData.getRun(id) match {
				case None => throw new IllegalArgumentException("Id " + id + " not found")
				case Some(run) => {
					val overrideLogger: Option[OverrideLogger] = message.debug match {
						case false => None
						case true => {
							val logger = new OverrideLogger("debug")
							logger.start
							Some(logger)
						}
					}
					grade(new RunContext(serviceCtx, Some(this), run, message.debug,
						message.rejudge, overrideLogger))
				}
			}
		}
		RunGradeOutputMessage()
	}

	def updateVerdict(run: Run)(implicit ctx: RunContext): Run = {
		ctx.trace(EventCategory.UpdateVerdict) {
			GraderData.update(run)
		}
		if (run.status == Status.Ready) {
			log.info("Verdict update: {} {} {} {} {} {} {}",
				run.id, run.status, run.verdict, run.score, run.contest_score, run.runtime, run.memory)
			listeners foreach { listener => listener(ctx, run) }
		}

		run
	}

	override def updateContext(newCtx: Context) = {
		serviceCtx = newCtx

		this.synchronized {
			if (serviceCtx.config.grader.embedded_runner_enabled && !embeddedRunner) {
				runnerDispatcher.addRunner(
					new com.omegaup.runner.Runner(
						"#embedded-runner",
						serviceCtx.config.runner.sandbox match {
							case "null" => NullSandbox
							case _ => Minijail
						}
					)
				)
			}
			embeddedRunner = serviceCtx.config.grader.embedded_runner_enabled
		}

		runnerDispatcher.updateContext(newCtx)
		serviceCtx.config.grader.routing.registered_runners.foreach({ endpoint => {
			val tokens = endpoint.split(":")
			if (tokens.length > 0 && tokens(0).trim.length > 0) {
				if (tokens.length == 1) {
					runnerDispatcher.register(tokens(0), 21681)
				} else {
					runnerDispatcher.register(tokens(0), tokens(1).toInt)
				}
			}
		}})
	}

	override def stop(): Unit = {
		log.info("omegaUp grader stopping")
		runnerDispatcher.stop
	}

	override def join(): Unit = {
		runnerDispatcher.join
		conn.close
		log.info("omegaUp grader stopped")
	}
}

/* vim: set noexpandtab: */
