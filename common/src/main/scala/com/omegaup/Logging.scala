package com.omegaup

import ch.qos.logback
import com.omegaup.data.LogCopyable
import java.io.ByteArrayOutputStream
import logback.classic.Level
import logback.classic.Level._
import logback.classic.PatternLayout
import logback.classic.spi.ILoggingEvent
import logback.classic.spi.LoggingEvent
import logback.core.OutputStreamAppender
import org.slf4j.LoggerFactory
import org.slf4j.helpers.MessageFormatter

import spray.json.JsObject
import spray.json.JsString

trait Log {
	protected lazy val log =
		new Logger(getClass.getName.replace("$", "#").stripSuffix("#"))
}

class OverrideLogger(levelName: String) {
	val baseLevel = Logging.parseLevel(levelName)
	val layout = new PatternLayout
	layout.setPattern(Logging.layoutPattern)
	layout.setContext(Logging.rootLogger.getLoggerContext)
	val buffer = new StringBuilder(128)

	def overrides(level: Level) = level.isGreaterOrEqual(baseLevel)
	def append(event: ILoggingEvent) = {
		val element = layout.doLayout(event)
		buffer.append(element)
	}
	def append(message: String) = {
		buffer.append(message)
	}
	def start() = {
		layout.start
	}
	def stop() = {
		layout.stop
	}
	override def toString() = {
		layout.stop
		buffer.toString
	}
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
		if (ctx.loggingOverride(TRACE))
			ctx.overrideLogger.append(new LoggingEvent(logback.classic.Logger.FQCN,
				log, TRACE, message, throwable, null))
		log.trace(message, throwable)
	}
	private def debugImpl(message: String, throwable: Throwable)
			(implicit ctx: Context) = {
		if (ctx.loggingOverride(DEBUG))
			ctx.overrideLogger.append(new LoggingEvent(logback.classic.Logger.FQCN,
				log, DEBUG, message, throwable, null))
		log.debug(message, throwable)
	}
	private def infoImpl(message: String, throwable: Throwable)
			(implicit ctx: Context) = {
		if (ctx.loggingOverride(INFO))
			ctx.overrideLogger.append(new LoggingEvent(logback.classic.Logger.FQCN,
				log, INFO, message, throwable, null))
		log.info(message, throwable)
	}
	private def warnImpl(message: String, throwable: Throwable)
			(implicit ctx: Context) = {
		if (ctx.loggingOverride(WARN))
			ctx.overrideLogger.append(new LoggingEvent(logback.classic.Logger.FQCN,
				log, WARN, message, throwable, null))
		log.warn(message, throwable)
	}
	private def errorImpl(message: String, throwable: Throwable)
			(implicit ctx: Context) = {
		if (ctx.loggingOverride(ERROR))
			ctx.overrideLogger.append(new LoggingEvent(logback.classic.Logger.FQCN,
				log, ERROR, message, throwable, null))
		log.error(message, throwable)
	}

	def trace(message: String)(implicit ctx: Context) = {
		if (isTraceEnabled || ctx.loggingOverride(TRACE)) {
			traceImpl(message, null)
		}
	}
	def trace(message: String, values: Any*)(implicit ctx: Context) = {
		if (isTraceEnabled || ctx.loggingOverride(TRACE)) {
			traceImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				null
			)
		}
	}
	def trace(throwable: Throwable, message: String)(implicit ctx: Context) = {
		if (isTraceEnabled || ctx.loggingOverride(TRACE)) {
			traceImpl(message, throwable)
		}
	}
	def trace(throwable: Throwable, message: String, values: Object*)
			(implicit ctx: Context) = {
		if (isTraceEnabled || ctx.loggingOverride(TRACE)) {
			traceImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				throwable
			)
		}
	}

	def debug(message: String)(implicit ctx: Context) = {
		if (isDebugEnabled || ctx.loggingOverride(DEBUG)) {
			debugImpl(message, null)
		}
	}
	def debug(message: String, values: Any*)(implicit ctx: Context) = {
		if (isDebugEnabled || ctx.loggingOverride(DEBUG)) {
			debugImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				null
			)
		}
	}
	def debug(throwable: Throwable, message: String)(implicit ctx: Context) = {
		if (isDebugEnabled || ctx.loggingOverride(DEBUG)) {
			debugImpl(message, throwable)
		}
	}
	def debug(throwable: Throwable, message: String, values: Object*)
			(implicit ctx: Context) = {
		if (isDebugEnabled || ctx.loggingOverride(DEBUG)) {
			debugImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				throwable
			)
		}
	}

	def info(message: String)(implicit ctx: Context) = {
		if (isInfoEnabled || ctx.loggingOverride(INFO)) {
			infoImpl(message, null)
		}
	}
	def info(message: String, values: Any*)(implicit ctx: Context) = {
		if (isInfoEnabled || ctx.loggingOverride(INFO)) {
			infoImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				null
			)
		}
	}
	def info(throwable: Throwable, message: String)(implicit ctx: Context) = {
		if (isInfoEnabled || ctx.loggingOverride(INFO)) {
			infoImpl(message, throwable)
		}
	}
	def info(throwable: Throwable, message: String, values: Object*)
			(implicit ctx: Context) = {
		if (isInfoEnabled || ctx.loggingOverride(INFO)) {
			infoImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				throwable
			)
		}
	}

	def warn(message: String)(implicit ctx: Context) = {
		if (isWarnEnabled || ctx.loggingOverride(WARN)) {
			warnImpl(message, null)
		}
	}
	def warn(message: String, values: Any*)(implicit ctx: Context) = {
		if (isWarnEnabled || ctx.loggingOverride(WARN)) {
			warnImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				null
			)
		}
	}
	def warn(throwable: Throwable, message: String)(implicit ctx: Context) = {
		if (isWarnEnabled || ctx.loggingOverride(WARN)) {
			warnImpl(message, throwable)
		}
	}
	def warn(throwable: Throwable, message: String, values: Object*)
			(implicit ctx: Context) = {
		if (isWarnEnabled || ctx.loggingOverride(WARN)) {
			warnImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				throwable
			)
		}
	}

	def error(message: String)(implicit ctx: Context) = {
		if (isErrorEnabled || ctx.loggingOverride(ERROR)) {
			errorImpl(message, null)
		}
	}
	def error(message: String, values: Any*)(implicit ctx: Context) = {
		if (isErrorEnabled || ctx.loggingOverride(ERROR)) {
			errorImpl(
				MessageFormatter.arrayFormat(
					message, values.map(_.asInstanceOf[Object]).toArray
				).getMessage,
				null
			)
		}
	}
	def error(throwable: Throwable, message: String)(implicit ctx: Context) = {
		if (isErrorEnabled || ctx.loggingOverride(ERROR)) {
			errorImpl(message, throwable)
		}
	}
	def error(throwable: Throwable, message: String, values: Object*)
			(implicit ctx: Context) = {
		if (isErrorEnabled || ctx.loggingOverride(ERROR)) {
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
	import logback.classic.Logger
	import logback.core.filter._
	import logback.core.spi.FilterReply

	val layoutPattern = "%date [%thread] %-5level %logger{35} - %msg%n"
	val perfLayoutPattern = "%msg%n"
	val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
		.asInstanceOf[logback.classic.Logger]

	def init()(implicit ctx: Context): Unit = {
		val logLevel = parseLevel(ctx.config.logging.level)

		createAppender(
			rootLogger,
			ctx.config.logging.file,
			layoutPattern,
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

		val perfLog = ctx.config.logging.perf_file
		if (perfLog != "") {
			val perfLogger = LoggerFactory.getLogger("com.omegaup.grader.RunContext")
				.asInstanceOf[logback.classic.Logger]

			createAppender(perfLogger, perfLog, perfLayoutPattern)

			perfLogger.setAdditive(false)
			perfLogger.setLevel(INFO)
		}
	}

	def parseLevel(name: String) = {
		name match {
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
			case "off" => OFF
		}
	}

	private def createAppender(
		logger: logback.classic.Logger,
		fileName: String,
		layoutPattern: String,
		filter: logback.core.filter.Filter[logback.classic.spi.ILoggingEvent] = null
	)(implicit ctx: Context) = {
		import logback.core.{FileAppender, ConsoleAppender}
		import logback.core.rolling.FixedWindowRollingPolicy
		import logback.core.rolling.RollingFileAppender
		import logback.core.rolling.SizeBasedTriggeringPolicy
		import logback.classic.net._
		import logback.classic.encoder._
		import logback.classic.spi.ILoggingEvent

		logger.detachAndStopAllAppenders
		val context = logger.getLoggerContext
		val appender = if (fileName == "syslog") {
			val syslogAppender = new SyslogAppender
			syslogAppender.setFacility("SYSLOG")

			syslogAppender
		} else if (fileName != "") {
			val encoder = new PatternLayoutEncoder
			encoder.setContext(context)
			encoder.setPattern(layoutPattern)
			encoder.start

			val rollingPolicy = new FixedWindowRollingPolicy
			val nameTuple = FileUtil.splitExtension(FileUtil.basename(fileName))
			rollingPolicy.setContext(context)
			rollingPolicy.setFileNamePattern(
				"%s.%%i.%s" format (nameTuple.productIterator.toList: _*))
			rollingPolicy.setMinIndex(1)
			rollingPolicy.setMaxIndex(ctx.config.logging.rolling_count)

			val triggeringPolicy = new SizeBasedTriggeringPolicy[ILoggingEvent]
			triggeringPolicy.setMaxFileSize(ctx.config.logging.max_size)

			val fileAppender = new RollingFileAppender[ILoggingEvent]
			fileAppender.setAppend(true)
			fileAppender.setFile(fileName)
			fileAppender.setEncoder(encoder)
			fileAppender.setRollingPolicy(rollingPolicy)
			fileAppender.setTriggeringPolicy(triggeringPolicy)
			rollingPolicy.setParent(fileAppender)
			rollingPolicy.start
			triggeringPolicy.start

			fileAppender
		} else {
			val encoder = new PatternLayoutEncoder
			encoder.setContext(context)
			encoder.setPattern(layoutPattern)
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

	def debugWrap[T <: LogCopyable[T]](f: (Boolean => Context) => T)(implicit parentCtx: Context): T = {
		var overrideLogger: Option[OverrideLogger] = None
		try {
			val message = f(debug => {
				if (debug) {
					overrideLogger = Some(new OverrideLogger("debug"))
					overrideLogger.get.start
					new Context(parentCtx.config, overrideLogger.get)
				} else {
					parentCtx
				}
			})
			overrideLogger match {
				case None => message
				case Some(logger) => message.copyWithLogs(Some(logger.toString))
			}
		} finally {
			overrideLogger.map(_.stop)
		}
	}
}

/* vim: set noexpandtab: */
