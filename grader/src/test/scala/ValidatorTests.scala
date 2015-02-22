import com.omegaup.Logging
import com.omegaup.grader._

import java.io._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FlatSpec
import org.scalatest.Matchers

class ValidatorSpec extends FlatSpec with Matchers with ContextMixin
		with BeforeAndAfterAll {

  override def beforeAll() {
    config.set("logging.level", "off")
    Logging.init
	}

	"Tokenizers" should "tokenize properly" in {
		var tok = new Tokenizer(new File("grader/src/test/resources/token_test.txt"),
				        !_.isWhitespace)

		tok.hasNext should equal (true)
		tok.next.toString should equal ("public")
		tok.hasNext should equal (true)
		tok.next.toString should equal ("static")
		tok.hasNext should equal (true)
		tok.next.toString should equal ("void")
		tok.hasNext should equal (true)
		tok.next.toString should equal ("1")
		tok.hasNext should equal (true)
		tok.next.toString should equal ("2.0")
		tok.hasNext should equal (true)
		tok.next.toString should equal ("-3.0.0")
		tok.hasNext should equal (true)
		tok.next.toString should equal ("1234567890123456789012345678901234567890." +
      "1234567890123456789012345678901234567890")
		tok.hasNext should equal (false)

		tok = new Tokenizer(new File("grader/src/test/resources/token_test.txt"),
		  		    (c) => c.isDigit || c == '.' || c == '-')

		tok.hasNext should equal (true)
		tok.next.toDouble should equal (1.0)
		tok.hasNext should equal (true)
		tok.next.toDouble should equal (2.0)
		tok.hasNext should equal (true)
		tok.next.toDouble should equal (-3.0)
		tok.hasNext should equal (true)
		tok.next.toDouble should equal (1.2345678901234568E39)
		tok.hasNext should equal (false)
	}

	"Validator" should "work properly" in {
		TokenCaselessValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/A/sample.out"),
			new File("grader/src/test/resources/B/sample.out"),
			null) should equal (1.0)
		TokenCaselessValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/A/easy.00.out"),
			new File("grader/src/test/resources/B/easy.00.out"),
			null) should equal (1.0)
		TokenCaselessValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/A/easy.01.out"),
			new File("grader/src/test/resources/B/easy.01.out"),
			null) should equal (0.0)
		TokenCaselessValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/A/medium.00.out"),
			new File("grader/src/test/resources/B/medium.00.out"),
			null) should equal (0.0)
		TokenCaselessValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/A/medium.01.out"),
			new File("grader/src/test/resources/B/medium.01.out"),
			null) should equal (0.0)
		TokenNumericValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/A/artista02.out"),
			new File("grader/src/test/resources/B/artista02.out"),
			null) should equal (1.0)

		TokenCaselessValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/grade_A.out"),
			new File("grader/src/test/resources/grade_B.out"),
			null) should equal (1.0)
		TokenValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/grade_A.out"),
			new File("grader/src/test/resources/grade_B.out"),
			null) should equal (0.0)
		TokenNumericValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/grade_A.out"),
			new File("grader/src/test/resources/grade_B.out"),
			null) should equal (1.0)
	}

	"Validator" should "be tolerant to large files" in {
		TokenNumericValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/A.out"),
			new File("grader/src/test/resources/B.out"),
			null) should equal (0.0)
		TokenNumericValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/A.out"),
			new File("grader/src/test/resources/A.out"),
			null) should equal (0.0)
		TokenNumericValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/B.out"),
			new File("grader/src/test/resources/B.out"),
			null) should equal (1.0)
	}
}

/* vim: set noexpandtab: */
