package com.omegaup
import ch.qos.logback.classic.Level

class Context(val config: Config = new Config, val overrideLogger: OverrideLogger = null) {
	def loggingOverride(level: Level) = {
		overrideLogger != null && overrideLogger.overrides(level)
	}
}

/* vim: set noexpandtab: */
