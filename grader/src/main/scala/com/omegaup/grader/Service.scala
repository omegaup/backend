package com.omegaup.grader

import com.omegaup.Config
import com.omegaup.Https
import com.omegaup.Log
import com.omegaup.Logging
import com.omegaup.Context

object Service extends Object with Log {
	def parseOptions(args: Array[String]): GraderOptions = {
		var options = GraderOptions()
		var i = 0
		while (i < args.length) {
			if (args(i) == "--config" && i + 1 < args.length) {
				i += 1
				options = options.copy(configPath = args(i))
			} else if (args(i) == "--output" && i + 1 < args.length) {
				i += 1
				val redirect = new java.io.PrintStream(
					new java.io.FileOutputStream(args(i)))
				System.setOut(redirect)
				System.setErr(redirect)
			}
			i += 1
		}

		options
	}

	def main(args: Array[String]) = {
		val options = parseOptions(args)

		implicit val ctx = new Context(new Config(options.configPath))

		// logger
		Logging.init

		val server = new Grader(options)

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
