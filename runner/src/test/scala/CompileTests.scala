import java.io._

import com.omegaup._
import com.omegaup.data._
import com.omegaup.runner._
import com.omegaup.libinteractive.idl.Parser
import com.omegaup.libinteractive.target.Options
import com.omegaup.libinteractive.target.Command
import org.slf4j._

import org.scalatest.{FlatSpec, BeforeAndAfterAll}
import org.scalatest.Matchers
import org.scalatest.matchers.BeMatcher
import org.scalatest.matchers.MatchResult

case class CaseResult(name: String, status: String, output: Option[String],
  score: Option[Double])

class OKMatcher(output: String) extends BeMatcher[CaseResult] with Matchers {
  def apply(result: CaseResult) =
    MatchResult(
      result.status == "OK" && result.output.getOrElse("") == output,
      "for case `" + result.name + "', status was " + result.status + " and " + output + " was " + result.output.getOrElse("(None)"),
      "for case `" + result.name + "' status was " + result.status + " and expecting `" + output + "', got `" + result.output.getOrElse("(None)") + "'"
    )
}

class StatusMatcher(status: String) extends BeMatcher[CaseResult] with Matchers {
  def apply(result: CaseResult) =
    MatchResult(
      result.status == status,
      "for case `" + result.name + "', " + status + " was " + result.status,
      "for case `" + result.name + "' expecting " + status + ", got " + result.status
    )
}

class ScoreMatcher(score: Double) extends BeMatcher[CaseResult] with Matchers {
  def apply(result: CaseResult) =
    MatchResult(
      !result.score.isEmpty && approximately(result.score.get, score),
      "for case `" + result.name + "', " + score + " was " + result.score,
      "for case `" + result.name + "' expecting " + score + ", got " + result.score
    )

  def approximately(a: Double, b: Double) =
    Math.abs(a - b) <= Math.abs(1e-4 * a)
}

trait CaseMatchers {
  def OK(output: String): BeMatcher[CaseResult] = new OKMatcher(output)
  val JudgeError = new StatusMatcher("JE")
  val MemoryLimitExceeded = new StatusMatcher("ML")
  val OutputLimitExceeded = new StatusMatcher("OL")
  val RestrictedFunction = new StatusMatcher("FO")
  val RuntimeError = new StatusMatcher("RE")
  val Signal = new StatusMatcher("SG")
  val TimeLimitExceeded = new StatusMatcher("TO")
  def Scored(score: Double): BeMatcher[CaseResult] = new ScoreMatcher(score)
}

class CallbackListener(expectations: Map[String, CaseResult => Unit])
    extends Object with RunCaseCallback with Matchers {
  var output: Option[String] = None

  def apply(filename: String, length: Long, stream: InputStream): Unit = {
    val caseName = FileUtil.removeExtension(filename)
    expectations should contain key (caseName)

    if (filename.endsWith(".out")) {
      output = Some(FileUtil.read(stream))
    } else if (filename.endsWith(".meta")) {
      val meta = MetaFile.load(new InputStreamReader(stream))
      val score = (if (meta.contains("score")) {
        Some(meta("score").toDouble)
      } else {
        None
      })
      expectations(caseName)(CaseResult(caseName, meta("status"), output, score))
      output = None
    }
  }
}

object NullRunCaseCallback extends Object with RunCaseCallback {
  def apply(filename: String, length: Long, stream: InputStream): Unit = {}
}

class CompileSpec extends FlatSpec with Matchers with CaseMatchers
    with BeforeAndAfterAll {
  private implicit var ctx: Context = null

  override def beforeAll() = {
    import java.util.zip._

    val root = new File("test-env")

    if (root.exists()) {
      FileUtil.deleteDirectory(root.getCanonicalPath)
    }

    root.mkdir()
    new File(root.getCanonicalPath + "/compile").mkdir()

    ctx = new Context
    ctx.config.set("runner.preserve", true)
    ctx.config.set("compile.root", root.getCanonicalPath + "/compile")
    ctx.config.set("runner.sandbox.path", new File("../sandbox").getCanonicalPath)
    ctx.config.set("runner.minijail.path", new File("/var/lib/minijail").getCanonicalPath)
    ctx.config.set("logging.level", "debug")

    Logging.init
  }

  def compileTest(message: CompileInputMessage) (expectations: CompileOutputMessage => Unit) (implicit runner: Runner) = {
    val compileOutput = runner.compile(message)
    expectations(compileOutput)
  }

  def runTest(message: CompileInputMessage,
      cases: ((String, String), CaseResult => Unit)*) (implicit runner: Runner) = {
    val compileOutput = runner.compile(message)

    compileOutput.status should equal ("ok")
    compileOutput.token should not equal None

    runner.run(RunInputMessage(compileOutput.token.get, cases = Some(
      cases.map(c => new CaseData(c._1._1, c._1._2)).toList
    )), new CallbackListener(
      cases.map(c => c._1._1 -> c._2).toMap
    ))
  }

  def runTestWithCustomMessage(message: CompileInputMessage,
      runInputMessage: RunInputMessage,
      cases: ((String, String), CaseResult => Unit)*) (implicit runner: Runner) = {
    val compileOutput = runner.compile(message)

    compileOutput.status should equal ("ok")
    compileOutput.token should not equal None

    runner.run(runInputMessage.copy(
      token = compileOutput.token.get,
      cases = Some(cases.map(c => new CaseData(c._1._1, c._1._2)).toList)
    ), new CallbackListener(
      cases.map(c => c._1._1 -> c._2).toMap
    ))
  }

  "Compile error" should "be correctly handled" in {
    implicit val runner = new Runner("test", Minijail)

    compileTest(CompileInputMessage("c", List(("Main.c", "foo")))) {
      _.status should equal ("compile error")
    }
    
    compileTest(CompileInputMessage("c", List(("Main.c", "#include<stdio.h>")))) {
      _.status should equal ("compile error")
    }
    
    compileTest(CompileInputMessage("c", List(("Main.c", "#include</dev/random>")))) {
      _.status should equal ("compile error")
    }
    
    compileTest(CompileInputMessage("cpp", List(("Main.cpp", "foo")))) {
      _.status should equal ("compile error")
    }
    
    compileTest(CompileInputMessage("cpp", List(("Main.cpp", "#include<stdio.h>")))) {
      _.status should equal ("compile error")
    }
    
    compileTest(CompileInputMessage("cpp", List(("Main.cpp", "#include</dev/random>")))) {
      _.status should equal ("compile error")
    }
    
    compileTest(CompileInputMessage("java", List(("Main.java", "foo")))) { test => {
      test.status should equal ("compile error")
      test.error should not equal (Some("Class should be called \"Main\"."))
    }}
    
    compileTest(CompileInputMessage("java", List(("Main.java", """
      class Foo {
        public static void main(String[] args) {
          System.out.println("Hello, World!\n");
        }
      }
    """)))) { test => {
      test.status should equal ("compile error")
      test.error should equal (Some("Class should be called \"Main\"."))
    }}

    compileTest(CompileInputMessage("kj", List(("Main.kj", "foo")))) {
      _.status should equal ("compile error")
    }

    compileTest(CompileInputMessage("kj", List(("Main.kj", """
      class program {
        program() {
          while(notFacingEast) turnleft();
          pickbeeper();
          turnoff();
        }
      }
    """)))) {
      _.status should equal ("ok")
    }
  }
  
  "OK" should "be correctly handled" in {
    implicit val runner = new Runner("test", Minijail)

    runTest(
      CompileInputMessage("c", List(("Main.c", """
        #include<stdio.h>
        #include<stdlib.h>
        int main() {
          int x;
          (void)scanf("%d", &x);
          switch (x) {
            case 0:
              printf("Hello, World!\n");
              break;
            case 1:
              while(1);
              break;
            case 2:
              fork();
              break;
            case 3:
              while(1) {
                void* mem = malloc(1024*1024);
                memset(mem, -1, 1024 * 1024);
              }
              break;
            case 4:
              while(1) printf("trololololo\n");
              break;
            case 5:
              printf("%d", *(int*)(x-6));
              break;
            case 6:
              printf("%d", 1/(x-6));
              break;
            case 7:
              return 1;
          }
          return 0;
        }
      """))),
      ("ok", "0") -> { _ should be (OK("Hello, World!")) },
      ("tle", "1") -> { _ should be (TimeLimitExceeded) },
      ("rfe", "2") -> { _ should be (RestrictedFunction) },
      ("mle", "3") -> { _ should be (MemoryLimitExceeded) },
      ("ole", "4") -> { _ should be (OutputLimitExceeded) },
      ("segfault", "5") -> { _ should be (Signal) },
      ("zerodiv", "6") -> { _ should be (RuntimeError) }
    )
   
    runTest(
      CompileInputMessage("cpp", List(("Main.cpp", """
        #include<iostream>
        #include<stdlib.h>
        #include<cstring>
        #include<unistd.h>
        using namespace std;
        int main() {
          int x;
          cin >> x;
          switch (x) {
            case 0:
              cout << "Hello, World!" << endl;
              break;
            case 1:
              while(1);
              break;
            case 2:
              fork();
              break;
            case 3:
              while(1) {
                void* mem = malloc(1024*1024);
                memset(mem, -1, 1024 * 1024);
              }
              break;
            case 4:
              while(1) cout << "trololololo" << endl;
              break;
            case 5:
              cout << *reinterpret_cast<int*>(x-5) << endl;
              break;
            case 6:
              cout << 1/(x-6) << endl;
              break;
            case 8:
              return 1;
          }
          return 0;
        }
      """))),
      ("ok", "0") -> { _ should be (OK("Hello, World!")) },
      ("tle", "1") -> { _ should be (TimeLimitExceeded) },
      ("rfe", "2") -> { _ should be (RestrictedFunction) },
      ("mle", "3") -> { _ should be (MemoryLimitExceeded) },
      ("ole", "4") -> { _ should be (OutputLimitExceeded) },
      ("segfault", "5") -> { _ should be (Signal) },
      ("zerodiv", "6") -> { _ should be (RuntimeError) }
    )
    
    runTest(
      CompileInputMessage("java", List(("Main.java", """
        import java.io.*;
        import java.util.*;
        class Main {
          public static void main(String[] args) throws Exception {
            Scanner in = new Scanner(System.in);
            List l = new ArrayList();
            switch (in.nextInt()){
              case 0:
                System.out.println("Hello, World!\n");
                break;
              case 1:
                while(true) {}
              case 2:
                Runtime.getRuntime().exec("/bin/ls").waitFor();
                break;
              case 3:
                while(true) { l.add(new ArrayList(1024*1024)); }
              case 4:
                while(true) { System.out.println("trololololo"); }
              case 5:
                System.out.println(l.get(0));
                break;
              case 6:
                System.out.println(1 / (int)(Math.sin(0.1)));
                break;
              case 7:
                System.exit(1);
                break;
            }
          }
        }
      """))),
      ("ok", "0") -> { _ should be (OK("Hello, World!")) },
      ("tle", "1") -> { _ should be (TimeLimitExceeded) },
      // ("rfe", "2") -> { _ should be (RestrictedFunction) },
      // ("mle", "3") -> { _ should be (MemoryLimitExceeded) },
      // ("ole", "4") -> { _ should be (OutputLimitExceeded) },
      ("segfault", "5") -> { _ should be (RuntimeError) },
      ("zerodiv", "6") -> { _ should be (RuntimeError) }
    )

    runTestWithCustomMessage(
      CompileInputMessage("java", List(("Main.java", """
        import java.io.*;
        import java.util.*;
        class Main {
          public static void main(String[] args) throws Exception {
            Thread.sleep(700);
            System.out.println("OK");
          }
        }
      """))),
      RunInputMessage(null, overallWallTimeLimit = 1000L),
      ("ok", "") -> { _ should be (OK("OK")) },
      ("tle", "") -> { _ should be (TimeLimitExceeded) }
    )
  }

  "Exploits" should "be handled" in {
    implicit val runner = new Runner("test", Minijail)

    // x86 forkbomb
    runTest(
      CompileInputMessage("cpp", List(("Main.cpp", """
        int main() { (*(void (*)())"\x6a\x02\x58\xcd\x80\xeb\xf9")(); }
      """))),
      ("x86_forkbomb", "") -> { r: CaseResult => r should be (RestrictedFunction) }
    )

    // x86_64 forkbomb
    runTest(
      CompileInputMessage("cpp", List(("Main.cpp", """
        int main() { (*(void (*)())"\x48\x31\xc0\xb0\x39\xcd\x80\xeb\xfa")(); }
      """))),
      ("x86_64_forkbomb", "") -> { r: CaseResult => r should be (RestrictedFunction) }
    )

    // Java6 parse double bug in compiler: CVE-2010-4476
    compileTest(CompileInputMessage("java", List(("Main.java", """
      class Main {
        public static void main(String[] args) {
          double d = 2.2250738585072012e-308;
          System.out.println("Value: " + d);
        }
      }
    """)))) {
      _.status should equal ("ok")
    }

    // Java6 parse double bug in runtime: CVE-2010-4476
    runTest(
      CompileInputMessage("java", List(("Main.java", """
        class Main {
          public static void main(String[] args) {
            double d = Double.parseDouble("2.2250738585072012e-308");
            System.out.println("Value: " + d);
          }
        }
      """))),
      ("ok", "") -> { r: CaseResult => r should be (OK("Value: 2.2250738585072014E-308")) }
    )

    // 2^200 error messages
    compileTest(CompileInputMessage("c", List(("Main.c", """
      #include "Main.c"
      #include "Main.c"
    """)))) {
      _.error should not equal (None)
    }
  }

  "Validator" should "work" in {
    implicit val runner = new Runner("test", Minijail)

    compileTest(CompileInputMessage("c", List(("Main.c", """
      #include<stdio.h>
      #include<stdlib.h>
      int main() {
        printf("100\n");
        return 0;
      }
    """)), Some("c"))) {
      _.status should equal ("judge error")
    }

    compileTest(CompileInputMessage("c", List(("Main.c", """
      #include<stdio.h>
      #include<stdlib.h>
      int main() {
        printf("100\n");
        return 0;
      }
    """)), Some("c"), Some(List(("Main.c", "foo"))))) {
      _.status should equal ("judge error")
    }

    runTest(
      CompileInputMessage("c", List(("Main.c", """
        #include<stdio.h>
        #include<stdlib.h>
        int main() {
          printf("100\n");
          return 0;
        }
      """)), Some("c"), Some(List(("Main.c", """
        #include<stdio.h>
        #include<stdlib.h>
        int main() {
          printf("0\n");
          return 0;
        }
      """)))),
      ("zero", "0") -> { r: CaseResult => r should be (OK("100")) }
    )
    
    runTest(
      CompileInputMessage("c", List(("Main.c", """
        #include<stdio.h>
        #include<stdlib.h>
        int main() {
          printf("100\n");
          return 0;
        }
      """)), Some("c"), Some(List(("Main.c", """
        #include<stdio.h>
        #include<stdlib.h>
        int main() {
          printf("foo\n");
          return 0;
        }
      """)))),
      ("je", "0") -> { r: CaseResult => r should be (JudgeError) }
    )

    runTest(
      CompileInputMessage("c", List(("Main.c", """
        #include<stdio.h>
        #include<stdlib.h>
        int main() {
          double a, b; scanf("%lf %lf", &a, &b);
          printf("%lf\n", a + b);
          return 0;
        }
      """)), Some("c"), Some(List(("Main.c", """
        #include<stdio.h>
        #include<stdlib.h>
        int main() {
          FILE* data = fopen("data.in", "r");
          double a, b, answer, user;
          (void)fscanf(data, "%lf %lf", &a, &b);
          (void)scanf("%lf", &user);
          answer = a*a + b*b;
          printf("%lf\n", 1.0 / (1.0 + (answer - user) * (answer - user)));
          return 0;
        }
      """)))),
      ("one", "1 1\n") -> { _ should be (Scored(1.0)) },
      ("zero", "0 0\n") -> { _ should be (Scored(1.0)) },
      ("two", "2 2\n") -> { _ should be (Scored(0.058824)) },
      ("half", "0.5 0.5\n") -> { _ should be (Scored(0.8)) }
    )
    
    runTest(
      CompileInputMessage("cpp", List(("Main.cpp", """
        #include<iostream>
        int main() {
          double a, b;
          std::cin >> a >> b;
          std::cout << a*a + b*b << std::endl;
          return 0;
        }
      """)), Some("py"), Some(List(("Main.py", """
data = open("data.in", "r")
a, b = map(float, data.readline().strip().split())
user = float(raw_input().strip())
answer = a**2 + b**2
print 1.0 / (1.0 + (answer - user)**2)
      """)))),
      ("one", "1 1\n") -> { _ should be (Scored(1.0)) },
      ("zero", "0 0\n") -> { _ should be (Scored(1.0)) },
      ("two", "2 2\n") -> { _ should be (Scored(1.0)) },
      ("half", "0.5 0.5\n") -> { _ should be (Scored(1.0)) }
    )
  }

  "libinteractive" should "work" in {
    val parser = new Parser
    val runner = new Runner("test", Minijail)

    val interactive = InteractiveDescription(
      """
        interface Main {};
        interface summer {
          int summer(int a, int b);
        };
      """,
      parentLang = "cpp",
      childLang = "cpp",
      moduleName = "summer"
    )
    val idl = parser.parse(interactive.idlSource)
    val test1 = runner.compile(CompileInputMessage("cpp", List(("summer.cpp", """
      #include "summer.h"
      int summer(int a, int b) {
        return a + b;
      }
    """), ("Main.cpp", """
      #include <cstdio>
      #include "summer.h"
      using namespace std;
      int main() {
        int a, b;
        scanf("%d %d\n", &a, &b);
        printf("%d\n", summer(a, b));
      }
    """)), interactive = Some(interactive)))
    test1.status should equal ("ok")
    test1.token should not equal None

    runner.run(
      RunInputMessage(
        token = test1.token.get,
        cases= Some(List(
          new CaseData("three", "1 2\n")
        )),
        interactive = Some(InteractiveRuntimeDescription(
          main = idl.main.name,
          interfaces = idl.interfaces.map(_.name),
          parentLang = interactive.parentLang
        ))
      ),
      NullRunCaseCallback
    )
  }
}
