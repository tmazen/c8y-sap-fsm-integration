# Force AMD64 for Cumulocity Cloud compatibility
FROM --platform=linux/amd64 eclipse-temurin:21-jre-alpine

# 1. Install system tools
RUN apk add --no-cache coreutils wget
RUN mkdir -p /etc/c8y-fsm-sync-microservice-java/ /data/ /otel

# 2. Copy Configuration & Manifest
# This is the "Magic Fix" for your null password.
# We copy it to the location the Cumulocity SDK automatically scans.
COPY src/main/resources/application.yml /etc/c8y-fsm-sync-microservice-java/application.yml

# Copy the manifest file
COPY src/main/configuration/cumulocity.json /data/cumulocity.json

# Copy resources (contains entrypoint.sh)
# If this folder is also missing, we will need to find your entrypoint.sh!
#COPY src/main/resources/ /data/

# Copy the entrypoint script directly (more reliable than copying the whole folder)
COPY src/main/resources/entrypoint.sh /data/entrypoint.sh

# 3. Copy the compiled JAR
COPY target/c8y-fsm-sync-microservice-java-*.jar /app.jar


# 4. Final setup
RUN chmod +x /data/entrypoint.sh
#WORKDIR /data

ENTRYPOINT ["/bin/sh", "/data/entrypoint.sh"]
