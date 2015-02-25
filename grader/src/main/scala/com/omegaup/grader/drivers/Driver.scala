package com.omegaup.grader.drivers
  
import com.omegaup._
import com.omegaup.data._
import com.omegaup.grader._

trait Driver {
	def run(run: Run)(implicit ctx: RunContext): Run
	def validateOutput(run: Run)(implicit ctx: RunContext): Run
	def setLogs(run: Run, logs: String)(implicit ctx: RunContext): Unit
}

/* vim: set noexpandtab: */
