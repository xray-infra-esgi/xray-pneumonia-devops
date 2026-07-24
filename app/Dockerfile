# Stage 1: build the jar 
FROM sbtscala/scala-sbt:eclipse-temurin-11.0.17_8_1.8.2_2.13.10 AS builder
WORKDIR /build

COPY build.sbt .
COPY project/build.properties project/plugins.sbt project/
RUN sbt update

COPY src/ src/
RUN sbt assembly

# Stage 2: minimal runtime
FROM eclipse-temurin:11-jre
WORKDIR /app
COPY conf/ conf/
COPY --from=builder /build/target/scala-2.13/xray.jar .

ENTRYPOINT ["java", "-jar", "/app/xray.jar"]
CMD ["--help"]
