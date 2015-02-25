import spray.json._

import java.nio.file.Path
import java.nio.file.Paths

import com.omegaup.data._
import com.omegaup.data.OmegaUpProtocol._
import com.omegaup.libinteractive.idl._
import com.omegaup.libinteractive.target._

import org.scalatest.FlatSpec
import org.scalatest.Matchers

case class RandomObject(identifier: Int, name: String)

object TestProtocol extends DefaultJsonProtocol {
	implicit val randomObjectProtocol = jsonFormat2(RandomObject)
}
import TestProtocol._

class JsonSpec extends FlatSpec with Matchers {

	"A random object" should "be automatically serializable" in {
		val obj = new RandomObject(5, "Hello, World!")

		val json = Serialization.writeString(obj)
		json should equal ("""{"identifier":5,"name":"Hello, World!"}""")
		obj should equal (Serialization.readString[RandomObject](json))

		an [spray.json.DeserializationException] should be thrownBy {
			Serialization.readString[RandomObject]("""{"identifier":"bar"}""")
		}
		an [spray.json.DeserializationException] should be thrownBy {
			Serialization.readString[RandomObject]("""{}""")
		}
		an [spray.json.JsonParser.ParsingException] should be thrownBy {
			Serialization.readString[RandomObject]("""/""")
		}
	}

	"GroupVerdictMessage lists" should "be parseable" in {
		val serialized = """[{"group":"0","cases":[{"name":"0","verdict":"AC","score":1.0}],"score":1.0},{"group":"1","cases":[{"name":"1","verdict":"AC","score":1.0}],"score":1.0}]"""
		val deserialized = Serialization.readString[List[GroupVerdictMessage]](serialized)
		deserialized.length should equal (2)
	}
}

/* vim: set noexpandtab: */
