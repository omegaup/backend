package com.omegaup.grader.drivers
  
import com.omegaup._
import com.omegaup.data._
import com.omegaup.grader._

trait Driver {
  def run(ctx: RunContext, run: Run): Run
  def grade(ctx: RunContext, run: Run): Run
}
