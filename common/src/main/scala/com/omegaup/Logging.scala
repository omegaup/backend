package com.omegaup

import org.slf4j.LoggerFactory
import org.slf4j.helpers.MessageFormatter
import ch.qos.logback

trait Log {
	protected lazy val log =
		new Logger(getClass.getName.replace("$", "#").stripSuffix("#"))
}

class Logger(name: String) {
	private lazy val log = LoggerFactory.getLogger(name)
			.asInstanceOf[logback.classic.Logger]

	private val isTraceEnabled = log.isTraceEnabled
	private val isDebugEnabled = log.isDebugEnabled
	private val isInfoEnabled = log.isInfoEnabled
	private val isWarnEnabled = log.isWarnEnabled
	private val isErrorEnabled = log.isErrorEnabled

	private def traceImpl(message: String, throwable: Throwable)
			(implicit ctx: Context) = {
		log.trace(message, throwable)
	}
	private def debugImpl(message: String, throwable: Throwable)
			(implicit ctx: Context) = {
		log.debug(message, throwable)
	}
	private def infoImpl(message: String, throwable: Throwable)
			(implicit ctx: Context) = {
		log.info(message, throwable)
	}
	private def warnImpl(message: String, throwable: Throwable)
			(implicit ctx: Context) = {
		log.warn(message, throwable)
	}
	private def errorImpl(message: String, throwable: Throwable)
			(implicit ctx: Context) = {
		log.error(message, throwable)
	}

	def trace(message: String)(implicit ctx: Context) = {
		if (isTraceEnabled) {
			traceImpl(message, null)
		}
	}
	def trace(message: String, values: Any*)(implicit ctx: Context) = {
		if (isTraceEnabled) {
			traceImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				null
			)
		}
	}
	def trace(throwable: Throwable, message: String)(implicit ctx: Context) = {
		if (isTraceEnabled) {
			traceImpl(message, throwable)
		}
	}
	def trace(throwable: Throwable, message: String, values: Object*)
			(implicit ctx: Context) = {
		if (isTraceEnabled) {
			traceImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				throwable
			)
		}
	}

	def debug(message: String)(implicit ctx: Context) = {
		if (isDebugEnabled) {
			debugImpl(message, null)
		}
	}
	def debug(message: String, values: Any*)(implicit ctx: Context) = {
		if (isDebugEnabled) {
			debugImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				null
			)
		}
	}
	def debug(throwable: Throwable, message: String)(implicit ctx: Context) = {
		if (isDebugEnabled) {
			debugImpl(message, throwable)
		}
	}
	def debug(throwable: Throwable, message: String, values: Object*)
			(implicit ctx: Context) = {
		if (isDebugEnabled) {
			debugImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				throwable
			)
		}
	}

	def info(message: String)(implicit ctx: Context) = {
		if (isInfoEnabled) {
			infoImpl(message, null)
		}
	}
	def info(message: String, values: Any*)(implicit ctx: Context) = {
		if (isInfoEnabled) {
			infoImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				null
			)
		}
	}
	def info(throwable: Throwable, message: String)(implicit ctx: Context) = {
		if (isInfoEnabled) {
			infoImpl(message, throwable)
		}
	}
	def info(throwable: Throwable, message: String, values: Object*)
			(implicit ctx: Context) = {
		if (isInfoEnabled) {
			infoImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				throwable
			)
		}
	}

	def warn(message: String)(implicit ctx: Context) = {
		if (isWarnEnabled) {
			warnImpl(message, null)
		}
	}
	def warn(message: String, values: Any*)(implicit ctx: Context) = {
		if (isWarnEnabled) {
			warnImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				null
			)
		}
	}
	def warn(throwable: Throwable, message: String)(implicit ctx: Context) = {
		if (isWarnEnabled) {
			warnImpl(message, throwable)
		}
	}
	def warn(throwable: Throwable, message: String, values: Object*)
			(implicit ctx: Context) = {
		if (isWarnEnabled) {
			warnImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				throwable
			)
		}
	}

	def error(message: String)(implicit ctx: Context) = {
		if (isErrorEnabled) {
			errorImpl(message, null)
		}
	}
	def error(message: String, values: Any*)(implicit ctx: Context) = {
		if (isErrorEnabled) {
			errorImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				null
			)
		}
	}
	def error(throwable: Throwable, message: String)(implicit ctx: Context) = {
		if (isErrorEnabled) {
			errorImpl(message, throwable)
		}
	}
	def error(throwable: Throwable, message: String, values: Object*)
			(implicit ctx: Context) = {
		if (isErrorEnabled) {
			errorImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				throwable
			)
		}
	}
}

object Logging extends Object {
	private val encoderPattern = "%date [%thread] %-5level %logger{35} - %msg%n"
	def init()(implicit ctx: Context): Unit = {
		import logback.classic.Logger
		import logback.classic.Level._
		import logback.core.filter._
		import logback.classic.spi.ILoggingEvent
		import logback.core.spi.FilterReply

		val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
			.asInstanceOf[logback.classic.Logger]

		val logLevel = ctx.config.get("logging.level", "info") match {
			case "all" => TRACE
			case "finest" => TRACE
			case "finer" => TRACE
			case "trace" => TRACE
			case "fine" => DEBUG
			case "config" => DEBUG
			case "debug" => DEBUG
			case "info" => INFO
			case "warn" => WARN
			case "warning" => WARN
			case "error" => ERROR
			case "severe" => ERROR
		}

		createAppender(
			rootLogger,
			ctx.config.get("logging.file", ""),
			new Filter[ILoggingEvent]() {
				override def decide(event: ILoggingEvent): FilterReply = {
					val throwable = event.getThrowableProxy()

					// Jetty is too verbose in DEBUG.
					if (event.getLoggerName.startsWith("org.eclipse.jetty") &&
							event.getLevel == DEBUG && logLevel.isGreaterOrEqual(TRACE)) {
						return FilterReply.DENY
					}

					// These exceptions seem benign and clutter up the logs as well.
					if (throwable != null) {
						val message = throwable.getClassName

						if (message.contains("java.nio.channels.ClosedChannelException") ||
							message.contains("org.mortbay.jetty.EofException")
						) {
							return FilterReply.DENY
						}
					}

					FilterReply.NEUTRAL
				}
			}
		)

		rootLogger.setLevel(logLevel)

		val perfLog = ctx.config.get("logging.perf.file", "")
		if (perfLog != "") {
			val perfLogger = LoggerFactory.getLogger("omegaup.grader.RunContext")
				.asInstanceOf[logback.classic.Logger]

			createAppender(perfLogger, perfLog)

			perfLogger.setAdditive(false)
			perfLogger.setLevel(INFO)
		}
	}

	private def createAppender(
		logger: logback.classic.Logger,
		file: String,
		filter: logback.core.filter.Filter[logback.classic.spi.ILoggingEvent] = null
	) = {
		import logback.core.{FileAppender, ConsoleAppender}
		import logback.classic.net._
		import logback.classic.encoder._
		import logback.classic.spi.ILoggingEvent

		logger.detachAndStopAllAppenders
		val context = logger.getLoggerContext
		val appender = if (file == "syslog") {
			val syslogAppender = new SyslogAppender
			syslogAppender.setFacility("SYSLOG")

			syslogAppender
		} else if (file != "") {
			val encoder = new PatternLayoutEncoder
			encoder.setContext(context)
			encoder.setPattern(encoderPattern)
			encoder.start()

			val fileAppender = new FileAppender[ILoggingEvent]
			fileAppender.setAppend(true)
			fileAppender.setFile(file)
			fileAppender.setEncoder(encoder)

			fileAppender
		} else {
			val encoder = new PatternLayoutEncoder
			encoder.setContext(context)
			encoder.setPattern(encoderPattern)
			encoder.start()

			val consoleAppender = new ConsoleAppender[ILoggingEvent]
			consoleAppender.setEncoder(encoder)

			consoleAppender
		}
		appender.setContext(context)
		if (filter != null) {
			appender.addFilter(filter)
		}
		appender.start

		logger.addAppender(appender)
	}
}

/* vim: set noexpandtab: */
