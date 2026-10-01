# Java coding conventions

Source: [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). This project adopts its naming, indentation, import, and source-organization conventions. Files use UTF-8, and documentation, comments, logs, and user-facing text are written in English.

- Use two spaces for indentation; do not use tabs. Opening braces stay on the declaration line.
- Name classes with `UpperCamelCase`, methods and variables with `lowerCamelCase`, and constants with `UPPER_SNAKE_CASE`.
- Declare one public top-level class per file and use lowercase package names.
- Use explicit imports instead of wildcards. Order imports consistently by qualified name.
- Target 100-character lines and split long declarations and expressions where readable.
- Close resources with try-with-resources. Do not swallow exceptions or expose stack traces and database details to clients.
- Keep HTTP validation, mathematical parsing, conversion logic, and persistence in separate classes.
- Use prepared SQL statements and typed JSON validation. Never pass user input to a script engine, reflection-based evaluator, shell, or executable-code interpreter.
- Preserve exact decimal and integer operations where possible; document floating-point approximations and rounding rules.
- Check numeric domains, nesting, expression length, and intermediate result limits before expensive work.
- Run the expression and conversion suites after mathematical changes. Run migration and persistence tests after database changes, including legacy-record compatibility.
