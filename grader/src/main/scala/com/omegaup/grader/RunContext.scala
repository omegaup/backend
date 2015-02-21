package com.omegaup.grader

import com.omegaup.Log
import com.omegaup.RunnerService
import com.omegaup.data.Run

object EventCategory extends Enumeration {
	type EventCategory = Value
	val Task = Value(0)
	val Queue = Value(1)
	val UpdateVerdict = Value(2)
	val Runner = Value(3)
	val Compile = Value(4)
	val Input = Value(5)
	val Run = Value(6)
	val Validate = Value(7)
	val BroadcastQueue = Value(8)
	val GraderRefresh = Value(9)
	val Requeue = Value(10)
}
import EventCategory.EventCategory

case class Event(begin: Boolean, category: EventCategory, args: (String, String)*) {
	val time: Long = System.nanoTime
	override def toString(): String = {
		val argString = if (args.length == 0) {
			""
		} else {
			""","args":{""" + args.map(arg => s""""${arg._1}":"${arg._2}"""").mkString(",") + "}"
		}
		s"""{"name":"$category","tid":0,"pid":0,"ts":${time / 1000},"ph":"${if (begin) 'B' else 'E'}"$argString}"""
	}
}

case class FlowEvent(begin: Boolean, id: Long, category: EventCategory) {
	val time: Long = System.nanoTime
	override def toString(): String = {
		s"""{"name":"$category","tid":0,"pid":0,"id":0x${id.toHexString},"ts":${time / 1000},"ph":"${if (begin) 's' else 'f'}"}"""
	}
}

case class CompleteEvent(category: EventCategory, time: Long, duration: Long, args: Seq[(String, String)]) {
	override def toString(): String = {
		val argString = if (args.length == 0) {
			""
		} else {
			""","args":{""" + args.map(arg => s""""${arg._1}":"${arg._2}"""").mkString(",") + "}"
		}
		s"""{"name":"$category","tid":0,"pid":0,"ts":${time / 1000},"dur":${duration / 1000},"ph":"X"$argString}"""
	}
}

class RunContext(grader: Option[Grader], var run: Run, val debug: Boolean, val rejudge: Boolean) extends Object with Log {
	val creationTime = System.currentTimeMillis
	val rejudges: Int = 0
	val eventList = new scala.collection.mutable.MutableList[AnyRef]
	var service: RunnerService = null
	var flightTime: Long = 0
	private var hasBeenDequeued: Boolean = false
	eventList += new Event(true, EventCategory.Task)

	def startFlight(service: RunnerService) = {
		this.service = service
		this.flightTime = System.currentTimeMillis
	}

	def trace[T](category: EventCategory, args: (String, String)*)(f: => T): T = {
		val t0 = System.nanoTime
		try {
			f
		} finally {
			val t1 = System.nanoTime
			eventList += new CompleteEvent(category, t0, t1 - t0, args)
		}
	}

	def queued(): Unit = {
		// If this event has been previously dequeued, log whatever we had previously and start anew
		if (hasBeenDequeued) {
			val flowId = (run.id.toLong << 32) | System.currentTimeMillis
			eventList += new FlowEvent(true, flowId, EventCategory.Requeue)
			finish
			eventList.clear
			eventList += new FlowEvent(false, flowId, EventCategory.Requeue)
			eventList += new Event(true, EventCategory.Task, "requeued" -> "true")
		}
		eventList += new Event(true, EventCategory.Queue)
	}
	def dequeued(runner: String): Unit = {
		eventList += new Event(false, EventCategory.Queue, "runner" -> runner)
		hasBeenDequeued = true
	}

	def broadcastQueued(): Unit = eventList += new Event(true, EventCategory.BroadcastQueue)
	def broadcastDequeued(): Unit = eventList += new Event(false, EventCategory.BroadcastQueue)

	def finish() = {
		eventList += new Event(false, EventCategory.Task, "run_id" -> run.id.toString)
		log.info("[" + eventList.map(_.toString).mkString(",") + "]")
	}

	def updateVerdict(run: Run) = {
		grader.map(_.updateVerdict(this, run))
	}
}

/* vim: set noexpandtab: */
