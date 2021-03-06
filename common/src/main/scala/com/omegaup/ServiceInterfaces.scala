package com.omegaup

import com.omegaup.data._
import java.io.InputStream
import java.io.OutputStream

trait ServiceInterface {
	def updateContext(newCtx: Context)
	def start(): Unit
	def stop(): Unit
	def join(): Unit
}

trait RunCaseCallback {
	def apply(filename: String, length: Long, stream: InputStream): Unit
}

case class InputEntry(name: String, data: InputStream, length: Long, hash: String)

trait RunnerService {
	def compile(message: CompileInputMessage)(implicit ctx: Context): CompileOutputMessage
	def run(message: RunInputMessage, callback: RunCaseCallback)(implicit ctx: Context): RunOutputMessage
	def input(inputName: String, entries: Iterable[InputEntry])(implicit ctx: Context): InputOutputMessage
	def name(): String
	def port(): Int = 21681
	override def toString() = "RunnerService(%s)".format(name)
}

trait GraderService {
	def grade(message: RunGradeInputMessage): RunGradeOutputMessage
}

/* vim: set noexpandtab: */
