packageOptions in (Compile, packageBin) +=
    Package.ManifestAttributes( java.util.jar.Attributes.Name.MAIN_CLASS -> "com.omegaup.runner.Service" )

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-compress" % "1.8.1",
  "org.eclipse.jetty" % "jetty-client" % "9.1.5.v20140505",
  "org.eclipse.jetty" % "jetty-security" % "9.1.5.v20140505",
  "org.eclipse.jetty" % "jetty-server" % "9.1.5.v20140505"
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
  "-keepclassmembers class scala.tools.scalap.scalax.rules.** { *; }",
  "-keep class scala.collection.JavaConversions",
  "-keep class org.eclipse.jetty.util.log.Slf4jLog",
  "-keep class ch.qos.logback.classic.Logger",
  "-keep class ch.qos.logback.classic.spi.ThrowableProxy",
  ProguardOptions.keepMain("com.omegaup.runner.Service")
)

ProguardKeys.inputFilter in Proguard := { file =>
  file.name match {
    case s if s.startsWith("runner") => None
    case s if s.startsWith("scala-compiler") => Some("!META-INF/**,!scala/tools/nsc/**")
    case _ => Some("!**/ECLIPSEF.RSA,!**/ECLIPSEF.SF,!about.html,!META-INF/MANIFEST.MF,!rootdoc.txt,!META-INF/LICENSE.txt,!META-INF/NOTICE.txt")
  }
}

ProguardKeys.proguardVersion in Proguard := "5.2"

mainClass in (Compile, run) := Some("com.omegaup.runner.Service")

javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G")

test in assembly := {}
