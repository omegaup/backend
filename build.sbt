parallelExecution in Global := false

name := "omegaUp"

version := "1.1"

organization := "omegaup"

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

lazy val backend = project.in(file(".")).aggregate(runner, grader)

lazy val common = project

lazy val libinteractive = project

lazy val runner = project.dependsOn(common, libinteractive)

lazy val grader = project.dependsOn(common, runner)
