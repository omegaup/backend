package com.omegaup.grader

import java.io._
import java.util._
import java.util.regex.Pattern
import scala.collection.Iterator
import scala.collection.mutable
import scala.collection.immutable.Map
import com.omegaup._
import com.omegaup.data._
import com.omegaup.data.OmegaUpProtocol._
import Verdict._
import PenaltyType._

trait OutputValidator extends Object with Log with Using {
	def validateRun(run: Run)(implicit ctx: RunContext): Run = {
		val alias = run.problem.alias
		val gradeDirectory = new File(ctx.config.common.roots.grade,
			run.guid.substring(0, 2) + "/" + run.guid.substring(2))

		log.info("Validating {} {} with {}", alias, run.id, run.problem.validator)

		run.status = Status.Ready
		run.verdict = Verdict.Accepted
		run.runtime = 0
		run.memory = 0

		val resultsDirectory = new File(gradeDirectory, "results")
		val metas = resultsDirectory.listFiles
			.filter { _.getName.endsWith(".meta") }
			.map{ f => f.getName.substring(0, f.getName.length - 5)->(f, MetaFile.load(f.getCanonicalPath)) }
			.toMap

		val weightsFile = new File(ctx.config.common.roots.problems, alias + "/testplan")

		log.trace("Finding Weights file in {}", weightsFile.getCanonicalPath)

		val weights:scala.collection.Map[String,scala.collection.Map[String,Double]] = if (weightsFile.exists) {
			val weights = new mutable.ListMap[String,mutable.ListMap[String,Double]]
			val fileReader = new BufferedReader(new FileReader(weightsFile))
			var line: String = null

			while( { line = fileReader.readLine(); line != null} ) {
				val tokens = line.split("\\s+")

				if(tokens.length == 2 && !tokens(0).startsWith("#")) {
					val idx = tokens(0).indexOf(".")

					val group = if (idx != -1) {
						tokens(0).substring(0, idx)
					} else {
						tokens(0)
					}

					if (!weights.contains(group)) {
						weights += (group -> new mutable.ListMap[String,Double])
					}

					weights(group) += (tokens(0) -> tokens(1).toDouble)
				}
			}

			fileReader.close()

			weights
		} else {
			val weights = new mutable.ListMap[String,mutable.ListMap[String,Double]]

			val inputs = new File(ctx.config.common.roots.problems, alias + "/cases/in/")
				.listFiles
				.filter { _.getName.endsWith(".in") }

			for (f <- inputs) {
				val caseName = f.getName.substring(0, f.getName.length - 3)

				val idx = caseName.indexOf(".")
				val group = if (idx != -1) {
					caseName.substring(0, idx)
				} else {
					caseName
				}

				if (!weights.contains(group)) {
					weights += (group -> new mutable.ListMap[String,Double])
				}

				weights(group) += (caseName -> 1.0)
			}

			weights
		}

		metas.values.foreach { case (f, meta) => {
			if (meta.contains("overall-wall-time-exceeded") &&
			    !run.problem.overall_wall_time_limit.isEmpty) {
				run.runtime = run.problem.overall_wall_time_limit.get
			}
			if (meta.contains("time")) {
				run.runtime += math.round(1000 * meta("time").toDouble)
			}
			if (meta.contains("mem")) {
				run.memory = math.max(run.memory, meta("mem").toLong)
			}
			val v = meta("status") match {
				case "XX" => Verdict.JudgeError
				case "OK" => Verdict.Accepted
				case "RE" => Verdict.RuntimeError
				case "TO" => Verdict.TimeLimitExceeded
				case "ML" => Verdict.MemoryLimitExceeded
				case "OL" => Verdict.OutputLimitExceeded
				case "FO" => Verdict.RestrictedFunctionError
				case "FA" => Verdict.RestrictedFunctionError
				case "SG" => Verdict.RuntimeError
				case _    => Verdict.JudgeError
			}

			if (run.language == Language.Cpp && meta("status") == "SG") {
				val errFile = new File(f.getCanonicalPath.replace(".meta", ".err"))
				if (errFile.exists && FileUtil.read(errFile.getCanonicalPath).contains("std::bad_alloc")) {
					if (run.verdict < Verdict.MemoryLimitExceeded) run.verdict = Verdict.MemoryLimitExceeded
				} else if(run.verdict < v) run.verdict = v
			} else if (run.language == Language.Java) {
				val errFile = new File(f.getCanonicalPath.replace(".meta", ".err"))
				if (errFile.exists && FileUtil.read(errFile.getCanonicalPath).contains("java.lang.OutOfMemoryError")) {
					if (run.verdict < Verdict.MemoryLimitExceeded) run.verdict = Verdict.MemoryLimitExceeded
				} else if(run.verdict < v) run.verdict = v
			} else if(run.verdict < v) run.verdict = v
		}}

		if (!run.problem.overall_wall_time_limit.isEmpty &&
		    run.runtime > run.problem.overall_wall_time_limit.get) {
			run.runtime = run.problem.overall_wall_time_limit.get
		}

		val caseScores = weights.map { case (group, data) => {
			val scores = data.map { case (name, weight) => {
				var verdict = if (metas.contains(name)) {
					metas(name)._2("status")
				} else {
					"OK"
				}

				val score = if (metas.contains(name) && metas(name)._2("status") == "OK") {
					val f = metas(name)._1

					val rawScore = validateCase(
						run,
						name,
						new File(f.getCanonicalPath.replace(".meta", ".out")),
						new File(ctx.config.common.roots.problems,
							alias + "/cases/out/" + f.getName.replace(".meta", ".out")),
						metas(name)._2
					)

					verdict = if (rawScore == 1.0) {
						"AC"
					} else if (rawScore > 0) {
						"PA"
					} else {
						"WA"
					}

					rawScore
				} else {
					0.0
				}

				new CaseVerdictMessage(
					name,
					verdict,
					score * weight,
					if (metas.contains(name)) {
						filterMeta(metas(name)._2)
					} else {
						Map.empty[String, String]
					}
				)
			}}

			new GroupVerdictMessage(
				group,
				scores.toList,
				if (scores.forall(caseVerdict => caseVerdict.verdict == "AC" || caseVerdict.verdict == "PA")) {
					scores.foldLeft(0.0)(_+_.score)
				} else {
					0.0
				}
			)
		}}

		val details = new File(gradeDirectory, "details.json")
		log.debug("Writing details into {}.", details.getCanonicalPath)
		Serialization.write(caseScores, new FileWriter(details))

		val normalizedWeights = weights.foldLeft(0.0)(_+_._2.foldLeft(0.0)(_+_._2))
		run.score = caseScores.foldLeft(0.0)(_+_.score) / normalizedWeights * (run.contest match {
			case None => 1.0
			case Some(contest) => {
				if (contest.points_decay_factor <= 0.0 || run.submit_delay == 0.0) {
					1.0
				} else {
					var TT = (contest.finish_time.getTime() - contest.start_time.getTime()) / 60000.0
					var PT = run.submit_delay / 60.0

					if (contest.points_decay_factor >= 1.0) {
						contest.points_decay_factor = 1.0
					}

					(1 - contest.points_decay_factor) + contest.points_decay_factor * TT*TT / (10 * PT*PT + TT*TT)
				}
			}
		})

		run.score = scala.math.round(run.score * 1024 * 1024) / (1024.0 * 1024.0)
		if(run.score == 0 && run.verdict < Verdict.WrongAnswer)
			run.verdict = Verdict.WrongAnswer
		else if(run.score < (1-1e-9) && run.verdict < Verdict.PartialAccepted)
			run.verdict = Verdict.PartialAccepted

		if (run.verdict == Verdict.JudgeError) {
			run.score = 0
			run.memory = 0
			run.runtime = 0
		}

		run.contest_score = run.problem.points match {
			case None => None
			case Some(factor) => Some(run.score * factor)
		}
		run.penalty = (if (!run.contest.isEmpty &&
		                   run.contest.get.penalty_type == PenaltyType.Runtime) {
			run.runtime
		} else {
			run.submit_delay
		})

		run
	}

	def validateCase(run: Run, caseName: String, runOut: File, problemOut: File,
		meta: scala.collection.Map[String,String])(implicit ctx: Context):
	Double

	private def filterMeta(meta: scala.collection.Map[String, String]):
			scala.collection.immutable.Map[String, String] = {
		meta.filter(_._1 != "status").toMap
	}
}

object CustomValidator extends OutputValidator {
	override def validateCase(run: Run, caseName: String, runOut: File,
		problemOut: File, meta: scala.collection.Map[String,String])
		(implicit ctx: Context):
	Double = {
		if (meta.contains("score")) {
			meta("score").toDouble
		} else {
			0.0
		}
	}
}

object LiteralValidator extends OutputValidator {
	override def validateCase(run: Run, caseName: String, runOut: File,
		problemOut: File, meta: scala.collection.Map[String,String])
		(implicit ctx: Context):
	Double = {
		val score = (if (runOut.exists) {
			FileUtil.read(runOut).trim
		} else {
			""
		})
		if (score != "") {
			Math.max(0.0, Math.min(1.0, score.toDouble))
		} else {
			0.0
		}
	}
}

class Token(var nextChar: Int, reader: Reader,
		containedInTokenClass: (Char) => Boolean) extends Iterator[Char] {
	def hasNext(): Boolean = {
		return !eof && containedInTokenClass(nextChar.asInstanceOf[Char])
	}

	def eof(): Boolean = nextChar == -1

	def next(): Char = {
		val result = nextChar
		nextChar = reader.read
		return result.asInstanceOf[Char]
	}

	override def toString(): String = {
		val sb = new StringBuilder
		while (hasNext) {
			sb.append(next)
		}
		return sb.toString
	}

	def toDouble(): Double = {
		var ans: Double = 0
		var before: Boolean = true
		var negative: Boolean = false
		var p: Double = 1

		while (hasNext) {
			val ch = next

			if (ch == '-') {
				negative = true
			} else if (ch == '.') {
				before = false
			} else if (before) {
				ans *= 10
				ans += ch.asDigit
			} else {
				p /= 10
				ans += p * ch.asDigit
			}
		}

		if (negative) ans *= -1

		return ans
	}
}

trait TokenValidator {
	def equal(a: Token, b: Token): Boolean
}

class NumericTokenValidator(precision: Double) extends TokenValidator {
	def equal(a: Token, b: Token): Boolean = {
		val da = a.toDouble
		val db = b.toDouble

		return math.abs(da - db) <= math.abs(precision * math.max(1, da))
	}
}

class CharTokenValidator(equals: (Char, Char) => Boolean) extends TokenValidator {
	def equal(a: Token, b: Token): Boolean = {
		while (a.hasNext && b.hasNext) {
			if (!equals(a.next, b.next)) {
				return false
			}
		}

		return !a.hasNext && !b.hasNext
	}
}

class Tokenizer(file: File, containedInTokenClass: (Char) => Boolean)
		extends Iterator[Token] {
	val reader = new BufferedReader(new FileReader(file))
	var eof: Boolean = false
	var nextToken: Token = null

	def hasNext(): Boolean = {
		if (nextToken != null) return true
		if (eof) return false

		var char: Int = 0
		while ({char = reader.read; char != -1}) {
			if (containedInTokenClass(char.asInstanceOf[Char])) {
				nextToken = new Token(char, reader, containedInTokenClass)
				eof = nextToken.eof
				return true
			}
		}

		eof = true
		return false
	}

	def next(): Token = {
		val result = nextToken
		nextToken = null
		return result
	}

	def close(): Unit = reader.close
	def path(): String = file.getCanonicalPath
}

trait TokenizerValidator extends Object with Log {
	def validateCase(run: Run, caseName: String, inA: Tokenizer, inB: Tokenizer,
		tc: TokenValidator)(implicit ctx: Context):
	Double = {
		log.debug("Validating {}, case {}", run, caseName)

		try {
			var points:Double = 1

			while (points > 0 && inA.hasNext && inB.hasNext) {
				if (!tc.equal(inA.next, inB.next)) {
					log.debug("Token mismatch {} {} {}", caseName, inA.path, inB.path)
					points = 0
				}
			}

			if (inA.hasNext || inB.hasNext) {
				log.debug("Unfinished input {} {} {}", caseName, inA.path, inB.path)
				points = 0
			}

			log.debug("Validating {}, case {}. Reporting {} points", run, caseName, points)
			points
		} catch {
			case e: Exception => {
				log.error(e, "Error grading")

				0
			}
		} finally {
			log.debug("Finished grading {}, case {}", run, caseName)
			inA.close
			inB.close
		}
	}
}

object TokenValidator extends OutputValidator with TokenizerValidator {
	def validateCase(run: Run, caseName: String, runOut: File, problemOut: File,
		meta: scala.collection.Map[String,String])(implicit ctx: Context):
	Double = {
		val charClass = (c: Char) => !c.isWhitespace
		validateCase(
			run,
			caseName,
			new Tokenizer(runOut, charClass),
			new Tokenizer(problemOut, charClass),
			new CharTokenValidator(_ == _))
	}
}

object TokenCaselessValidator extends OutputValidator with TokenizerValidator {
	def validateCase(run: Run, caseName: String, runOut: File, problemOut: File,
		meta: scala.collection.Map[String,String])(implicit ctx: Context):
	Double = {
		val charClass = (c: Char) => !c.isWhitespace
		validateCase(
			run,
			caseName,
			new Tokenizer(runOut, charClass),
			new Tokenizer(problemOut, charClass),
			new CharTokenValidator(_.toLower == _.toLower))
	}
}

object TokenNumericValidator extends OutputValidator with TokenizerValidator {
	def validateCase(run: Run, caseName: String, runOut: File, problemOut: File,
		meta: scala.collection.Map[String,String])(implicit ctx: Context):
	Double = {
		val charClass = (c: Char) => c.isDigit || c == '.' || c == '-'
		validateCase(
			run,
			caseName,
			new Tokenizer(runOut, charClass),
			new Tokenizer(problemOut, charClass),
			new NumericTokenValidator(if (run != null) run.problem.tolerance else 1e-6))
	}
}

/* vim: set noexpandtab: */
