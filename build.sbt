parallelExecution in Global := false

enablePlugins(GitVersioning)

git.useGitDescribe := true

git.gitDescribedVersion <<= git.gitDescribedVersion((v) => v.map(_.drop(1)))

lazy val commonSettings = Seq(
	organization := "com.omegaup",
	scalaVersion := "2.11.5",
	scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
	exportJars := true,
	libraryDependencies ++= Seq(
		"ch.qos.logback" % "logback-classic" % "1.1.1",
		"ch.qos.logback" % "logback-core" % "1.1.1",
		"com.omegaup" %% "libinteractive" % "latest.integration",
		"commons-codec" % "commons-codec" % "1.9",
		"io.spray" %%  "spray-json" % "1.3.1",
		"org.scalatest" %% "scalatest" % "2.2.4" % "test",
		"org.slf4j" % "log4j-over-slf4j" % "1.7.6"
	),
	ProguardKeys.proguardVersion in Proguard := "5.2"
)

lazy val backend = project.in(file("."))
	.aggregate(common, runner, grader)
	.settings(commonSettings: _*)
	.settings(
		name := "omegaUp"
	)

lazy val common_macros = project
	.settings(commonSettings: _*)
	.settings(
		name := "common_macros"
	)

lazy val common = project
	.dependsOn(common_macros)
	.settings(commonSettings: _*)
	.settings(
		name := "common"
	)
	.enablePlugins(BuildInfoPlugin)
	.settings(
		buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
		buildInfoPackage := "com.omegaup"
	)

lazy val runner = project
	.dependsOn(common % "compile->compile;test->test")
	.settings(commonSettings: _*)
	.settings(
		name := "runner"
	)

lazy val grader = project
	.dependsOn(common % "compile->compile;test->test", runner)
	.settings(commonSettings: _*)
	.settings(
		name := "grader"
	)

mainClass in (Compile, run) := Some("com.omegaup.Service")

test in assembly := {}
