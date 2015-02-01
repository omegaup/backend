package com.omegaup

import com.omegaup.data._
import java.io.InputStream
import java.io.OutputStream

trait ServiceInterface {
	def stop(): Unit
	def join(): Unit
}

trait RunCaseCallback {
	def apply(filename: String, length: Long, stream: InputStream): Unit
}

case class InputEntry(name: String, data: InputStream, length: Long, hash: String)

trait RunnerService {
	def compile(message: CompileInputMessage): CompileOutputMessage
	def run(message: RunInputMessage, callback: RunCaseCallback): RunOutputMessage
	def input(inputName: String, entries: Iterable[InputEntry]): InputOutputMessage
	def name(): String
	def port(): Int = 21681
	override def toString() = "RunnerService(%s)".format(name)
}

trait GraderService {
	def grade(message: GradeInputMessage): GradeOutputMessage
}

/* vim: set noexpandtab: */
