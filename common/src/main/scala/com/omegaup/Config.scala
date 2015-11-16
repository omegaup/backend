package com.omegaup

case class BroadcasterConfig(
	enable_all_events: Boolean = false,
	port: Int = 39613	
)

case class RootsConfig(
	compile: String = "/var/lib/omegaup/compile",
	grade: String = "/var/lib/omegaup/grade",
	input: String = "/var/lib/omegaup/input",
	problems: String = "/var/lib/omegaup/problems",
	submissions: String = "/var/lib/omegaup/submissions"
)

case class CompilersConfig(
	c: String = "/usr/bin/gcc",
	cpp: String = "/usr/bin/g++",
	hs: String = "/usr/lib/ghc/lib/ghc",
	java: String = "/usr/bin/javac",
	karel: String = "/usr/bin/kcl",
	pas: String = "/usr/bin/fpc",
	py: String = "/usr/bin/python",
	rb: String = "/usr/bin/ruby"
)

case class PathsConfig(
	karel: String = "/usr/bin/karel",
	minijail: String = "/var/lib/minijail"
)

case class CommonConfig(
	compilers: CompilersConfig = CompilersConfig(),
	paths: PathsConfig = PathsConfig(),
	roots: RootsConfig = RootsConfig()
)

case class RoutingConfig(
	registered_runners: List[String] = List(),
	slow_threshold: Int = 50,
	table: String = ""
)

case class ScoreboardRefreshConfig(
	disabled: Boolean = false,
	interval: Int = 10000,
	token: String = "secret",
	url: String = "http://localhost/api/scoreboard/refresh/"
)

case class GraderConfig(
	embedded_runner_enabled: Boolean = false,
	flight_pruner_interval: Int = 60,
	port: Int = 21680,
	proxy_runner_enabled: Boolean = false,
	routing: RoutingConfig = RoutingConfig(),
	runner_queue_timeout: Int = 600,
	runner_timeout: Int = 600,
	scoreboard_refresh: ScoreboardRefreshConfig = ScoreboardRefreshConfig(),
	standalone: Boolean = false
)

case class OmegaUpConfig(
	role_url: String = "http://localhost/api/contest/role/",
	salt: String = "omegaup"
)

case class DbConfig(
	driver: String = "org.h2.Driver",
	password: String = "",
	url: String = "jdbc:h2:file:omegaup",
	user: String = "omegaup"
)

case class LoggingConfig(
	file: String = "/var/log/omegaup/service.log",
	level: String = "info",
	max_size: String = "25MB",
	perf_file: String = "/var/log/omegaup/perf.log",
	rolling_count: Int = 10
)

case class RunnerLimitsConfig(
	compile_time: Int = 30,
	memory: Int = 640 * 1024 * 1024,
	output: Int = 64 * 1024 * 1024
)

case class RunnerConfig(
	deregister_url: String = "https://localhost:21680/endpoint/deregister/",
	hostname: String = "localhost",
	limits: RunnerLimitsConfig = new RunnerLimitsConfig(),
	port: Int = 21681,
	preserve: Boolean = false,
	preserve_tar: Boolean = false,
	register_url: String = "https://localhost:21680/endpoint/register/",
	sandbox: String = "minijail"
)

case class SslConfig(
	disabled: Boolean = false,
	keystore_password: String = "omegaup",
	keystore_path: String = "omegaup.jks",
	password: String = "omegaup",
	truststore_password: String = "omegaup",
	truststore_path: String = "omegaup.jks"
)

case class ProxyConfig(
	admin_password: String = "",
	admin_username: String = "",
	contest_alias: String = "",
	contestants: List[String] = List(),
	remote_host: String = "omegaup.com",
	use_tls: Boolean = true
)

case class Config(
	broadcaster: BroadcasterConfig = BroadcasterConfig(),
	common: CommonConfig = CommonConfig(),
	db: DbConfig = DbConfig(),
	grader: GraderConfig = GraderConfig(),
	logging: LoggingConfig = LoggingConfig(),
	omegaup: OmegaUpConfig = OmegaUpConfig(),
	proxy: ProxyConfig = ProxyConfig(),
	runner: RunnerConfig = RunnerConfig(),
	ssl: SslConfig = SslConfig()
)

object ConfigMerge {
	import spray.json._
	import DefaultJsonProtocol._

	private def copy(obj: Any, json: JsObject): Object = {
		val clazz = obj.getClass
		val generated = List("productIterator", "productPrefix", "productArity",
			"productElement", "canEqual", "tupled", "curried", "unapply", "equals",
			"toString", "hashCode", "apply", "copy", "andThen", "compose")
		val fields = clazz.getMethods.toSeq
				.map(meth => meth -> meth.getName)
				.filter({ case (meth, name) =>
						meth.getDeclaringClass == clazz && !name.contains("$") &&
						!generated.contains(name)
				})
				.sortWith(_._2 < _._2)
				.map(_._1)
		val copyMethods = clazz.getMethods.filter(_.getName == "copy")
		if (copyMethods.length == 0) {
			throw new NoSuchMethodException(clazz + ".copy")
		}
		val meth = copyMethods(0)
		assert(meth.getParameterTypes.toSeq ==
			fields.map(_.getReturnType).toSeq)

		val params = fields.map(field => {
			val original = field.invoke(obj)
			(json.fields.get(field.getName) match {
				case Some(o: JsObject) => copy(original, o)
				case Some(i: JsNumber) if field.getReturnType == classOf[Int] =>
					i.value.toInt
				case Some(i: JsNumber) if field.getReturnType == classOf[Long] =>
					i.value.toLong
				case Some(i: JsNumber) if field.getReturnType == classOf[Float] =>
					i.value.toFloat
				case Some(i: JsNumber) if field.getReturnType == classOf[Double] =>
					i.value.toDouble
				case Some(s: JsString) => s.value
				case Some(JsTrue) => true
				case Some(JsFalse) => false
				case Some(a: JsArray) => a.elements.map(_.asInstanceOf[JsString].value).toList
				case _ => original
			}).asInstanceOf[Object]
		}).toSeq

		meth.invoke(obj, params:_*)
	}

	def apply[T](obj: T, json: String) = {
		copy(obj, json.parseJson.asJsObject).asInstanceOf[T]
	}
}

/* vim: set noexpandtab: */
