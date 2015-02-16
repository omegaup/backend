import net.liftweb.json.Serialization

import java.nio.file.Path
import java.nio.file.Paths

import com.omegaup.data._
import com.omegaup.libinteractive.idl._
import com.omegaup.libinteractive.target._

import org.scalatest.FlatSpec
import org.scalatest.Matchers
 
case class RandomObject(identifier: Int, name: String)

class JsonSpec extends FlatSpec with Matchers {

	"A random object" should "be automatically serializable" in {
		implicit val formats = OmegaUpSerialization.formats
		
		val obj = new RandomObject(5, "Hello, World!")
		
		val json = Serialization.write(obj)
		json should equal ("""{"identifier":5,"name":"Hello, World!"}""")
		obj should equal (Serialization.read[RandomObject](json))
		
		an [net.liftweb.json.MappingException] should be thrownBy {
      Serialization.read[RandomObject]("""{"identifier":"bar"}""")
    }
		an [net.liftweb.json.MappingException] should be thrownBy {
      Serialization.read[RandomObject]("""{}""")
    }
		an [net.liftweb.json.JsonParser.ParseException] should be thrownBy {
      Serialization.read[RandomObject]("""/""")
    }
	}

  "GroupVerdictMessage lists" should "be parseable" in {
		implicit val formats = OmegaUpSerialization.formats

    val serialized = """[{"group":"0","cases":[{"name":"0","verdict":"AC","score":1.0}],"score":1.0},{"group":"1","cases":[{"name":"1","verdict":"AC","score":1.0}],"score":1.0}]"""
    val deserialized = Serialization.read[List[GroupVerdictMessage]](serialized)
    deserialized.length should equal (2)
  }
}
