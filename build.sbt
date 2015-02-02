parallelExecution in Global := false

lazy val commonSettings = Seq(
	version := "1.1",
	organization := "com.omegaup",
	scalaVersion := "2.10.4",
	scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
	exportJars := true,
	libraryDependencies ++= Seq(
		"ch.qos.logback" % "logback-classic" % "1.1.1",
		"ch.qos.logback" % "logback-core" % "1.1.1",
		"com.omegaup" %% "libinteractive" % "latest.integration",
		"commons-codec" % "commons-codec" % "1.9",
		"net.liftweb" %% "lift-json" % "2.5.1",
		"org.scalatest" %% "scalatest" % "2.1.2" % "test",
		"org.slf4j" % "log4j-over-slf4j" % "1.7.6"
	)
)

lazy val backend = project.in(file("."))
	.aggregate(common, runner, grader)
	.settings(commonSettings: _*)
	.settings(
		name := "omegaUp"
	)

lazy val common = project
	.settings(commonSettings: _*)
	.settings(
		name := "common"
	)

lazy val runner = project
	.dependsOn(common)
	.settings(commonSettings: _*)
	.settings(
		name := "runner"
	)

lazy val grader = project
	.dependsOn(common, runner)
	.settings(commonSettings: _*)
	.settings(
		name := "grader"
	)
