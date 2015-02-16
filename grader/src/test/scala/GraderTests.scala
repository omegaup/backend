import com.omegaup.Config
import com.omegaup.Database
import com.omegaup.FileUtil
import com.omegaup.Logging
import com.omegaup.Service
import com.omegaup.data._
import com.omegaup.grader.Grader
import com.omegaup.grader.GraderOptions

import Language._

import scala.collection.mutable._
import org.scalatest.{FlatSpec, BeforeAndAfterAll}
import org.scalatest.Matchers

class GraderSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  private var grader: Grader = null

  override def beforeAll() {
    import java.io._
    import java.util.zip._

    val root = new File("test-env")
    if (root.exists()) {
      FileUtil.deleteDirectory(root)
    }
    root.mkdir()

    // populate temp database for problems and contests
    Config.set("db.driver", "org.h2.Driver")
    Config.set(
      "db.url",
      "jdbc:h2:file:" + root.getCanonicalPath + "/omegaup"
    )
    Config.set("db.user", "sa")
    Config.set("db.password", "")

    Config.set("ssl.keystore", "grader/omegaup.jks")
    Config.set("grader.standalone", "true")
    Config.set("grader.runner.timeout", "10")
    Config.set("grader.port", "21681")
    Config.set("grader.embedded_runner.enable", "true")
    Config.set("grader.scoreboard_refresh.enable", "false")
    Config.set("grader.root", root.getCanonicalPath + "/grader")
    Config.set("runner.sandbox.path", new File("../sandbox").getCanonicalPath)
    Config.set("runner.minijail.path", "/var/lib/minijail")
    Config.set(
      "runner.sandbox.profiles.path",
      new File("grader/src/test/resources/sandbox-profiles").getCanonicalPath
    )
    Config.set("submissions.root", root.getCanonicalPath + "/submissions")
    for (i <- 0 until 256) {
      new File(root, f"submissions/$i%02x").mkdirs
    }
    Config.set("problems.root", root.getCanonicalPath + "/problems")
    Config.set("compile.root", root.getCanonicalPath + "/compile")
    Config.set("input.root", root.getCanonicalPath + "/input")
    Config.set("runner.sandbox", "minijail")
    Config.set("runner.preserve", "true")
    Config.set("logging.level", "debug")
    Config.set("logging.file", "")

    Logging.init

    val input = new ZipInputStream(new FileInputStream("grader/src/test/resources/omegaup-base.zip"))
    var entry: ZipEntry = input.getNextEntry
    val buffer = Array.ofDim[Byte](1024)
    var read: Int = 0

    while(entry != null) {
      val outFile = new File(root.getCanonicalPath + "/" + entry.getName)

      if(entry.getName.endsWith("/")) {
        outFile.mkdirs()
      } else {
        val output = new FileOutputStream(outFile)
        while( { read = input.read(buffer); read > 0 } ) {
          output.write(buffer, 0, read)
        }
        output.close
      }

      input.closeEntry
      entry = input.getNextEntry
    }

    input.close

    Class.forName(Config.get("db.driver", "org.h2.Driver"))
    implicit val conn = java.sql.DriverManager.getConnection(
      Config.get("db.url", "jdbc:h2:file:omegaup"),
      Config.get("db.user", "omegaup"),
      Config.get("db.password", "")
    )
    try {
      FileUtil.read("grader/src/main/resources/h2.sql").split("\n\n").foreach { Database.execute(_) }
      FileUtil.read("grader/src/test/resources/h2.sql").split("\n\n").foreach { Database.execute(_) }
    } finally {
      conn.close
    }

    grader = new Grader(new GraderOptions)
    grader.start
  }

  override def afterAll() {
    grader.stop
    grader.join
  }

  "Grader" should "work end-to-end" in {
    val tests = new ListBuffer[Run => Unit]
    tests += null
    implicit val conn = grader.conn

    val lock = new Object
    var ready = false
    var exception: Exception = null

    grader.addListener {
      (ctx, run) => {
        try {
          tests(run.id.toInt)(run)
        } catch {
          case e: Exception => { exception = e }
        }
        lock.synchronized {
          ready = true
          lock.notify
        }
      }
    }

    def omegaUpSubmit(problem: String, language: Language, code: String, contest: Option[String] = None)(test: (Run) => Unit) = {
      val submit_id = Service.runNew(RunNewInputMessage(
        problem = problem,
        language = language.toString,
        code = code,
        contest = contest
      )).id

      ready = false
      tests += test
      grader.grade(new RunGradeInputMessage(id = List(submit_id.get)))

      lock.synchronized {
        if (!ready) {
          lock.wait(10000)
        }
      }

      if (!ready) {
        throw new RuntimeException("Didn't finish")
      }
      if (exception != null) throw exception
    }

    omegaUpSubmit("HELLO", Language.Cpp, """
      int main() {
        while(true);
      }
    """) { run => {
      run.status should equal (Status.Ready)
      run.verdict should equal (Verdict.TimeLimitExceeded)
      run.score should equal (0)
      run.contest_score should equal (None)
    }}

  // Contest submission disabled.
  /*
  omegaUpSubmit("HELLO", Language.Cpp, """
    #include <cstdlib>
    #include <iostream>
    #include <map>
    #include <unistd.h>

    using namespace std;

    int main(int argc, char *argv[]) {
      int a, b;
      cin >> a >> b;
      cout << "Hello, World!" << endl;
      cout << a + b << endl;

      return EXIT_SUCCESS;
    }
  """, contest = Some(1)) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.Accepted)
    run.score should equal (1)
    run.contest_score should equal (Some(100))
  }}
  */

  omegaUpSubmit("HELLO", Language.Cpp, """
    #include <cstdlib>
    #include <iostream>
    #include <map>
    #include <unistd.h>

    using namespace std;

    int main(int argc, char *argv[]) {
      int a, b;
      cin >> a >> b;
      cout << "Hello, World!" << endl;
      cout << 3 << endl;

      return EXIT_SUCCESS;
    }
  """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.PartialAccepted)
    run.score should equal (0.5)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO", Language.Literal, "") { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.WrongAnswer)
    run.score should equal (0.0)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO", Language.Literal,
    """data:application/x-zip;base64,
    UEsDBAoAAAAAAKis/kI1yT8dEAAAABAAAAAGABwAMDAub3V0VVQJAAN7lPhRe5T4UXV4CwABBOgD
    AAAE6AMAAEhlbGxvLCBXb3JsZCEKMwpQSwMEFAAAAAgAqaz+Qrbi9zMUAAAAFgAAAAYAHAAwMS5v
    dXRVVAkAA32U+FF9lPhRdXgLAAEE6AMAAAToAwAA80jNycnXUQjPL8pJUeQyMgADLgBQSwECHgMK
    AAAAAACorP5CNck/HRAAAAAQAAAABgAYAAAAAAABAAAApIEAAAAAMDAub3V0VVQFAAN7lPhRdXgL
    AAEE6AMAAAToAwAAUEsBAh4DFAAAAAgAqaz+Qrbi9zMUAAAAFgAAAAYAGAAAAAAAAQAAAKSBUAAA
    ADAxLm91dFVUBQADfZT4UXV4CwABBOgDAAAE6AMAAFBLBQYAAAAAAgACAJgAAACkAAAAAAA=
  """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.Accepted)
    run.score should equal (1.0)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO", Language.Literal,
    """data:application/x-zip;base64,
    UEsDBAoAAAAAAKis/kI1yT8dEAAAABAAAAAGABwAMDAub3V0VVQJAAN7lPhRe5T4UXV4CwABBOgD
    AAAE6AMAAEhlbGxvLCBXb3JsZCEKMwpQSwMECgAAAAAASwb/QvaaEjYQAAAAEAAAAAYAHAAwMS5v
    dXRVVAkAA73B+FGGlPhRdXgLAAEE6AMAAAToAwAASGVsbG8sIFdvcmxkIQowClBLAQIeAwoAAAAA
    AKis/kI1yT8dEAAAABAAAAAGABgAAAAAAAEAAACkgQAAAAAwMC5vdXRVVAUAA3uU+FF1eAsAAQTo
    AwAABOgDAABQSwECHgMKAAAAAABLBv9C9poSNhAAAAAQAAAABgAYAAAAAAABAAAApIFQAAAAMDEu
    b3V0VVQFAAO9wfhRdXgLAAEE6AMAAAToAwAAUEsFBgAAAAACAAIAmAAAAKAAAAAAAA==
  """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.PartialAccepted)
    run.score should equal (0.5)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO2", Language.Cpp, """
    #include <cstdlib>
    #include <iostream>
    #include <map>
    #include <unistd.h>

    using namespace std;

    int main(int argc, char *argv[]) {
      int a, b;
      cin >> a >> b;
      cout << a + b << endl;

      return EXIT_SUCCESS;
    }
  """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.Accepted)
    run.score should equal (1)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO2", Language.Cpp, """
    #include <cstdlib>
    #include <iostream>
    #include <map>
    #include <unistd.h>

    using namespace std;

    int main(int argc, char *argv[]) {
      int a, b;
      cin >> a >> b;
      cout << 3 << endl;

      return EXIT_SUCCESS;
    }
  """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.PartialAccepted)
    run.score should be (0.2 +- 0.001)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO3", Language.Cpp, """
    #include <cstdlib>
    #include <iostream>
    #include <map>
    #include <unistd.h>

    using namespace std;

    int main(int argc, char *argv[]) {
      int a, b;
      cin >> a >> b;
      cout << a + b << endl;

      return EXIT_SUCCESS;
    }
  """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.Accepted)
    run.score should equal (1)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO3", Language.Cpp, """
    #include <cstdlib>
    #include <iostream>
    #include <map>
    #include <unistd.h>

    using namespace std;

    int main(int argc, char *argv[]) {
      int a, b;
      cin >> a >> b;
      cout << 3 << endl;

      return EXIT_SUCCESS;
    }
  """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.PartialAccepted)
    run.score should be (0.05 +- 0.001)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO4", Language.Cpp, """
    #include <cstdlib>
    #include <iostream>
    #include <map>
    #include <unistd.h>

    using namespace std;

    int main(int argc, char *argv[]) {
      double a, b;
      cin >> a >> b;
      cout << a + b << endl;

      return EXIT_SUCCESS;
    }
  """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.PartialAccepted)
    run.score should be (0.71 +- 0.01)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO4", Language.Cpp, """
    #include <cstdlib>
    #include <iostream>
    #include <map>
    #include <unistd.h>

    using namespace std;

    int main(int argc, char *argv[]) {
      double a, b;
      cin >> a >> b;
      cout << a*a + b*b << endl;

      return EXIT_SUCCESS;
    }
    """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.Accepted)
    run.score should equal (1)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO5", Language.Cpp, """
    #include "solve.h"

    long long solve(long long a, long long b) { return a + b; }
    """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.Accepted)
    run.score should equal (1)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO5", Language.Cpp, """
    long long solve(long long a, long long b) { return 0; }
  """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.WrongAnswer)
    run.score should equal (0)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("HELLO5", Language.Cpp, """
    #include <stdio.h>
    int main() { printf("Hello, World!\n3\n"); }
  """) { run => {
    run.status should equal (Status.Ready)
    run.verdict should equal (Verdict.CompileError)
    run.score should equal (0)
    run.contest_score should equal (None)
  }}

  omegaUpSubmit("KAREL", Language.KarelJava, """
    class program {
      void turn(n) { iterate(n) turnleft(); }

      void avanza()
      {
        if (frontIsClear)
          {
          move();
        }
        else
          {
          turn(2);
          while(frontIsClear)
            move();
          turn(3);
          if (frontIsClear)
            {
            move();
            turn(3);
          }
        }
      }

      void recur(n)
      {
        if (notFacingNorth)
          {
          recuerdame(n);
          regresa();
        }
        else
          {
          turn(3);
          while(frontIsClear)
            {
            move();
          }
          turn(2);
        }
      }

      void regresa()
      {
        if(frontIsClear)
          {
          move();
        }
        else
          {
          turn(1);
          move();
          turnleft();
          while(frontIsClear)
            move();
          turn(2);
        }
      }

      void crece(n)
      {
        if(!iszero(n))
          {
          iterate(4)
          {
            if (frontIsClear)
              {
              move();
              if (notNextToABeeper)
                {
                putbeeper();
              }
              crece(pred(n));
              turn(2);
              move();
              turn(2);
            }
            turnleft();
          }
        }
      }

      void recuerdame(n)
      {
        if (nextToABeeper)
          {
          avanza();
          recur(n);
          crece(n);
        }
        else
          {
          avanza();
          recur(n);
        }
      }

      void cuenta(n)
      {
        if (nextToABeeper)
          {
          pickbeeper();
          cuenta(succ(n));
        }
        else
          {
          while(notFacingEast)
            turnleft();
          recuerdame(n);
        }
      }

      void recoge()
      {
        if (notFacingNorth)
          {
          avanza();
          if (nextToABeeper)
            {
            pickbeeper();
            recoge();
            putbeeper();
          }
          else
            {
            recoge();
          }
        }else
        {
          turn(2);
          while(frontIsClear)
            move();
        }
      }

      program() {
        cuenta(0);
        while(notFacingEast)
          turnleft();
        recoge();
        turnoff();
      }

    }
    """) { run => {
      run.status should equal (Status.Ready)
      run.verdict should equal (Verdict.Accepted)
      run.score should equal (1)
      run.contest_score should equal (None)
    }}
  }
}
