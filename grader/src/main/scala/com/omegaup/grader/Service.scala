package com.omegaup.grader

import com.omegaup.ContextMixin
import com.omegaup.Log

object Service extends Object with Log with ContextMixin {
	override def start() = {
		val server = new Grader

		Runtime.getRuntime.addShutdownHook(new Thread() {
			override def run() = {
				log.info("Shutting down")
				server.stop
			}
		});

		server.join
	}
}

/* vim: set noexpandtab: */
