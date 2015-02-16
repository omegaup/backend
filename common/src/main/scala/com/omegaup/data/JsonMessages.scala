package com.omegaup.data

import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.target.Options
import com.omegaup.libinteractive.target.OS
import com.omegaup.libinteractive.target.Command

import java.nio.file.Path
import java.nio.file.Paths

import net.liftweb.json.Formats
import net.liftweb.json.JNull
import net.liftweb.json.JString
import net.liftweb.json.JValue
import net.liftweb.json.MappingException
import net.liftweb.json.NoTypeHints
import net.liftweb.json.Serializer
import net.liftweb.json.Serialization
import net.liftweb.json.TypeInfo

case class NullMessage()

// Serializers
object OSSerializer extends Serializer[OS.EnumVal] {
  private val OSClass = classOf[OS.EnumVal]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), OS.EnumVal] = {
    case (TypeInfo(OSClass, _), json) => json match {
      case JString(s) if !OS.values.find(_.name == s).isEmpty =>
          OS.values.find(_.name == s).get
      case x => throw new MappingException("Can't convert " + x + " to OS")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: OS.EnumVal => JString(x.toString)
  }
}

object CommandSerializer extends Serializer[Command.EnumVal] {
  private val CommandClass = classOf[Command.EnumVal]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Command.EnumVal] = {
    case (TypeInfo(CommandClass, _), json) => json match {
      case JString(s) if !Command.values.find(_.name == s).isEmpty =>
          Command.values.find(_.name == s).get
      case x => throw new MappingException("Can't convert " + x + " to Command")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: Command.EnumVal => JString(x.toString)
  }
}

object PathSerializer extends Serializer[Path] {
  private val PathClass = classOf[Path]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Path] = {
    case (TypeInfo(PathClass, _), json) => json match {
      case JString(s) => Paths.get(s)
      case JNull => null
      case x => throw new MappingException("Can't convert " + x + " to Path")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case null => JNull
    case x: Path => JString(x.toString)
  }
}

object OmegaUpSerialization {
  val formats = Serialization.formats(NoTypeHints) +
      PathSerializer +
      CommandSerializer +
      OSSerializer
}

// from Runner
case class RunCaseResult(name: String, status: String, time: Int, memory: Int, output: Option[String] = None, context: Option[String] = None)
case class CaseData(name: String, data: String)

case class InteractiveDescription(idlSource: String, parentLang: String,
    childLang: String, moduleName: String)
case class CompileInputMessage(lang: String, code: List[(String, String)],
    master_lang: Option[String] = None,
    master_code: Option[List[(String, String)]] = None,
    interactive: Option[InteractiveDescription] = None,
    debug: Boolean = false)
case class CompileOutputMessage(status: String = "ok", error: Option[String] = None, token: Option[String] = None)

case class InteractiveRuntimeDescription(main: String, interfaces: List[String],
    parentLang: String)
case class RunInputMessage(token: String, timeLimit: Long = 1000,
  overallWallTimeLimit: Long = 60000, extraWallTime: Long = 0, memoryLimit: Int = 65535,
  outputLimit: Long = 10240, stackLimit: Long = 10485760, debug: Boolean = false,
  input: Option[String] = None, cases: Option[List[CaseData]] = None,
  interactive: Option[InteractiveRuntimeDescription] = None)
case class RunOutputMessage(status: String = "ok", error: Option[String] = None,
  results: Option[List[RunCaseResult]] = None)

case class InputOutputMessage(status: String = "ok", error: Option[String] = None)

// from Grader
case class ReloadConfigInputMessage(overrides: Option[Map[String, String]] = None)
case class ReloadConfigOutputMessage(status: String = "ok", error: Option[String] = None)
case class StatusOutputMessage(status: String = "ok", embedded_runner: Boolean = true, queue: Option[QueueStatus] = None)
case class Running(name: String, id: Int)
case class QueueStatus(
  run_queue_length: Int,
  runner_queue_length: Int,
  runners: List[String],
  running: List[Running]
)
case class RunGradeInputMessage(id: List[String], debug: Boolean = false, rejudge: Boolean = false)
case class RunGradeOutputMessage(status: String = "ok", error: Option[String] = None)
case class EndpointRegisterInputMessage(hostname: String, port: Int)
case class EndpointRegisterOutputMessage(status: String = "ok", error: Option[String] = None)

// for standalone Grader
case class RunNewInputMessage(problem: String, language: String, code: String,
  ip: Option[String] = None, contest: Option[String] = None)
case class RunNewOutputMessage(status: String = "ok",
  id: Option[String] = None, error: Option[String] = None)
case class RunStatusInputMessage(id: String)
case class RunStatusOutputMessage(problem: String, status: String,
  verdict: String, score: Double, runtime: Double, memory: Double,
  source: String, groups: Option[List[GroupVerdictMessage]],
  compile_error: Option[String])
case class RunListInputMessage()
case class RunListOutputMessageEntry(problem: String, id: String,
  status: String, verdict: String, score: Double, runtime: Double,
  memory: Double)
case class ProblemNewOutputMessage(status: String = "ok", error: Option[String] = None)
case class ProblemListInputMessage()
case class ProblemListOutputMessageEntry(id: String, title: String,
  validator: String, time_limit: Option[Double],
  overall_wall_time_limit: Option[Double], extra_wall_time: Double,
  memory_limit: Option[Double], output_limit: Option[Double],
  stack_limit: Option[Double], points: Option[Double], slow: Boolean,
  statements: Map[String, String])

// for serializing judgement details
case class CaseVerdictMessage(name: String, verdict: String, score: Double)
case class GroupVerdictMessage(group: String, cases: List[CaseVerdictMessage], score: Double)

// Broadcaster
case class ContestRoleResponse(status: String = "ok", admin: Boolean = false)
case class BroadcastInputMessage(
  contest: String = "",
  message: String = "",
  broadcast: Boolean = false,
  targetUser: Long = -1,
  userOnly: Boolean = false
)
case class BroadcastOutputMessage(status: String = "ok", error: Option[String] = None)
case class ScoreboardRefreshResponse(status: String = "ok", error: Option[String] = None, errorcode: Option[String] = None, header: Option[String] = None)
