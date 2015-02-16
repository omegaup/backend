package com.omegaup.grader

import java.text.ParseException
import com.omegaup._
import com.omegaup.data._
import com.omegaup.runner._
import Status._
import Verdict._
import Validator._

case class GraderOptions(
	configPath: String = "omegaup.conf"
)

class Grader(val options: GraderOptions) extends Object with GraderService with ServiceInterface with Log {
	type Listener = (RunContext, Run) => Unit
	private val listeners = scala.collection.mutable.ListBuffer.empty[Listener]
	val runnerDispatcher = new RunnerDispatcher

	// Loading SQL connector driver
	Class.forName(Config.get("db.driver", "org.h2.Driver"))
	implicit val conn = java.sql.DriverManager.getConnection(
		Config.get("db.url", "jdbc:h2:file:omegaup"),
		Config.get("db.user", "omegaup"),
		Config.get("db.password", "")
	)

	def start() = {
		updateConfiguration(false)

		recoverQueue

		info("omegaUp Grader service started")
	}

	def addListener(listener: Listener) = listeners += listener

	def removeListener(listener: Listener) = listeners -= listener

	def recoverQueue() = {
		val pendingRuns = GraderData.pendingRuns

		info("Recovering previous queue: {} runs re-added", pendingRuns.size)

		pendingRuns foreach(run => grade(new RunContext(Some(this), run, false, false)))
	}

	def grade(ctx: RunContext): Unit = {
		info("Judging {}", ctx.run.id)

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
				case Some(run) => grade(new RunContext(Some(this), run, message.debug, message.rejudge))
			}
		}
		RunGradeOutputMessage()
	}

	def updateVerdict(ctx: RunContext, run: Run): Run = {
		ctx.trace(EventCategory.UpdateVerdict) {
			GraderData.update(run)
		}
		if (run.status == Status.Ready) {
			info("Verdict update: {} {} {} {} {} {} {}",
				run.id, run.status, run.verdict, run.score, run.contest_score, run.runtime, run.memory)
			listeners foreach { listener => listener(ctx, run) }
		}

		run
	}

	def updateConfiguration(embeddedRunner: Boolean) = {
		if (Config.get("grader.embedded_runner.enable", false) && !embeddedRunner) {
			runnerDispatcher.addRunner(new com.omegaup.runner.Runner("#embedded-runner", Minijail))
		}
		val source = Config.get("grader.routing.table", "")
		try {
			runnerDispatcher.updateConfiguration(
				Config.get("grader.routing.table", source),
				Config.get("grader.routing.slow_threshold", 50)
			)
		} catch {
			case ex: ParseException => {
				error("Unable to parse {} at character {}", source, ex.getErrorOffset)
			}
		}
		Config.get("grader.routing.registered_runners", "").split("\\s+").foreach({ endpoint => {
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
		info("omegaUp grader stopping")
		runnerDispatcher.stop
	}

	override def join(): Unit = {
		runnerDispatcher.join
		conn.close
		info("omegaUp grader stopped")
	}
}

/* vim: set noexpandtab: */
