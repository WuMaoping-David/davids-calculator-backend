package cn.calculator;

/** A safe, user-facing expression error. */
public final class CalculationException extends RuntimeException {
  private final String code;

  public CalculationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
