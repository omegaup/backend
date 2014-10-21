package com.omegaup.grader.drivers

import com.omegaup._
import com.omegaup.data._
import com.omegaup.grader._
import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.idl.Parser
import java.io._
import java.util.concurrent._
import java.util.zip.DeflaterOutputStream
import scala.util.matching.Regex
import scala.collection.mutable.{ListBuffer, TreeSet}
import Language._
import Verdict._
import Status._
import Validator._

object OmegaUpDriver extends Driver with Log with Using {
  def getInputName(alias: String): String = {
    val runtime = Runtime.getRuntime
    val params = List("/usr/bin/git", "ls-tree", "HEAD", "-d", "cases/in")
    val env = List[String]()
    val path = new File(Config.get("problems.root", "problems"), alias)

    // Call git to obtain the hash of the cases/in directory.
    pusing (runtime.exec(params.toArray, env.toArray, path)) { p => {
      using (new BufferedReader(new InputStreamReader(p.getInputStream))) { reader =>
        reader.readLine.split("\\s")(2)
      }
    }}
  }

  def getInputEntries(alias: String, inputName: String): Iterable[InputEntry] = {
    val runtime = Runtime.getRuntime
    val params = List("/usr/bin/git", "ls-tree", inputName)
    val env = List[String]()
    val path = new File(Config.get("problems.root", "problems"), alias)
    val objectsPath = new File(path, ".git/objects")
    val casesPath = new File(path, "cases/in")
    val treeHashes = new TreeSet[(String, String)]

    // Get the files in the directory from git.
    pusing (runtime.exec(params.toArray, env.toArray, path)) { p => {
      var line: String = null
      using (new BufferedReader(new InputStreamReader(p.getInputStream))) { reader =>{
        while ({ line = reader.readLine ; line != null } ){
          var tokens = line.split("\t")
          val name = tokens(1)
          tokens = tokens(0).split(" ")
          treeHashes += ((name, tokens(2)))
        }
      }}
    }}

    // Return a lazy view with the hashes plus their stream.
    treeHashes.view.map { case (name: String, hash: String) => {
        val file = new File(objectsPath, hash.substring(0, 2) + "/" + hash.substring(2))
        if (file.exists) {
          new InputEntry(name, new FileInputStream(file), file.length, hash)
        } else {
          // The file is likely within a .pack file. We need to compress it manually.
          // TODO(lhchavez): It's probably not a great idea to do this in-memory.
          val originalFile = new File(casesPath, name)
          val header = s"blob ${originalFile.length}\u0000".getBytes
          val bytes = new ByteArrayOutputStream
          using (new DeflaterOutputStream(bytes)) { zlib => {
            zlib.write(header)
            using (new FileInputStream(originalFile)) {
              FileUtil.copy(_, zlib)
            }
          }}

          new InputEntry(name, new ByteArrayInputStream(bytes.toByteArray), bytes.size, null)
        }
    }}
  }

  override def run(ctx: RunContext, run: Run): Run = {
    val id = run.id
    val alias = run.problem.alias
    val lang = run.language
    val errorFile = new File(Config.get("grader.root", "grader"), + id + ".err")

    info("Compiling {} {} on {}", alias, id, ctx.service.name)
	
    if (errorFile.exists) {
      errorFile.delete
    }

    run.status = Status.Compiling
    run.judged_by = Some(ctx.service.name)
    Manager.updateVerdict(ctx, run)

    val code = FileUtil.read(Config.get("submissions.root", "submissions") + "/" + run.guid)
    val compileMessage = createCompileMessage(run, code)
    val output = ctx.trace(EventCategory.Compile) {
      ctx.service.compile(compileMessage)
    }
  
    if(output.status != "ok") {
      FileUtil.write(errorFile, output.error.get)
  
      run.status = Status.Ready
      run.verdict = Verdict.CompileError
      run.memory = 0
      run.runtime = 0
      run.score = 0

      return run
    }

    val input = getInputName(alias)
    val msg = new RunInputMessage(
      output.token.get,
      debug = ctx.debug,
      timeLimit = run.problem.time_limit match {
        case Some(x) => x / 1000.0f
        case _ => 1.0f
      },
      memoryLimit = run.problem.memory_limit match {
        case Some(x) => x.toInt
        case _ => 65535
      },
      outputLimit = run.problem.output_limit match {
        case Some(x) => x.toLong
        case _ => 10240
      },
      stackLimit = run.problem.stack_limit match {
        case Some(x) => x.toLong
        case _ => 10485760
      },
      input = Some(input),
      interactive = compileMessage.interactive match {
        case None => None
        case Some(interactive) => {
          val parser = new Parser
          val idl = parser.parse(interactive.idlSource)

          Some(InteractiveRuntimeDescription(
            main = idl.main.name,
            interfaces = idl.interfaces.map(_.name),
            parentLang = interactive.parentLang
          ))
        }
      }
    )
  
    run.status = Status.Running
    Manager.updateVerdict(ctx, run)

    val target = new File(Config.get("grader.root", "grader"), id.toString)
    FileUtil.deleteDirectory(target)
    target.mkdir
    val placer = new CasePlacer(target)

    info("Running {}({}) on {}", alias, id, ctx.service.name)
    var response = ctx.trace(EventCategory.Run) {
      ctx.service.run(msg, placer)
    }
    debug("Ran {} {}, returned {}", alias, id, response)
    if (response.status != "ok") {
      if (response.error.get ==  "missing input") {
        info("Received a missing input message, trying to send input from {} ({})", alias, ctx.service.name)
        ctx.trace(EventCategory.Input) {
          if(ctx.service.input(
            input, getInputEntries(alias, input)
          ).status != "ok") {
            throw new RuntimeException("Unable to send input. giving up.")
          }
        }
        response = ctx.trace(EventCategory.Run) {
          ctx.service.run(msg, placer)
        }
        if (response.status != "ok") {
          error("Second try, ran {}({}) on {}, returned {}", alias, id, ctx.service.name, response)
          throw new RuntimeException("Unable to run submission after sending input. giving up.")
        }
      } else {
        throw new RuntimeException(response.error.get)
      }
    }

    // Finally return the run.
    run
  }

  class CasePlacer(directory: File) extends Object with RunCaseCallback with Using with Log {
    def apply(filename: String, length: Long, stream: InputStream): Unit = {
      debug("Placing file {}({}) into {}", filename, length, directory)
      val target = new File(directory, filename)
      if (!target.getParentFile.exists) {
        target.getParentFile.mkdirs
      }
      using (new FileOutputStream(new File(directory, filename))) {
        FileUtil.copy(stream, _)
      }
    }
  }

  override def grade(ctx: RunContext, run: Run): Run = {
    ctx.trace(EventCategory.Grade) {
      run.problem.validator match {
        case Validator.Custom => CustomGrader.grade(ctx, run)
        case Validator.Token => TokenGrader.grade(ctx, run)
        case Validator.TokenCaseless => TokenCaselessGrader.grade(ctx, run)
        case Validator.TokenNumeric => TokenNumericGrader.grade(ctx, run)
        case _ => throw new IllegalArgumentException("Validator " + run.problem.validator + " not found")
      }
    }
  }

  @throws(classOf[FileNotFoundException])
  private def createCompileMessage(run: Run, code: String): CompileInputMessage = {
    var validatorLang: Option[String] = None
    var validatorCode: Option[List[(String, String)]] = None

    if (run.problem.validator == Validator.Custom) {
      List("c", "cpp", "py", "pas", "rb").map(lang => {
        (lang -> new File(
          Config.get("problems.root", "problems"),
          run.problem.alias + "/validator." + lang)
        )
      }).find(_._2.exists) match {
        case Some((lang, validator)) => {
          debug("Using custom validator {} for problem {}",
                validator.getCanonicalPath,
                run.problem.alias)
          validatorLang = Some(lang)
          validatorCode = Some(List(("Main." + lang, FileUtil.read(validator))))
        }

        case _ => {
          throw new FileNotFoundException("Validator for problem " + run.problem.alias +
                                          " was set to 'custom', but no validator program" +
                                          " was found.")
        }
      }
    } else {
      debug("Using {} validator for problem {}", run.problem.validator, run.problem.alias)
    }

    val codes = new ListBuffer[(String,String)]
    val interactiveRoot = new File(
      Config.get("problems.root", "problems"),
      run.problem.alias + "/interactive"
    )
    var interactive: Option[InteractiveDescription] = None

    if (interactiveRoot.isDirectory) {
      debug("Using interactive mode problem {}", run.problem.alias)

      val interactiveFiles = interactiveRoot
        .list
        .map(new File(interactiveRoot, _))
        .filter(_.isFile)

      val idlFile = interactiveFiles.find(_.getName.endsWith(".idl"))

      if (!idlFile.isEmpty) {
        val idlSource = FileUtil.read(idlFile.get)
        val parser = new Parser
        val parsedIdl = parser.parse(idlSource)
        val mainFile = interactiveFiles.find(
            _.getName.startsWith(parsedIdl.main.name + "."))

        if (!mainFile.isEmpty) {
          interactive = Some(InteractiveDescription(
            FileUtil.read(idlFile.get),
            parentLang = FileUtil.extension(mainFile.get),
            childLang = run.language.toString,
            moduleName = FileUtil.removeExtension(idlFile.get)
          ))

          codes += s"${interactive.get.moduleName}.${run.language.toString}" -> code
          codes += mainFile.get.getName -> FileUtil.read(mainFile.get)
        } else {
          throw new FileNotFoundException(
            new File(interactiveRoot, parsedIdl.main.name + ".*").getCanonicalPath)
        }
      } else {
        val unitNameFile = new File(interactiveRoot, "unitname")
        if (!unitNameFile.isFile) {
          throw new FileNotFoundException(unitNameFile.getCanonicalPath)
        }

        val langDir = new File(interactiveRoot, run.language.toString)
        if (!langDir.isDirectory) {
          throw new FileNotFoundException(langDir.getCanonicalPath)
        }

        langDir
          .list
          .map(new File(langDir, _))
          .filter(_.isFile)
          .foreach(file => { codes += file.getName -> FileUtil.read(file) })

        val unitName = FileUtil.read(unitNameFile)
        codes += unitName + "." + run.language.toString -> code
    
        if (codes.size < 2) {
          throw new FileNotFoundException(langDir.getCanonicalPath)
        }
      }
    } else {
      codes += "Main." + run.language.toString -> code
    }

    new CompileInputMessage(run.language.toString,
                            codes.result,
                            validatorLang,
                            validatorCode,
                            interactive)
  }
}
