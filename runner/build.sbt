name := "Runner"

version := "1.1"

organization := "omegaup"

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

exportJars := true

packageOptions in (Compile, packageBin) +=
    Package.ManifestAttributes( java.util.jar.Attributes.Name.MAIN_CLASS -> "com.omegaup.runner.Service" )

libraryDependencies ++= Seq(
  "org.eclipse.jetty" % "jetty-server" % "9.1.5.v20140505",
  "org.eclipse.jetty" % "jetty-client" % "9.1.5.v20140505",
  "org.eclipse.jetty" % "jetty-security" % "9.1.5.v20140505",
  "net.liftweb" %% "lift-json" % "2.5.1",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.6",
  "ch.qos.logback" % "logback-core" % "1.1.1",
  "ch.qos.logback" % "logback-classic" % "1.1.1",
  "commons-codec" % "commons-codec" % "1.9",
  "org.apache.commons" % "commons-compress" % "1.8.1",
  "org.scalatest" %% "scalatest" % "2.1.2" % "test"
)

proguardSettings 

ProguardKeys.options in Proguard ++= Seq(
  "-dontskipnonpubliclibraryclasses",
  "-dontskipnonpubliclibraryclassmembers",
  "-dontoptimize",
  "-dontobfuscate",
  "-dontpreverify",
  "-dontnote",
  "-dontwarn",
  "-keep interface scala.ScalaObject",
  "-keep class com.omegaup.*",
  "-keep class com.omegaup.data.*",
  "-keep class com.omegaup.grader.*",
  "-keep class com.omegaup.runner.*",
  "-keepclassmembers class com.omegaup.data.* { *; }",
  "-keepclassmembers class com.omegaup.runner.* { *; }",
  "-keepclassmembers class com.omegaup.Service { *; }",
  "-keep class scala.collection.JavaConversions",
  "-keep class org.eclipse.jetty.util.log.Slf4jLog",
  "-keep class ch.qos.logback.classic.Logger",
  "-keep class ch.qos.logback.classic.spi.ThrowableProxy",
  ProguardOptions.keepMain("com.omegaup.runner.Service")
)

ProguardKeys.inputFilter in Proguard := { file =>
  file.name match {
    case "runner_2.10-1.1.jar" => None
    case _ => Some("!**/ECLIPSEF.RSA,!**/ECLIPSEF.SF,!about.html,!META-INF/MANIFEST.MF,!rootdoc.txt,!META-INF/LICENSE.txt,!META-INF/NOTICE.txt")
  }
}

javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G")
