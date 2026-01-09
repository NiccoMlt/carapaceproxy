FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
COPY carapace-server/pom.xml carapace-server/
COPY carapace-ui/pom.xml carapace-ui/

COPY carapace-server/src carapace-server/src
COPY carapace-ui/src carapace-ui/src

COPY .mvn .mvn
COPY mvnw mvnw

RUN ./mvnw clean install -DskipTests -Pproduction -B

FROM eclipse-temurin:21-jre-jammy

ENV ADMIN_PORT=8001
ENV LISTENER_HTTP_PORT=8089
ENV LISTENER_HTTPS_PORT=4089
ENV CARAPACE_http.admin.host=0.0.0.0

EXPOSE $ADMIN_PORT
EXPOSE $LISTENER_HTTP_PORT
EXPOSE $LISTENER_HTTPS_PORT

RUN apt-get update \
     && apt-get -y dist-upgrade \
     && apt-get -y install --no-install-recommends netcat dnsutils less curl unzip \
     && apt-get -y --purge autoremove \
     && apt-get autoclean \
     && apt-get clean \
     && rm -rf /var/lib/apt/lists/*

RUN mkdir /carapace
WORKDIR /carapace

COPY --from=builder /app/carapace-server/target/carapace-server-*.zip /tmp/

RUN unzip /tmp/carapace-server-*.zip -d /tmp/extracted \
    && mv /tmp/extracted/carapace-server-*/* /carapace/ \
    && rm -rf /tmp/carapace-server-*.zip /tmp/extracted

COPY entrypoint.sh /carapace/entrypoint.sh
RUN chmod +x /carapace/entrypoint.sh

ENTRYPOINT ["/carapace/entrypoint.sh"]
CMD ["/carapace/bin/service", "server", "console"]
