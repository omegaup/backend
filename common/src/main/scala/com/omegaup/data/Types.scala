package com.omegaup.data

import java.sql._

object Validator extends Enumeration {
	type Validator = Value
	val  Token = Value(1, "token")
	val  TokenCaseless = Value(2, "token-caseless")
	val  TokenNumeric = Value(3, "token-numeric")
	val  TokenAbsoluteNumeric = Value(4, "token-absolute-numeric")
	val  Custom = Value(5, "custom")
	val  Literal = Value(6, "literal")
}

object Language extends Enumeration {
	type Language = Value
	val  C = Value(1, "c")
	val  Cpp = Value(2, "cpp")
	val  Java = Value(3, "java")
	val  Python = Value(4, "py")
	val  Ruby = Value(5, "rb")
	val  Perl = Value(6, "pl")
	val  CSharp = Value(7, "cs")
	val  Pascal = Value(8, "pas")
	val  KarelPascal = Value(9, "kp")
	val  KarelJava = Value(10, "kj")
	val  Literal = Value(11, "cat")
	val  Haskell = Value(12, "hs")
	val  Cpp11 = Value(13, "cpp11")
}

object Status extends Enumeration {
	type Status = Value
	val  New = Value(1, "new")
	val  Waiting = Value(2, "waiting")
	val  Compiling = Value(3, "compiling")
	val  Running = Value(4, "running")
	val  Ready = Value(5, "ready")
}

object Verdict extends Enumeration {
	type Verdict = Value
	val  Accepted = Value(1, "AC")
	val  PartialAccepted = Value(2, "PA")
	val  PresentationError = Value(3, "PE")
	val  WrongAnswer = Value(4, "WA")
	val  TimeLimitExceeded = Value(5, "TLE")
	val  OutputLimitExceeded = Value(6, "OLE")
	val  MemoryLimitExceeded = Value(7, "MLE")
	val  RuntimeError = Value(8, "RTE")
	val  RestrictedFunctionError = Value(9, "RFE")
	val  CompileError = Value(10, "CE")
	val  JudgeError = Value(11, "JE")
}

object PenaltyType extends Enumeration {
	type PenaltyType = Value
	val  ContestStart = Value(1, "contest_start")
	val  ProblemOpen = Value(2, "problem_open")
	val  Runtime = Value(3, "runtime")
	val  NoPenalty = Value(4, "none")
}

import Validator._
import Verdict._
import Status._
import Language._
import PenaltyType._

class Contest(
	var id: Long = 0,
	var title: String = "",
	var alias: String = "",
	var start_time: Timestamp = new Timestamp(0),
	var finish_time: Timestamp = new Timestamp(0),
	var penalty_type: PenaltyType = PenaltyType.NoPenalty,
	var points_decay_factor: Double = 0,
	var urgent: Boolean = false
) {
  def copy() =
    new Contest(
      id = this.id,
      title = this.title,
      alias = this.alias,
      start_time = this.start_time,
      finish_time = this.finish_time,
      penalty_type = this.penalty_type,
      points_decay_factor = this.points_decay_factor,
      urgent = this.urgent
    )
}

class Problem(
	var id: Long = 0,
	var title: String = "",
	var alias: String = "",
	var validator: Validator = Validator.TokenNumeric,
	var time_limit: Option[Long] = Some(3000),
	var validator_time_limit: Option[Long] = Some(1000),
	var overall_wall_time_limit: Option[Long] = None,
	var extra_wall_time: Long = 0,
	var memory_limit: Option[Long] = Some(64),
	var output_limit: Option[Long] = Some(10240),
	var stack_limit: Option[Long] = Some(10485760),
	var points: Option[Double] = None,
	var tolerance: Double = 1e-6,
	var slow: Boolean = false
) {
  def copy() =
    new Problem(
      id = this.id,
      title = this.title,
      alias = this.alias,
      validator = this.validator,
      time_limit = this.time_limit,
      validator_time_limit = this.validator_time_limit,
      overall_wall_time_limit = this.overall_wall_time_limit,
      extra_wall_time = this.extra_wall_time,
      memory_limit = this.memory_limit,
      output_limit = this.output_limit,
      stack_limit = this.stack_limit,
      points = this.points,
      tolerance = this.tolerance,
      slow = this.slow
    )
}

class User(
	var id: Long = 0,
	var username: String = ""
) {
  def copy() =
    new User(
      id = this.id,
      username = this.username
    )
}

class Run(
	var id: Long = 0,
	var user: Option[User] = None,
	var problem: Problem = null,
	var contest: Option[Contest] = None,
	var guid: String = "",
	var language: Language = Language.C,
	var status: Status = Status.New,
	var verdict: Verdict = Verdict.JudgeError,
	var runtime: Long = 0,
	var penalty: Long = 0,
	var memory: Long = 0,
	var score: Double = 0,
	var contest_score: Option[Double] = None,
	var ip: String = "127.0.0.1",
	var time: Timestamp = new Timestamp(0),
	var submit_delay: Int = 0,
	var judged_by: Option[String] = None
) {
  def copy() =
    new Run(
      id = this.id,
      user = this.user map(_.copy),
      problem = this.problem.copy,
      contest = this.contest map(_.copy),
      guid = this.guid,
      language = this.language,
      status = this.status,
      verdict = this.verdict,
      runtime = this.runtime,
      penalty = this.penalty,
      memory = this.memory,
      score = this.score,
      contest_score = this.contest_score,
      ip = this.ip,
      time = this.time,
      submit_delay = this.submit_delay,
      judged_by = this.judged_by
    )
}
