package com.omegaup

import org.slf4j.{Logger, LoggerFactory}

trait Log {
	private lazy val log = LoggerFactory.getLogger(getClass.getName.replace("$", "#").stripSuffix("#"))

	protected def trace(message:String, values:Any*) =
		log.trace(message, values.map(_.asInstanceOf[Object]).toArray:_*)
	protected def trace(message:String, error:Throwable) = log.trace(message, error)

	protected def debug(message:String, values:Any*) =
		log.debug(message, values.map(_.asInstanceOf[Object]).toArray:_*)
	protected def debug(message:String, error:Throwable) = log.debug(message, error)

	protected def info(message:String, values:Any*) =
		log.info(message, values.map(_.asInstanceOf[Object]).toArray:_*)
	protected def info(message:String, error:Throwable) = log.info(message, error)

	protected def warn(message:String, values:Any*) =
		log.warn(message, values.map(_.asInstanceOf[Object]).toArray:_*)
	protected def warn(message:String, error:Throwable) = log.warn(message, error)

	protected def error(message:String, values:Any*) =
		log.error(message, values.map(_.asInstanceOf[Object]).toArray:_*)
	protected def error(message:String, error:Throwable) = log.error(message, error)
}

object Logging extends Object with Log {
	private val encoderPattern = "%date [%thread] %-5level %logger{35} - %msg%n"
	def init(): Unit = {
		import ch.qos.logback.classic.Logger
		import ch.qos.logback.classic.Level._
		import ch.qos.logback.core.filter._
		import ch.qos.logback.classic.spi.ILoggingEvent
		import ch.qos.logback.core.spi.FilterReply

		val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]

		val logLevel = Config.get("logging.level", "info") match {
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

		createAppender(rootLogger, Config.get("logging.file", ""), new Filter[ILoggingEvent]() {
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
		})

		rootLogger.setLevel(logLevel)

		val perfLog = Config.get("logging.perf.file", "")
		if (perfLog != "") {
			val perfLogger = LoggerFactory.getLogger("omegaup.grader.RunContext").asInstanceOf[Logger]

			createAppender(perfLogger, perfLog)

			perfLogger.setAdditive(false)
			perfLogger.setLevel(INFO)
		}

		info("Logger loaded for {}", Config.get("logging.file", ""))
	}

	private def createAppender(
		logger: ch.qos.logback.classic.Logger,
		file: String,
		filter: ch.qos.logback.core.filter.Filter[ch.qos.logback.classic.spi.ILoggingEvent] = null
	) = {
		import ch.qos.logback.core.{FileAppender, ConsoleAppender}
		import ch.qos.logback.classic.net._
		import ch.qos.logback.classic.encoder._
		import ch.qos.logback.classic.spi.ILoggingEvent

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
