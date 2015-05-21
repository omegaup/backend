package com.omegaup.runner

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._

import com.omegaup._
import com.omegaup.data._

trait Sandbox {
  def targetFileName(lang: String, target: String) = lang match {
    case "c" => target
    case "cpp" => target
    case "cpp11" => target
    case "pas" => target
    case "hs" => target
    case "rb" => s"$target.rb"
    case "py" => s"$target.pyc"
    case "java" => s"$target.class"
    case "kj" => s"$target.kx"
    case "kp" => s"$target.kx"
  }

  def compile[A](lang: String,
                 inputFiles: TraversableOnce[String],
                 chdir: String = "",
                 outputFile: String = "",
                 errorFile: String = "",
                 metaFile: String = "",
                 target: String = "Main",
                 extraFlags: TraversableOnce[String] = List())(callback: Int => A)(implicit ctx: Context): A

  def run(message: RunInputMessage,
          lang: String,
          logTag: String = "Run",
          extraParams: TraversableOnce[String] = List[String](),
          chdir: String = "",
          inputFile: String = "",
          outputFile: String = "",
          errorFile: String = "",
          metaFile: String = "",
          originalInputFile: Option[String] = None,
          runMetaFile: Option[String] = None,
          target: String = "Main",
          extraMountPoints: List[(String, String)] = List[(String, String)]())(implicit ctx: Context): Unit
}

object NullSandbox extends Object with Sandbox with Log with Using {
  private val scheduler = Executors.newScheduledThreadPool(1)
  private val TimedOut = 0x34c9d964

  def compile[A](lang: String,
                 inputFiles: TraversableOnce[String],
                 chdir: String = "",
                 outputFile: String = "",
                 errorFile: String = "",
                 metaFile: String = "",
                 target: String = "Main",
                 extraFlags: TraversableOnce[String] = List()) (callback: Int => A)(implicit ctx: Context): A = {
    val params = (lang match {
      case "java" =>
        List(ctx.config.common.compilers.java, "-J-Xmx512M") ++
        inputFiles
      case "c" =>
        List(ctx.config.common.compilers.c, "-std=c99", "-O2") ++
        inputFiles ++ List("-lm", "-o", target)
      case "cpp" =>
        List(ctx.config.common.compilers.cpp, "-O2") ++
        inputFiles ++ List("-lm", "-o", target)
      case "cpp11" =>
        List(ctx.config.common.compilers.cpp, "-O2", "-std=c++11", "-xc++") ++
        inputFiles ++ List("-lm", "-o", target)
      case "pas" =>
        List(
          ctx.config.common.compilers.pas,
          "-Tlinux",
          "-O2",
          "-Mobjfpc",
          "-Sc",
          "-Sh"
        ) ++
        inputFiles ++ List("-o" + target)
      case "py" =>
        List(ctx.config.common.compilers.py, "-m", "py_compile") ++
        inputFiles
      case "rb" =>
        List(ctx.config.common.compilers.rb, "-wc") ++
        inputFiles
      case "kj" =>
        List(
          ctx.config.common.compilers.karel,
          "-lj",
          "-o",
          s"$target.kx",
          "-c"
        ) ++
        inputFiles
      case "kp" =>
        List(
          ctx.config.common.compilers.karel,
          "-lp",
          "-o",
          s"$target.kx",
          "-c"
        ) ++
        inputFiles
      case "hs" =>
        List(
          ctx.config.common.compilers.hs, "-B/usr/lib/ghc",
          "-O2",
          "-o",
          target
        ) ++
        inputFiles
      case _ => null
    }) ++ extraFlags

    log.debug("Compile {}", params.mkString(" "))

    val builder = new ProcessBuilder(params)
    builder.directory(new File(chdir))
    builder.redirectError(new File(errorFile))
    builder.redirectOutput(new File(outputFile))

    val t0 = System.currentTimeMillis
    val status = runWithTimeout(builder, ctx.config.runner.limits.compile_time * 1000)
    val t1 = System.currentTimeMillis

    val meta = Map(
      "time" -> ("%.3f" format ((t1 - t0) / 1000.0)),
      "time-wall" -> ("%.3f" format ((t1 - t0) / 1000.0)),
      "mem" -> "0"
    ) + (status match {
      case TimedOut => "status" -> "TO"
      case 0 => "status" -> "OK"
      case _ => {
        val errorPath = chdir + "/" + errorFile
        // Truncate the compiler error to 8k
        try {
          val outChan = new java.io.FileOutputStream(errorPath, true).getChannel()
          outChan.truncate(8192)
          outChan.close()
        } catch {
          case e: Exception => {
            log.error("Unable to truncate {}: {}", errorPath, e)
          }
        }
        "status" -> "RE"
      }
    })

    MetaFile.save(metaFile, meta)
    callback(status)
  }

  def run(message: RunInputMessage,
          lang: String,
          logTag: String = "Run",
          extraParams: TraversableOnce[String] = List[String](),
          chdir: String = "",
          inputFile: String = "",
          outputFile: String = "",
          errorFile: String = "",
          metaFile: String = "",
          originalInputFile: Option[String] = None,
          runMetaFile: Option[String] = None,
          target: String = "Main",
          extraMountPoints: List[(String, String)] = List[(String, String)]())(implicit ctx: Context): Unit = {
    val timeLimit = message.timeLimit + (lang match {
      case "java" => 1000
      case _ => 0
    }) + message.extraWallTime
    // 16MB + memory limit to prevent some RTE
    val memoryLimit = (16 * 1024 + message.memoryLimit) * 1024

    originalInputFile match {
      case Some(file) => FileUtil.copy(new File(file), new File(chdir, "data.in"))
      case None => {}
    }

    runMetaFile match {
      case Some(file) => FileUtil.copy(new File(file), new File(chdir, "meta.in"))
      case None => {}
    }

    val params = (lang match {
      case "java" =>
        List("/usr/bin/java", "-Xmx" + memoryLimit, target)
      case "c" =>
        List(s"./$target")
      case "cpp" =>
        List(s"./$target")
      case "cpp11" =>
        List(s"./$target")
      case "pas" =>
        List(s"./$target")
      case "py" =>
        List("/usr/bin/python", s"$target.py")
      case "rb" =>
        List(ctx.config.common.compilers.rb, s"$target.rb")
      case "kp" =>
        List(
          ctx.config.common.paths.karel,
          "/dev/stdin",
          "-oi",
          "-q",
          "-p2",
          s"$target.kx"
        )
      case "kj" =>
        List(
          ctx.config.common.paths.karel,
          "/dev/stdin",
          "-oi",
          "-q",
          "-p2",
          s"$target.kx"
        )
      case "hs" =>
        List(s"./$target")
    }) ++ extraParams

    log.debug("{} {}", logTag, params.mkString(" "))
    val builder = new ProcessBuilder(params)
    builder.directory(new File(chdir))
    builder.redirectError(new File(errorFile))
    builder.redirectOutput(new File(outputFile))
    builder.redirectInput(new File(inputFile))

    val t0 = System.currentTimeMillis
    val status = runWithTimeout(builder, timeLimit)
    val t1 = System.currentTimeMillis

    val meta = Map(
      "time" -> ("%.3f" format ((t1 - t0) / 1000.0)),
      "time-wall" -> ("%.3f" format ((t1 - t0) / 1000.0)),
      "mem" -> "0"
    ) + (status match {
      case TimedOut => "status" -> "TO"
      case 0 => "status" -> "OK"
      case _ => "status" -> "RE"
    })

    MetaFile.save(metaFile, meta)
  }

  private def runWithTimeout(builder: ProcessBuilder, timeout: Long): Int = {
    using(builder.start) { p => {
      val future = scheduler.schedule(new Runnable() {
        override def run(): Unit = {
          p.destroy
        }
      }, timeout, TimeUnit.MILLISECONDS)

      p.waitFor
      if (!future.cancel(false)) {
        TimedOut
      } else {
        p.exitValue
      }
    }}
  }
}

object Minijail extends Object with Sandbox with Log with Using {
  val executor = Executors.newCachedThreadPool

  def compile[A](lang: String,
                 inputFiles: TraversableOnce[String],
                 chdir: String = "",
                 outputFile: String = "",
                 errorFile: String = "",
                 metaFile: String = "",
                 target: String = "Main",
                 extraFlags: TraversableOnce[String] = List()) (callback: Int => A)(implicit ctx: Context): A = {
    val minijail = ctx.config.common.paths.minijail + "/bin/minijail0"
    val scripts = ctx.config.common.paths.minijail + "/scripts"
    val runtime = Runtime.getRuntime

    val commonParams = List(
      "-C", ctx.config.common.paths.minijail + "/root-compilers",
      "-d", "/home",
      "-b", chdir + ",/home,1",
      "-1", outputFile,
      "-2", errorFile,
      "-M", metaFile,
      "-t", (ctx.config.runner.limits.compile_time * 1000).toString,
      "-O", ctx.config.runner.limits.output.toString
    )

    val chrootedInputFiles = inputFiles.map(file => {
      if (!file.startsWith(chdir)) {
        throw new IllegalArgumentException("File " + file + " is not within the chroot jail")
      }
      file.substring(chdir.length + 1)
    })

    val params = (lang match {
      case "java" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/javac") ++
        commonParams ++
        List(
          "-b", ctx.config.common.paths.minijail + "/root-openjdk,/usr/lib/jvm",
          "-b", "/sys/,/sys"
        ) ++
        List("--", ctx.config.common.compilers.java, "-J-Xmx512M") ++
        chrootedInputFiles
      case "c" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/gcc") ++
        commonParams ++
        List("--", ctx.config.common.compilers.c, "-std=c99", "-O2") ++
        chrootedInputFiles ++ List("-lm", "-o", target)
      case "cpp" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/gcc") ++
        commonParams ++
        List("--", ctx.config.common.compilers.cpp, "-O2") ++
        chrootedInputFiles ++ List("-lm", "-o", target)
      case "cpp11" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/gcc") ++
        commonParams ++
        List("--", ctx.config.common.compilers.cpp, "-O2", "-std=c++11", "-xc++") ++
        chrootedInputFiles ++ List("-lm", "-o", target)
      case "pas" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/fpc") ++
        commonParams ++
        List(
          "--",
          "/usr/bin/ldwrapper", ctx.config.common.compilers.pas,
          "-Tlinux",
          "-O2",
          "-Mobjfpc",
          "-Sc",
          "-Sh"
        ) ++
        chrootedInputFiles ++ List("-o" + target)
      case "py" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/pyc") ++
        commonParams ++
        List("-b", ctx.config.common.paths.minijail + "/root-python,/usr/lib/python2.7") ++
        List("--", ctx.config.common.compilers.py, "-m", "py_compile") ++
        chrootedInputFiles
      case "rb" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/ruby") ++
        commonParams ++
        List("-b", ctx.config.common.paths.minijail + "/root-ruby,/usr/lib/ruby") ++
        List("--", ctx.config.common.compilers.rb, "-wc") ++
        chrootedInputFiles
      case "kj" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/kcl") ++
        commonParams ++
        List(
          "--",
          "/usr/bin/ldwrapper", ctx.config.common.compilers.karel,
          "-lj",
          "-o",
          s"$target.kx",
          "-c"
        ) ++
        chrootedInputFiles
      case "kp" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/kcl") ++
        commonParams ++
        List(
          "--",
          "/usr/bin/ldwrapper", ctx.config.common.compilers.karel,
          "-lp",
          "-o",
          s"$target.kx",
          "-c"
        ) ++
        chrootedInputFiles
      case "hs" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/ghc") ++
        commonParams ++
        List("-b", ctx.config.common.paths.minijail + "/root-hs,/usr/lib/ghc") ++
        List(
          "--",
          ctx.config.common.compilers.hs, "-B/usr/lib/ghc",
          "-O2",
          "-o",
          target
        ) ++
        chrootedInputFiles
      case _ => null
    }) ++ extraFlags

    log.debug("Compile {}", params.mkString(" "))

    val status = runMinijail(params)
    if (status != -1) {
      // Truncate the compiler error to 8k
      try {
        val outChan = new java.io.FileOutputStream(errorFile, true).getChannel()
        outChan.truncate(8192)
        outChan.close()
      } catch {
        case e: Exception => {
          log.error("Unable to truncate {}: {}", errorFile, e)
        }
      }
      patchMetaFile(lang, status, None, metaFile)
    }
    callback(status)
  }

  def run(message: RunInputMessage,
          lang: String,
          logTag: String = "Run",
          extraParams: TraversableOnce[String] = List[String](),
          chdir: String = "",
          inputFile: String = "",
          outputFile: String = "",
          errorFile: String = "",
          metaFile: String = "",
          originalInputFile: Option[String] = None,
          runMetaFile: Option[String] = None,
          target: String = "Main",
          extraMountPoints: List[(String, String)] = List[(String, String)]())(implicit ctx: Context) = {
    val minijail = ctx.config.common.paths.minijail + "/bin/minijail0"
    val scripts = ctx.config.common.paths.minijail + "/scripts"
    val runtime = Runtime.getRuntime

    val timeLimit = message.timeLimit + (lang match {
      case "java" => 1000
      case _ => 0
    })
    val extraWallTime = message.extraWallTime

    val commonParams = List(
      "-C", ctx.config.common.paths.minijail + "/root",
      "-d", "/home",
      "-b", chdir + ",/home",
      "-0", inputFile,
      "-1", outputFile,
      "-2", errorFile,
      "-M", metaFile,
      "-t", timeLimit.toString,
      "-w", extraWallTime.toString,
      "-O", message.outputLimit.toString,
      "-k", message.stackLimit.toString
    ) ++ extraMountPoints.flatMap { case (path, target) => {
      List("-b", path + "," + target)
    }}

    originalInputFile match {
      case Some(file) => FileUtil.copy(new File(file), new File(chdir, "data.in"))
      case None => {}
    }

    runMetaFile match {
      case Some(file) => FileUtil.copy(new File(file), new File(chdir, "meta.in"))
      case None => {}
    }

    // 16MB + memory limit to prevent some RTE
    val memoryLimit = (16 * 1024 + message.memoryLimit) * 1024
    // "640MB should be enough for anybody"
    val hardLimit = Math.max(
      memoryLimit,
      ctx.config.runner.limits.memory
    ).toString

    val params = (lang match {
      case "java" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/java") ++
        commonParams ++
        List(
          "-b", ctx.config.common.paths.minijail + "/root-openjdk,/usr/lib/jvm",
          "-b", "/sys/,/sys"
        ) ++
        List("--", "/usr/bin/java", "-Xmx" + memoryLimit, target)
      case "c" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/cpp") ++
        commonParams ++
        List("-m", hardLimit, "--", s"./$target")
      case "cpp" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/cpp") ++
        commonParams ++
        List("-m", hardLimit, "--", s"./$target")
      case "cpp11" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/cpp") ++
        commonParams ++
        List("-m", hardLimit, "--", s"./$target")
      case "pas" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/pas") ++
        commonParams ++
        List("-m", hardLimit, "--", "/usr/bin/ldwrapper", s"./$target")
      case "py" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/py") ++
        commonParams ++
        List("-b", ctx.config.common.paths.minijail + "/root-python,/usr/lib/python2.7") ++
        List("-m", hardLimit, "--", "/usr/bin/python", s"$target.py")
      case "rb" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/ruby") ++
        commonParams ++
        List("-b", ctx.config.common.paths.minijail + "/root-ruby,/usr/lib/ruby") ++
        List("--", ctx.config.common.compilers.rb, s"$target.rb")
      case "kp" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/karel") ++
        commonParams ++
        List(
          "--",
          "/usr/bin/ldwrapper", ctx.config.common.paths.karel,
          "/dev/stdin",
          "-oi",
          "-q",
          "-p2",
          s"$target.kx"
        )
      case "kj" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/karel") ++
        commonParams ++
        List(
          "--",
          "/usr/bin/ldwrapper", ctx.config.common.paths.karel,
          "/dev/stdin",
          "-oi",
          "-q",
          "-p2",
          s"$target.kx"
        )
      case "hs" =>
        List("/usr/bin/sudo", minijail, "-S", scripts + "/hs") ++
        commonParams ++
        List("-b", ctx.config.common.paths.minijail + "/root-hs,/usr/lib/ghc") ++
        List("-m", hardLimit, "--", s"./$target")
    }) ++ extraParams

    log.debug("{} {}", logTag, params.mkString(" "))
    val status = runMinijail(params)
    patchMetaFile(lang, status, Some(message), metaFile)
  }

  private def runMinijail(params: List[String])(implicit ctx: Context): Int = {
    val runtime = Runtime.getRuntime
    var status = -1
    var syscallName = ""

    using (runtime.exec(params.toArray)) { minijail =>
      if (minijail == null) {
        log.error("minijail process was null")
      } else {
        status = minijail.waitFor
        log.debug("minijail returned {}", status)
      }
    }

    status
  }

  private def patchMetaFile(lang: String, status: Int, message: Option[RunInputMessage], metaFile: String)(implicit ctx: Context) = {
    val meta = try {
      collection.mutable.Map(MetaFile.load(metaFile).toSeq: _*)
    } catch {
      case e: java.io.FileNotFoundException => collection.mutable.Map("time" -> "0",
                                                                      "time-wall" -> "0",
                                                                      "mem" -> "0",
                                                                      "signal" -> "-1")
    }

    if (meta.contains("signal")) {
      meta("status") = meta("signal") match {
        case "4" => "FO"  // SIGILL
        case "6" => "RE"  // SIGABRT
        case "7" => "SG"  // SIGBUS
        case "8" => "RE"  // SIGFPE
        case "9" => "FO"  // SIGKILL
        case "11" => "SG" // SIGSEGV
        case "13" => "RE" // SIGPIPE
        case "14" => "TO" // SIGALRM
        case "24" => "TO" // SIGXCPU
        case "30" => "TO" // SIGXCPU
        case "31" => "FO" // SIGSYS
        case "25" => "OL" // SIGFSZ
        case "35" => "OL" // SIGFSZ
        case other => {
          log.error("Received odd signal: {}", other)
          "JE"
        }
      }
    } else {
      meta("return") = meta("status")
      if (meta("status") == "0" || lang == "c") {
        meta("status") = "OK"
      } else if (meta("status") != "JE") {
        meta("status") = "RE"
      }
    }

    message match {
      case Some(m) => {
        if (lang == "java") {
          // Subtract the core JVM memory consumption. 
          meta("mem") = (meta("mem").toLong - 14000 * 1024).toString
        } else if (meta("status") != "JE" &&
                   meta("mem").toLong > m.memoryLimit * 1024) {
          meta("status") = "ML"
          meta("mem") = (m.memoryLimit * 1024).toString
        }
      }
      case _ => {}
    }

    meta("time") = "%.3f" format (meta("time").toInt / 1e6)
    meta("time-wall") = "%.3f" format (meta("time-wall").toInt / 1e6)
    MetaFile.save(metaFile, meta)
  }
}
