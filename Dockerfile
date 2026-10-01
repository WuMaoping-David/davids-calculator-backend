FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/classes ./classes
COPY --from=build /build/target/dependency ./lib
ENV BIND_ADDRESS=0.0.0.0 \
    PORT=8080 \
    DB_PATH=/var/data/calculator
EXPOSE 8080
CMD ["java", "-Dfile.encoding=UTF-8", "-cp", "classes:lib/*", "cn.calculator.CalculatorApplication"]
