package com.omegaup.grader

import com.omegaup.RunnerService
import com.omegaup.ServiceInterface
import com.omegaup.data.EndpointRegisterOutputMessage
import com.omegaup.data.QueueStatus

trait RunnerDispatcherInterface extends ServiceInterface {
	def status(): QueueStatus
	def addRun(ctx: RunContext): Unit
	def addRunner(runner: RunnerService): Unit
	def deregister(hostname: String, port: Int): EndpointRegisterOutputMessage
	def register(hostname: String, port: Int): EndpointRegisterOutputMessage
}
