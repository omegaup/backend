import net.liftweb.json.Serialization
import net.liftweb.json.parse

import com.omegaup._
import com.omegaup.data.OmegaUpSerialization

import org.scalatest.FlatSpec
import org.scalatest.Matchers

class ConfigSpec extends FlatSpec with Matchers {
  "Empty dict" should "parse correctly" in {
    val config = Config()
    val newConfig = ConfigMerge(config, parse("""{}"""))
    newConfig should be (config)
  }

  "Partial dict" should "replace values" in {
    val config = Config()
    val newConfig = ConfigMerge(config, parse("""{"broadcaster":{"port":1}}"""))
    newConfig should not be (config)
    newConfig.broadcaster.port should be (1)
  }

  "Router config" should "be supported" in {
    val config = Config()
    val newConfig = ConfigMerge(config, parse("""
      {
        "grader":{
          "routing": {
            "registered_runners": ["omegaup-slow0"]
          }
        }
      }
    """))
    newConfig should not be (config)
    newConfig.grader.routing.registered_runners should be (List("omegaup-slow0"))
  }
}
