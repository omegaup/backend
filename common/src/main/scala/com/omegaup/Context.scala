package com.omegaup

import ch.qos.logback.classic.Level

class Context(val config: Config = new Config, val overrideLogger: OverrideLogger = null) {
	def loggingOverride(level: Level) = {
		overrideLogger != null && overrideLogger.overrides(level)
	}
}

trait ContextMixin {
	protected implicit var serviceCtx: Context = null

	private def parseOptions(args: Array[String]): Context = {
		var configPath = "omegaup.conf"
		var i = 0
		while (i < args.length) {
			if (args(i) == "--config" && i + 1 < args.length) {
				i += 1
				configPath = args(i)
			} else if (args(i) == "--output" && i + 1 < args.length) {
				i += 1
				val redirect = new java.io.PrintStream(
					new java.io.FileOutputStream(args(i)))
				System.setOut(redirect)
				System.setErr(redirect)
			}
			i += 1
		}

		new Context(ConfigMerge(Config(), FileUtil.read(configPath)))
	}

	def updateContext(newCtx: Context) = {
		serviceCtx = newCtx
	}

	final def main(args: Array[String]) = {
		serviceCtx = parseOptions(args)

		// logger
		Logging.init

		start
	}

	def start(): Unit
}

/* vim: set noexpandtab: */
