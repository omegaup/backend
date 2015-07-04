packageOptions in (Compile, packageBin) +=
    Package.ManifestAttributes( java.util.jar.Attributes.Name.MAIN_CLASS -> "com.omegaup.Service" )

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.3.175" % "test",
  "mysql" % "mysql-connector-java" % "5.1.29" % "test",
  "org.apache.commons" % "commons-compress" % "1.8.1",
  "commons-fileupload" % "commons-fileupload" % "1.3.1",
  "commons-io" % "commons-io" % "2.4",
  "org.eclipse.jetty" % "jetty-client" % "9.1.5.v20140505",
  "org.eclipse.jetty" % "jetty-security" % "9.1.5.v20140505",
  "org.eclipse.jetty" % "jetty-server" % "9.1.5.v20140505",
  "org.eclipse.jetty.websocket" % "websocket-server" % "9.1.5.v20140505"
)

proguardSettings

ProguardKeys.options in Proguard ++= Seq(
  "-dontoptimize",
  "-dontobfuscate",
  "-dontnote",
  "-dontwarn",
  "-keep interface scala.ScalaObject",
  "-keep class com.omegaup.**",
  "-keepclasseswithmembers public class com.omegaup.** { public *** copy(...); }",
  "-keepclasseswithmembers public class com.omegaup.data.** { *; }",
  "-keep class scala.collection.JavaConversions",
  "-keep class org.eclipse.jetty.util.log.Slf4jLog",
  "-keep class org.eclipse.jetty.websocket.server.WebSocketServerFactory { *; }",
  "-keep class org.eclipse.jetty.websocket.api.extensions.*",
  "-keep class org.eclipse.jetty.websocket.common.extensions.**",
  "-keep class ch.qos.logback.classic.Logger",
  "-keep class ch.qos.logback.classic.spi.ThrowableProxy",
  ProguardOptions.keepMain("com.omegaup.Service")
)

ProguardKeys.inputFilter in Proguard := { file =>
  file.name match {
    case s if s.startsWith("grader") => None
    case s if s.startsWith("scala-compiler") => Some("!META-INF/**,!scala/tools/nsc/**")
    case _ => Some("!**/ECLIPSEF.RSA,!**/ECLIPSEF.SF,!about.html,!META-INF/MANIFEST.MF,!rootdoc.txt,!META-INF/services/java.sql.Driver,!META-INF/LICENSE.txt,!META-INF/NOTICE.txt,!decoder.properties")
  }
}

ProguardKeys.proguardVersion in Proguard := "5.2"

mainClass in (Compile, run) := Some("com.omegaup.Service")

javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G")

test in assembly := {}
