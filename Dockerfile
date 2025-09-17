FROM azul/zulu-openjdk-alpine:25-latest AS build
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dkotlin.incremental=false"
WORKDIR /app

COPY gradlew settings.gradle ./
COPY gradle ./gradle
RUN ./gradlew --version

COPY build.gradle ./
COPY src ./src
RUN ./gradlew build


FROM project42/s6-alpine:3.19
LABEL maintainer="Jake Wharton <docker@jakewharton.com>"
ENTRYPOINT ["/init"]
ENV \
    # Fail if cont-init scripts exit with non-zero code.
    S6_BEHAVIOUR_IF_STAGE2_FAILS=2 \
    # Run every hour (at 12 minutes past) by default.
    CRON="12 * * * *" \
    SCAN_IDLE="5" \
    HEALTHCHECK_ID="" \
    HEALTHCHECK_HOST="https://hc-ping.com"

RUN apk add --no-cache \
      curl \
      openjdk8-jre \
 && rm -rf /var/cache/* \
 && mkdir /var/cache/apk

COPY root/ /

WORKDIR /app
COPY --from=build /app/build/install/plex-auto-trash ./
