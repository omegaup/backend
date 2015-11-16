import com.omegaup._
import com.omegaup.data.OmegaUpProtocol._

import org.scalatest.FlatSpec
import org.scalatest.Matchers

class ConfigSpec extends FlatSpec with Matchers {
	"Empty dict" should "parse correctly" in {
		val config = Config()
		val newConfig = ConfigMerge(config, """{}""")
		newConfig should be (config)
	}

	"Partial dict" should "replace values" in {
		val config = Config()
		val newConfig = ConfigMerge(config, """{"broadcaster":{"port":1}}""")
		newConfig should not be (config)
		newConfig.broadcaster.port should be (1)
	}

	"Router config" should "be supported" in {
		val config = Config()
		val newConfig = ConfigMerge(config, """
		{
			"grader":{
				"routing": {
					"registered_runners": ["omegaup-slow0"]
				}
			}
		}
		""")
		newConfig should not be (config)
		newConfig.grader.routing.registered_runners should be (List("omegaup-slow0"))
	}

	"Contestant list" should "be supported" in {
		val config = Config()
		val newConfig = ConfigMerge(config, """
		{
			"proxy": {
				"contestants": [
					"foo:bar"
				]
			}
		}
		""")
		newConfig should not be (config)
		newConfig.proxy.contestants should be (List("foo:bar"))
	}
}

/* vim: set noexpandtab: */
