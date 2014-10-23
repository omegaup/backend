parallelExecution in Global := false

name := "omegaUp"

version := "1.1"

organization := "omegaup"

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

lazy val backend = project.in(file(".")).aggregate(libinteractive, runner, grader)

lazy val libinteractive = project

lazy val common = project.dependsOn(libinteractive)

lazy val runner = project.dependsOn(common, libinteractive)

lazy val grader = project.dependsOn(common, runner)
