packageOptions in (Compile, packageBin) +=
    Package.ManifestAttributes( java.util.jar.Attributes.Name.MAIN_CLASS -> "com.omegaup.Service" )

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.3.175" % "runtime",
  "mysql" % "mysql-connector-java" % "5.1.29" % "runtime",
  "org.apache.commons" % "commons-compress" % "1.8.1",
  "org.eclipse.jetty" % "jetty-client" % "9.1.5.v20140505",
  "org.eclipse.jetty" % "jetty-security" % "9.1.5.v20140505",
  "org.eclipse.jetty" % "jetty-server" % "9.1.5.v20140505",
  "org.eclipse.jetty.websocket" % "websocket-server" % "9.1.5.v20140505"
)

proguardSettings

ProguardKeys.options in Proguard ++= Seq(
  "-dontskipnonpubliclibraryclasses",
  "-dontskipnonpubliclibraryclassmembers",
  "-dontoptimize",
  "-dontobfuscate",
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
  "-keep class org.eclipse.jetty.websocket.server.WebSocketServerFactory { *; }",
  "-keep class org.eclipse.jetty.websocket.api.extensions.*",
  "-keep class org.eclipse.jetty.websocket.common.extensions.**",
  "-keep class org.eclipse.jetty.websocket.**",
  "-keep class ch.qos.logback.classic.Logger",
  "-keep class ch.qos.logback.classic.spi.ThrowableProxy",
  ProguardOptions.keepMain("com.omegaup.Service")
)

ProguardKeys.inputFilter in Proguard := { file =>
  file.name match {
    case "grader_2.10-1.1.jar" => None
    case _ => Some("!**/ECLIPSEF.RSA,!**/ECLIPSEF.SF,!about.html,!META-INF/MANIFEST.MF,!rootdoc.txt,!META-INF/services/java.sql.Driver,!META-INF/LICENSE.txt,!META-INF/NOTICE.txt")
  }
}

ProguardKeys.proguardVersion in Proguard := "5.2"

mainClass in (Compile, run) := Some("com.omegaup.Service")

javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G")
