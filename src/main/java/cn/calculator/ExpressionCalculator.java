package cn.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;

/** A bounded mathematical parser. Input is never executed as program code. */
public final class ExpressionCalculator {
  public static final int MAX_EXPRESSION_LENGTH = 512;
  private static final int MAX_NESTING = 64;
  private static final int MAX_NUMBER_DIGITS = 128;
  private static final int MAX_RESULT_LENGTH = 1000;
  private static final MathContext SCIENTIFIC_PRECISION = new MathContext(15);
  private static final BigDecimal MAX_EXPONENT = new BigDecimal("1000");

  public String calculate(String expression) {
    return calculate(expression, "deg");
  }

  public String calculate(String expression, String angleMode) {
    if (!"deg".equals(angleMode) && !"rad".equals(angleMode)) {
      throw error("INVALID_ANGLE_MODE", "Angle mode must be 'deg' or 'rad'.");
    }
    if (expression == null || expression.trim().isEmpty()) {
      throw error("EMPTY_EXPRESSION", "Enter an expression to calculate.");
    }
    if (expression.length() > MAX_EXPRESSION_LENGTH) {
      throw error("EXPRESSION_TOO_LONG", "An expression may contain at most 512 characters.");
    }
    Parser parser = new Parser(expression.replace('×', '*').replace('÷', '/')
        .replace("π", "pi"), "deg".equals(angleMode));
    BigDecimal result = parser.expression();
    parser.skipWhitespace();
    if (!parser.atEnd()) {
      throw error("INVALID_EXPRESSION", "Check the operators, numbers, and parentheses.");
    }
    return format(result);
  }

  static String format(BigDecimal value) {
    BigDecimal normalized = value.stripTrailingZeros();
    long integerDigits = (long) normalized.precision() - normalized.scale();
    long plainLength = Math.max(1, integerDigits) + Math.max(0, normalized.scale())
        + (normalized.scale() > 0 ? 1 : 0) + (normalized.signum() < 0 ? 1 : 0);
    if (plainLength > MAX_RESULT_LENGTH) {
      throw error("RESULT_TOO_LARGE", "The result is too long. Simplify the expression.");
    }
    return normalized.toPlainString();
  }

  private static BigDecimal bounded(BigDecimal value) {
    format(value);
    return value.stripTrailingZeros();
  }

  private static BigDecimal approximate(double value) {
    if (!Double.isFinite(value)) {
      throw error("NON_FINITE_RESULT", "This operation does not have a finite real result.");
    }
    return bounded(BigDecimal.valueOf(value).round(SCIENTIFIC_PRECISION));
  }

  private static BigDecimal power(BigDecimal base, BigDecimal exponent) {
    if (exponent.abs().compareTo(MAX_EXPONENT) > 0) {
      throw error("EXPONENT_TOO_LARGE", "The absolute exponent must not exceed 1000.");
    }
    if (exponent.stripTrailingZeros().scale() <= 0) {
      int integerExponent = exponent.intValueExact();
      if (base.signum() == 0 && integerExponent < 0) {
        throw error("DIVISION_BY_ZERO", "Zero cannot be raised to a negative power.");
      }
      // Bound intermediate powers as well as the final reciprocal.
      BigDecimal magnitude = BigDecimal.ONE;
      BigDecimal factor = base;
      int remaining = Math.abs(integerExponent);
      while (remaining > 0) {
        if ((remaining & 1) == 1) {
          magnitude = bounded(magnitude.multiply(factor));
        }
        remaining >>= 1;
        if (remaining > 0) {
          factor = bounded(factor.multiply(factor));
        }
      }
      return integerExponent < 0
          ? bounded(BigDecimal.ONE.divide(magnitude, MathContext.DECIMAL128)) : magnitude;
    }
    if (base.signum() < 0) {
      throw error("DOMAIN_ERROR", "A negative base requires an integer exponent.");
    }
    if (base.signum() == 0 && exponent.signum() < 0) {
      throw error("DIVISION_BY_ZERO", "Zero cannot be raised to a negative power.");
    }
    double doubleBase = base.doubleValue();
    if (!Double.isFinite(doubleBase) || doubleBase == 0 && base.signum() != 0) {
      throw error("SCIENTIFIC_RANGE", "The base is outside the scientific calculation range.");
    }
    double result = Math.pow(doubleBase, exponent.doubleValue());
    if (result == 0 && base.signum() != 0) {
      throw error("SCIENTIFIC_RANGE", "The result is too small for scientific calculation.");
    }
    return approximate(result);
  }

  private static BigDecimal factorial(BigDecimal value) {
    if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0
        || value.compareTo(new BigDecimal("170")) > 0) {
      throw error("FACTORIAL_DOMAIN", "Factorial requires an integer from 0 to 170.");
    }
    BigDecimal result = BigDecimal.ONE;
    for (int index = 2; index <= value.intValue(); index++) {
      result = result.multiply(BigDecimal.valueOf(index));
    }
    return result;
  }

  private static CalculationException error(String code, String message) {
    return new CalculationException(code, message);
  }

  private static final class Parser {
    private final String input;
    private final boolean degrees;
    private int position;
    private int nesting;

    Parser(String input, boolean degrees) {
      this.input = input;
      this.degrees = degrees;
    }

    BigDecimal expression() {
      BigDecimal value = term();
      while (true) {
        if (consume('+')) {
          value = bounded(value.add(term()));
        } else if (consume('-')) {
          value = bounded(value.subtract(term()));
        } else {
          return value;
        }
      }
    }

    private BigDecimal term() {
      BigDecimal value = unary();
      while (true) {
        if (consume('*')) {
          value = bounded(value.multiply(unary()));
        } else if (consume('/')) {
          BigDecimal divisor = unary();
          if (divisor.signum() == 0) {
            throw error("DIVISION_BY_ZERO", "The divisor must not be zero.");
          }
          value = bounded(value.divide(divisor, MathContext.DECIMAL128));
        } else {
          return value;
        }
      }
    }

    private BigDecimal unary() {
      boolean negative = false;
      while (true) {
        if (consume('-')) {
          negative = !negative;
        } else if (!consume('+')) {
          break;
        }
      }
      BigDecimal value = powerExpression();
      return negative ? value.negate() : value;
    }

    private BigDecimal powerExpression() {
      BigDecimal value = primary();
      while (consume('!')) {
        value = factorial(value);
      }
      if (consume('^')) {
        enterNesting();
        BigDecimal exponent = unary();
        nesting--;
        value = power(value, exponent);
      }
      return value;
    }

    private BigDecimal primary() {
      if (consume('(')) {
        return parenthesized();
      }
      skipWhitespace();
      if (!atEnd() && isLetter(input.charAt(position))) {
        int start = position;
        while (!atEnd() && isLetter(input.charAt(position))) {
          position++;
        }
        String name = input.substring(start, position).toLowerCase(Locale.ROOT);
        if ("pi".equals(name)) {
          return BigDecimal.valueOf(Math.PI);
        }
        if ("e".equals(name)) {
          return BigDecimal.valueOf(Math.E);
        }
        if (!consume('(')) {
          throw error("INVALID_EXPRESSION", "Functions require parentheses, for example sin(30).");
        }
        return function(name, parenthesized());
      }
      return number();
    }

    private BigDecimal parenthesized() {
      enterNesting();
      BigDecimal value = expression();
      if (!consume(')')) {
        throw error("INVALID_EXPRESSION", "The parentheses do not match.");
      }
      nesting--;
      return value;
    }

    private BigDecimal function(String name, BigDecimal argument) {
      if ("abs".equals(name)) {
        return argument.abs();
      }
      double value = argument.doubleValue();
      if (!Double.isFinite(value) || value == 0 && argument.signum() != 0) {
        throw error("SCIENTIFIC_RANGE", "The input is outside the scientific calculation range.");
      }
      switch (name) {
        case "sin":
        case "cos":
        case "tan":
          return trigonometric(name, argument);
        case "arcsin":
        case "asin":
        case "arccos":
        case "acos":
          if (argument.abs().compareTo(BigDecimal.ONE) > 0) {
            throw error("DOMAIN_ERROR", name + " requires a value between -1 and 1.");
          }
          double inverse = ("arcsin".equals(name) || "asin".equals(name)) ? Math.asin(value) : Math.acos(value);
          return approximate(degrees ? Math.toDegrees(inverse) : inverse);
        case "arctan":
        case "atan":
          double atan = Math.atan(value);
          return approximate(degrees ? Math.toDegrees(atan) : atan);
        case "sqrt":
          if (argument.signum() < 0) {
            throw error("DOMAIN_ERROR", "Square root requires a nonnegative value.");
          }
          return approximate(Math.sqrt(value));
        case "ln":
        case "log":
          if (argument.signum() <= 0) {
            throw error("DOMAIN_ERROR", "Logarithms require a positive value.");
          }
          return approximate("ln".equals(name) ? Math.log(value) : Math.log10(value));
        case "exp":
          double exponential = Math.exp(value);
          if (exponential == 0) {
            throw error("SCIENTIFIC_RANGE", "The result is too small for scientific calculation.");
          }
          return approximate(exponential);
        default:
          throw error("UNKNOWN_FUNCTION", "Unknown function: " + name + ".");
      }
    }

    private BigDecimal trigonometric(String name, BigDecimal argument) {
      if (argument.abs().compareTo(new BigDecimal("1000000000000")) > 0) {
        throw error("SCIENTIFIC_RANGE", "Angle magnitude must not exceed 1000000000000.");
      }
      double radians;
      if (degrees) {
        BigDecimal reduced = argument.remainder(new BigDecimal("360"));
        if (reduced.remainder(new BigDecimal("90")).signum() == 0) {
          int quadrant = (reduced.intValue() / 90 % 4 + 4) % 4;
          if ("sin".equals(name)) {
            return BigDecimal.valueOf(new int[] {0, 1, 0, -1}[quadrant]);
          }
          if ("cos".equals(name)) {
            return BigDecimal.valueOf(new int[] {1, 0, -1, 0}[quadrant]);
          }
          if (quadrant % 2 == 1) {
            throw error("DOMAIN_ERROR", "Tangent is undefined at this angle.");
          }
          return BigDecimal.ZERO;
        }
        radians = Math.toRadians(reduced.doubleValue());
      } else {
        radians = argument.doubleValue();
      }
      if ("tan".equals(name) && Math.abs(Math.cos(radians)) < 1e-15) {
        throw error("DOMAIN_ERROR", "Tangent is undefined or too close to a singularity.");
      }
      double result = "sin".equals(name) ? Math.sin(radians)
          : "cos".equals(name) ? Math.cos(radians) : Math.tan(radians);
      return approximate(result);
    }

    private BigDecimal number() {
      skipWhitespace();
      int start = position;
      int digits = 0;
      while (!atEnd() && isDigit(input.charAt(position))) {
        position++;
        digits++;
      }
      if (!atEnd() && input.charAt(position) == '.') {
        position++;
        while (!atEnd() && isDigit(input.charAt(position))) {
          position++;
          digits++;
        }
      }
      if (digits == 0) {
        throw error("INVALID_EXPRESSION", "Enter a complete number, function, or expression.");
      }
      if (digits > MAX_NUMBER_DIGITS) {
        throw error("NUMBER_TOO_LONG", "A number may contain at most 128 digits.");
      }
      return new BigDecimal(input.substring(start, position));
    }

    private void enterNesting() {
      if (++nesting > MAX_NESTING) {
        throw error("EXPRESSION_TOO_COMPLEX", "Expression nesting may not exceed 64 levels.");
      }
    }

    private boolean consume(char character) {
      skipWhitespace();
      if (!atEnd() && input.charAt(position) == character) {
        position++;
        return true;
      }
      return false;
    }

    private void skipWhitespace() {
      while (!atEnd() && Character.isWhitespace(input.charAt(position))) {
        position++;
      }
    }

    private boolean atEnd() {
      return position >= input.length();
    }

    private boolean isDigit(char character) {
      return character >= '0' && character <= '9';
    }

    private boolean isLetter(char character) {
      return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z';
    }
  }
}
