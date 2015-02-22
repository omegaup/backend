import com.omegaup.Config
import com.omegaup.Context
import com.omegaup.OverrideLogger

import org.scalatest.Alerting
import org.scalatest.Args
import org.scalatest.FailedStatus
import org.scalatest.Status
import org.scalatest.Suite
import org.scalatest.SuiteMixin

trait ContextMixin extends SuiteMixin with Alerting { this: Suite =>
  protected val config = new Config
  protected implicit var ctx: Context = new Context(config)

  abstract override def runTest(testName: String, args: Args): Status = {
    var thrownException: Option[Throwable] = None
    var status: Option[Status] = None
    val overrideLogger = new OverrideLogger("debug")
    overrideLogger.start
    ctx = new Context(config, overrideLogger)

    try {
      status = Some(super.runTest(testName, args))
      status.get
    }
    catch {
      case e: Exception => {
        thrownException = Some(e)
        status = Some(FailedStatus)
        status.get
      }
    }
    finally {
      overrideLogger.stop
      status match {
        case Some(FailedStatus) =>
          alert(overrideLogger.toString)
        case _ => ()
      }
      thrownException match {
        case Some(e) => throw e
        case None => ()
      }
    }
  }
}
