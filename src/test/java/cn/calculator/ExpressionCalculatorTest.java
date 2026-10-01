package cn.calculator;

/** Dependency-free regression suite. Throws on failure; does not require enabled JVM assertions. */
public final class ExpressionCalculatorTest {
  private static final ExpressionCalculator CALCULATOR = new ExpressionCalculator();
  private static int count;

  public static void main(String[] args) {
    equals("arcsin(0.5)", "30");
    equals("arccos(0.5)", "60");
    equals("arctan(1)", "45");
    equals("1 + 2 * 3", "7");
    equals("(1 + 2) * 3", "9");
    equals("10 / 2 + 7", "12");
    equals("8 - 3 * 2", "2");
    equals("-5 + 8", "3");
    equals("3 * -2", "-6");
    equals("+5 + 2", "7");
    equals("1.5 + 2.25", "3.75");
    equals("0.1 + 0.2", "0.3");
    equals("1 / 8", "0.125");
    equals("1 / 3", "0.3333333333333333333333333333333333");
    equals("2 / 3", "0.6666666666666666666666666666666667");
    equals("8 / 4 / 2", "1");
    equals("10 - 3 - 2", "5");
    equals(".5 + .25", "0.75");
    equals("2. + 1", "3");
    equals("  ( -2.5 + +.5 ) × 4 ÷ 2  ", "-4");
    equals("-(2 + 3) * --2", "-10");
    equals("-0.0", "0");
    equals("123456789012345678901234567890 + 1", "123456789012345678901234567891");
    equals(repeat("(", 64) + "1" + repeat(")", 64), "1");
    equals(repeat("-", 500) + "1", "1");
    equals(repeat("9", 128) + " + 1", "1" + repeat("0", 128));

    fails(null, "EMPTY_EXPRESSION");
    fails(" \t\n ", "EMPTY_EXPRESSION");
    fails("1 / 0", "DIVISION_BY_ZERO");
    fails("1 / (3 - 3)", "DIVISION_BY_ZERO");
    fails("1 / -0.0", "DIVISION_BY_ZERO");
    fails("2 +", "INVALID_EXPRESSION");
    fails("()", "INVALID_EXPRESSION");
    fails("(1+2", "INVALID_EXPRESSION");
    fails("1+2)", "INVALID_EXPRESSION");
    fails("1..2", "INVALID_EXPRESSION");
    fails("1 2", "INVALID_EXPRESSION");
    fails("2(3+4)", "INVALID_EXPRESSION");
    fails("2**3", "INVALID_EXPRESSION");
    equals("2^3", "8");
    fails("1e3", "INVALID_EXPRESSION");
    fails("NaN", "INVALID_EXPRESSION");
    fails("Infinity", "INVALID_EXPRESSION");
    fails("Math.pow(2,3)", "INVALID_EXPRESSION");
    fails("1;System.exit(0)", "INVALID_EXPRESSION");
    fails(repeat("9", 129), "NUMBER_TOO_LONG");
    fails(repeat("(", 65) + "1" + repeat(")", 65), "EXPRESSION_TOO_COMPLEX");
    fails(repeat("1", 513), "EXPRESSION_TOO_LONG");
    equals("2^3^2", "512");
    equals("-2^2", "-4");
    equals("(-2)^2", "4");
    equals("2^-2", "0.25");
    equals("-2^-2", "-0.25");
    equals("2^3!", "64");
    equals("-3!", "-6");
    equals("0!", "1");
    equals("10!", "3628800");
    equals("3!!", "720");
    equals("4^0.5", "2");
    equals("(-2)^-3", "-0.125");
    equals("0^0", "1");
    equals("sin(30)", "0.5");
    equals("sin(180)", "0");
    equals("sin(-90)", "-1");
    equals("cos(90)", "0");
    equals("cos(360)", "1");
    equals("tan(45)", "1");
    equals("tan(180)", "0");
    equals("asin(0.5)", "30");
    equals("acos(0.5)", "60");
    equals("atan(1)", "45");
    equals("sqrt(9) + abs(-2)", "5");
    equals("ln(e)", "1");
    equals("log(1000)", "3");
    equals("exp(0)", "1");
    equals("SIN(30) + Cos(60)", "1");
    equals("sin(pi/2)", "rad", "1");
    equals("sin(π/2)", "rad", "1");
    equals("asin(1)", "rad", "1.5707963267949");
    equals("2^100", "1267650600228229401496703205376");
    equals("((1.0000000000000^1000)^1000)^1000", "1");
    fails("tan(90)", "DOMAIN_ERROR");
    fails("tan(-270)", "DOMAIN_ERROR");
    fails("tan(pi/2)", "rad", "DOMAIN_ERROR");
    fails("sqrt(-1)", "DOMAIN_ERROR");
    fails("ln(0)", "DOMAIN_ERROR");
    fails("log(-2)", "DOMAIN_ERROR");
    fails("asin(1.01)", "DOMAIN_ERROR");
    fails("acos(-1.1)", "DOMAIN_ERROR");
    fails("(-1)^0.5", "DOMAIN_ERROR");
    fails("0^-1", "DIVISION_BY_ZERO");
    fails("2^1001", "EXPONENT_TOO_LARGE");
    fails("(-1)!", "FACTORIAL_DOMAIN");
    fails("2.5!", "FACTORIAL_DOMAIN");
    fails("171!", "FACTORIAL_DOMAIN");
    fails("exp(1000)", "NON_FINITE_RESULT");
    fails("exp(-1000)", "SCIENTIFIC_RANGE");
    fails("sqrt(10^-400)", "SCIENTIFIC_RANGE");
    fails("(10^-400)^0.5", "SCIENTIFIC_RANGE");
    fails("unknown(1)", "UNKNOWN_FUNCTION");
    fails("sin(1000000000001)", "SCIENTIFIC_RANGE");
    fails("2", "degrees", "INVALID_ANGLE_MODE");
    fails("10^1000", "RESULT_TOO_LARGE");
    fails(repeat("1^", 65) + "1", "EXPRESSION_TOO_COMPLEX");
    System.out.println("PASS: " + count + " parser checks");
  }

  private static void equals(String expression, String expected) {
    equals(expression, "deg", expected);
  }

  private static void equals(String expression, String mode, String expected) {
    String actual = CALCULATOR.calculate(expression, mode);
    if (!expected.equals(actual)) {
      throw new AssertionError(expression + " expected " + expected + " but was " + actual);
    }
    count++;
  }

  private static void fails(String expression, String expectedCode) {
    fails(expression, "deg", expectedCode);
  }

  private static void fails(String expression, String mode, String expectedCode) {
    try {
      CALCULATOR.calculate(expression, mode);
      throw new AssertionError("Expected rejection: " + expression);
    } catch (CalculationException exception) {
      if (!expectedCode.equals(exception.getCode())) {
        throw new AssertionError("Expected " + expectedCode + " but was " + exception.getCode());
      }
      count++;
    }
  }

  private static String repeat(String text, int count) {
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < count; index++) {
      result.append(text);
    }
    return result.toString();
  }
}
