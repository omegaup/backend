package com.omegaup.data

import com.omegaup.FileUtil
import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.target.Options
import com.omegaup.libinteractive.target.OS
import com.omegaup.libinteractive.target.Command

import java.io.Reader
import java.io.Writer
import java.nio.file.Path
import java.nio.file.Paths

import spray.json._
import DefaultJsonProtocol._

trait LogCopyable[T <: LogCopyable[T]] {
	self: T => def logs: Option[String]
	def copyWithLogs(logs: Option[String]): T
}

case class NullMessage()

// from Runner
case class RunCaseResult(name: String, status: String, time: Int, memory: Int,
	output: Option[String] = None, context: Option[String] = None)

case class CaseData(name: String, data: String)

case class InteractiveDescription(idlSource: String, parentLang: String,
    childLang: String, moduleName: String)
case class CompileInputMessage(lang: String, code: List[(String, String)],
    master_lang: Option[String] = None,
    master_code: Option[List[(String, String)]] = None,
    interactive: Option[InteractiveDescription] = None,
    debug: Boolean = false)
case class CompileOutputMessage(status: String = "ok",
    error: Option[String] = None, token: Option[String] = None,
    logs: Option[String] = None)
		extends LogCopyable[CompileOutputMessage] {
	def copyWithLogs(logs: Option[String]) = this.copy(logs = logs)
}

case class InteractiveRuntimeDescription(main: String, interfaces: List[String],
    parentLang: String)
case class RunInputMessage(token: String, timeLimit: Long = 1000,
		validatorTimeLimit: Long = 1000,
		overallWallTimeLimit: Long = 60000, extraWallTime: Long = 0,
		memoryLimit: Int = 65535, outputLimit: Long = 10240,
		stackLimit: Long = 10485760, debug: Boolean = false,
		input: Option[String] = None, cases: Option[List[CaseData]] = None,
		interactive: Option[InteractiveRuntimeDescription] = None)
case class RunOutputMessage(status: String = "ok", error: Option[String] = None,
    results: Option[List[RunCaseResult]] = None, logs: Option[String] = None)
		extends LogCopyable[RunOutputMessage] {
	def copyWithLogs(logs: Option[String]) = this.copy(logs = logs)
}

case class InputOutputMessage(status: String = "ok",
		error: Option[String] = None, logs: Option[String] = None)

// from Grader
case class ReloadConfigInputMessage(overrides: Option[Map[String, String]] = None)
case class ReloadConfigOutputMessage(status: String = "ok",
	error: Option[String] = None)
case class StatusOutputMessage(status: String = "ok",
	embedded_runner: Boolean = true, queue: Option[QueueStatus] = None,
	broadcaster_sockets: Int = 0)
case class Running(name: String, id: Int)
case class QueueStatus(run_queue_length: Int, runner_queue_length: Int,
	runners: List[String], running: List[Running])
case class RunGradeInputMessage(id: List[String], debug: Boolean = false,
	rejudge: Boolean = false)
case class RunGradeOutputMessage(status: String = "ok", error: Option[String] = None)
case class EndpointRegisterInputMessage(hostname: String, port: Int)
case class EndpointRegisterOutputMessage(status: String = "ok",
	error: Option[String] = None)

// for standalone Grader
case class RunNewInputMessage(problem: String, language: String, code: String,
  ip: Option[String] = None, contest: Option[String] = None)
case class RunNewOutputMessage(status: String = "ok",
  id: Option[String] = None, error: Option[String] = None)
case class RunStatusInputMessage(id: String)
case class RunStatusOutputMessage(problem: String, status: String,
  verdict: String, score: Double, runtime: Double, memory: Double,
  source: String, groups: Option[List[GroupVerdictMessage]],
  compile_error: Option[String], logs: Option[String])
case class RunListInputMessage()
case class RunListOutputMessageEntry(problem: String, id: String,
  status: String, verdict: String, score: Double, runtime: Double,
  memory: Double)
case class ProblemNewOutputMessage(status: String = "ok",
	error: Option[String] = None)
case class ProblemListInputMessage()
case class ProblemListOutputMessageEntry(id: String, title: String,
  validator: String, time_limit: Option[Double],
  overall_wall_time_limit: Option[Double], extra_wall_time: Double,
  memory_limit: Option[Double], output_limit: Option[Double],
  stack_limit: Option[Double], points: Option[Double], slow: Boolean,
  statements: Map[String, String])

// for serializing judgement details
case class CaseVerdictMessage(name: String, verdict: String, score: Double, meta: Map[String, String])
case class GroupVerdictMessage(group: String, cases: List[CaseVerdictMessage],
	score: Double)

// Broadcaster
case class ContestRoleResponse(status: String = "ok", admin: Boolean = false)
case class BroadcastInputMessage(contest: String = "", message: String = "",
	broadcast: Boolean = false, targetUser: Long = -1, userOnly: Boolean = false)
case class BroadcastOutputMessage(status: String = "ok",
	error: Option[String] = None)
case class ScoreboardRefreshResponse(status: String = "ok",
	error: Option[String] = None, errorcode: Option[String] = None,
	header: Option[String] = None)
case class RunDetails(username: Option[String], contest_alias: Option[String],
	alias: String, guid: String, runtime: Double, memory: Long, score: Double,
	contest_score: Option[Double], status: String, verdict: String,
	submit_delay: Long, time: Long, language: String)
case class UpdateRunMessage(message: String, run: RunDetails)

object OmegaUpProtocol extends DefaultJsonProtocol {
	implicit val nullMessageProtocol = jsonFormat0(NullMessage)
	implicit val runCaseResultProtocol = jsonFormat6(RunCaseResult)
	implicit val caseDataProtocol = jsonFormat2(CaseData)
	implicit val interactiveDescriptionProtocol =
		jsonFormat4(InteractiveDescription)
	implicit val compileInputMessageProtocol = jsonFormat6(CompileInputMessage)
	implicit val compileOutputMessageProtocol = jsonFormat4(CompileOutputMessage)
	implicit val interactiveRuntimeDescriptionProtocol =
		jsonFormat3(InteractiveRuntimeDescription)
	implicit val runInputMessageProtocol = jsonFormat12(RunInputMessage)
	implicit val runOutputMessageProtocol = jsonFormat4(RunOutputMessage)
	implicit val inputOutputMessageProtocol = jsonFormat3(InputOutputMessage)
	implicit val endpointRegisterInputMessageProtocol =
		jsonFormat2(EndpointRegisterInputMessage)
	implicit val endpointRegisterOutputMessageProtocol =
		jsonFormat2(EndpointRegisterOutputMessage)
	implicit val caseVerdictMessageProtocol = jsonFormat4(CaseVerdictMessage)
	implicit val groupVerdictMessageProtocol = jsonFormat3(GroupVerdictMessage)
	implicit val contestRoleResponseProtocol = jsonFormat2(ContestRoleResponse)
	implicit val broadcastInputMessageProtocol =
		jsonFormat5(BroadcastInputMessage)
	implicit val broadcastOutputMessageProtocol =
		jsonFormat2(BroadcastOutputMessage)
	implicit val scoreboardRefreshResponseProtocol =
		jsonFormat4(ScoreboardRefreshResponse)
	implicit val runDetailsProtocol = jsonFormat13(RunDetails)
	implicit val updateRunMessageProtocol = jsonFormat2(UpdateRunMessage)
	implicit val runGradeInputMessageProtocol = jsonFormat3(RunGradeInputMessage)
	implicit val runGradeOutputMessageProtocol =
		jsonFormat2(RunGradeOutputMessage)
	implicit val runNewInputMessageProtocol = jsonFormat5(RunNewInputMessage)
	implicit val runNewOutputMessageProtocol = jsonFormat3(RunNewOutputMessage)
	implicit val runStatusInputMessageProtocol =
		jsonFormat1(RunStatusInputMessage)
	implicit val runStatusOutputMessageProtocol =
		jsonFormat10(RunStatusOutputMessage)
	implicit val runListInputMessageProtocol = jsonFormat0(RunListInputMessage)
	implicit val runListOutputMessageEntryProtocol =
		jsonFormat7(RunListOutputMessageEntry)
	implicit val problemNewOutputMessageProtocol =
		jsonFormat2(ProblemNewOutputMessage)
	implicit val problemListInputMessageProtocol =
		jsonFormat0(ProblemListInputMessage)
	implicit val problemListOutputMessageEntryProtocol =
		jsonFormat12(ProblemListOutputMessageEntry)
	implicit val reloadConfigInputMessageProtocol =
		jsonFormat1(ReloadConfigInputMessage)
	implicit val reloadConfigOutputMessageProtocol =
		jsonFormat2(ReloadConfigOutputMessage)
	implicit val runningProtocol = jsonFormat2(Running)
	implicit val queueStatusProtocol = jsonFormat4(QueueStatus)
	implicit val statusOutputMessageProtocol =
		jsonFormat4(StatusOutputMessage)

	implicit object listGroupVerdictMessageProtocol extends JsonFormat[List[GroupVerdictMessage]] {
		override def write(obj: List[GroupVerdictMessage]): JsValue = {
			JsArray(obj.map(_.toJson):_*)
		}
		override def read(json: JsValue): List[GroupVerdictMessage] = {
			json match {
				case a: JsArray => a.elements.map(_.convertTo[GroupVerdictMessage]).toList
				case _ => throw new spray.json.DeserializationException("json was not an array")
			}
		}
	}

	object Serialization {
		def write[T : JsonWriter](obj: T, writer: Writer): Unit = {
			FileUtil.write(writer, obj.toJson.compactPrint)
		}

		def read[T : JsonReader](reader: Reader): T = {
			FileUtil.read(reader).parseJson.convertTo[T]
		}

		def writeString[T : JsonWriter](obj: T): String = {
			obj.toJson.compactPrint
		}

		def readString[T : JsonReader](str: String): T = {
			str.parseJson.convertTo[T]
		}
	}
}

/* vim: set noexpandtab: */
