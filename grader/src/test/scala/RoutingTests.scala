import com.omegaup.Logging
import com.omegaup.grader._
import com.omegaup.data._

import java.text.ParseException
import org.scalatest._
import org.scalatest.matchers._
import Matchers._

class RoutingSpec extends FreeSpec with ContextMixin with BeforeAndAfterAll {

  var router: RunRouter = null

  override def beforeAll() {
    config.set("logging.level", "off")
    Logging.init

    router = RoutingDescription.parse("""
      contest: problem == "problem";
      urgent: !rejudge && contest == "test_contest" && user in ["test_user"];
      practice: slow
    """)
  }

	"Routing descriptions" - {
    "should fail parsing with ParseException" in {
      a [ParseException] should be thrownBy RoutingDescription.parse("in")
    }
  }

  "Router" - {
    "should send normal runs to the normal queue" in {
      router(
        new RunContext(ctx, null, new Run(
          contest = Some(new Contest(alias = "foo")),
          problem = new Problem(),
          user = Some(new User(username = "foo"))
        ), false, false)
      ) should equal (2)
    }

    "should send to normal queue since username does not match" in {
      router(
        new RunContext(ctx, null, new Run(
          contest = Some(new Contest(alias = "test_contest")),
          problem = new Problem(),
          user = Some(new User(username = "foo"))
        ), false, false)
      ) should equal (2)
    }

    "should send to urgent queue since both username and contest match" in {
      router(
        new RunContext(ctx, null, new Run(
          contest = Some(new Contest(alias = "test_contest")),
          problem = new Problem(),
          user = Some(new User(username = "test_user"))
        ), false, false)
      ) should equal (0)
    }

    "should send to rejudge queue even if username and contest match" in {
      router(
        new RunContext(ctx, null, new Run(
          contest = Some(new Contest(alias = "test_contest")),
          problem = new Problem(),
          user = Some(new User(username = "test_user"))
        ), false, true)
      ) should equal (6)
    }

    "should send to the slow branch of the urgent queue for slow runs" in {
      router(
        new RunContext(ctx, null, new Run(
          contest = Some(new Contest(alias = "test_contest")),
          problem = new Problem(slow = true),
          user = Some(new User(username = "test_user"))
        ), false, false)
      ) should equal (1)
    }

    "should send to the slow branch of the normal queue for normal slow runs" in {
      router(
        new RunContext(ctx, null, new Run(
          contest = Some(new Contest(alias = "test_contest")),
          problem = new Problem(alias = "problem", slow = true),
          user = Some(new User(username = "test_user"))
        ), false, false)
      ) should equal (3)
    }
	}
}
