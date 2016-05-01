import com.omegaup.Logging
import com.omegaup.grader._

import java.io._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FlatSpec
import org.scalatest.Matchers

case class Lit(value: String) extends Token(
	nextChar = -1, reader = null,
	containedInTokenClass = _ => false
) {
	override def toDouble() = value.toDouble
}

class ValidatorSpec extends FlatSpec with Matchers with ContextMixin {

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
			null) should equal (1.0)
		TokenNumericValidator.validateCase(
			null,
			"test",
			new File("grader/src/test/resources/B.out"),
			new File("grader/src/test/resources/B.out"),
			null) should equal (1.0)
	}

	"NumericValidator" should "Work with big numbers" in {
		val validator = new NumericTokenValidator(0.01)
		for (n <- 0 until 100) {
			val number = "1" + "0" * n
			validator.equal(
				Lit(number),
				Lit(number + ".01")
			) should equal (true)
			validator.equal(
				Lit("-" + number),
				Lit("-" + number + ".01")
			) should equal (true)
		}
	}

	"NumericValidator" should "Work with small numbers" in {
		val validator = new NumericTokenValidator(0.01)
		for (n <- 0 until 100) {
			validator.equal(
				Lit("0." + "0" * n + "1"),
				Lit("0." + "0" * n + "101")
			) should equal (true)
			validator.equal(
				Lit("-0." + "0" * n + "1"),
				Lit("-0." + "0" * n + "101")
			) should equal (true)
		}
	}

	"NumericValidator" should "Work with numbers around 1" in {
		val validator = new NumericTokenValidator(0.01)
		for (n <- 2 until 100) {
			validator.equal(
				Lit("1." + "0" * n),
				Lit("1." + "0" * n + "1")
			) should equal (true)
			validator.equal(
				Lit("1." + "0" * n),
				Lit("0." + "9" * n)
			) should equal (true)
			validator.equal(
				Lit("-1." + "0" * n),
				Lit("-1." + "0" * n + "1")
			) should equal (true)
			validator.equal(
				Lit("-1." + "0" * n),
				Lit("-0." + "9" * n)
			) should equal (true)
		}
	}

	"AbsoluteNumericValidator" should "Be broken in many cases" in {
		val validator = new AbsoluteNumericTokenValidator(0.01)
		validator.equal(
			Lit("1.00"),
			Lit("1.01")
		) should equal (false)
		validator.equal(
			Lit("1.00"),
			Lit("1.0099")
		) should equal (true)
		validator.equal(
			Lit("100000000000000000000000.00"),
			Lit("100000000000000000000000.0099")
		) should equal (false)
	}
}

/* vim: set noexpandtab: */
