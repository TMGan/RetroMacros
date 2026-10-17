# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependencies separately from source so code edits do not re-download the world.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as an unprivileged user, so a compromised app process can't take over the container.
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build --chown=app:app /build/target/*.jar app.jar
USER app

# Render's free instance type caps out at 512MB. SerialGC has a much smaller
# footprint than G1 on a single shared vCPU, and capping heap at 70% leaves
# room for metaspace, threads, and the JVM itself.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
