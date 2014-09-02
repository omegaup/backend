package com.omegaup

import com.omegaup.data._
import java.io.InputStream
import java.io.OutputStream

trait RunCaseCallback {
	def apply(filename: String, length: Long, stream: InputStream): Unit
}

case class InputEntry(name: String, data: InputStream, length: Long, hash: String)

abstract class RunnerService {
	def compile(message: CompileInputMessage): CompileOutputMessage
	def run(message: RunInputMessage, callback: RunCaseCallback): RunOutputMessage
	def input(inputName: String, entries: Iterable[InputEntry]): InputOutputMessage
	def name(): String
	def port(): Int = 21681
	override def toString() = "RunnerService(%s)".format(name)
}

abstract class GraderService {
	def grade(id: Long): GradeOutputMessage
}

/* vim: set noexpandtab: */
