# ---- Stage 1: build the jar (this stage is thrown away afterwards) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Fix Windows line endings and make the script executable
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
# Download dependencies first: this layer is cached until pom.xml changes
RUN ./mvnw -q -B dependency:go-offline
COPY src ./src
# Build the jar (tests already ran in IntelliJ/CI)
RUN ./mvnw -q -B -DskipTests package

# ---- Stage 2: the small image we actually ship ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# A non-root user: a compromised app can't do root things
RUN useradd --system --uid 10001 appuser
# Copy only the jar: no compiler, sources or Maven cache
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
# MaxRAMPercentage sizes the heap from the container's memory limit, not the host's RAM
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]