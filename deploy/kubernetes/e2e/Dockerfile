FROM eclipse-temurin:25-jre
ARG SOURCE_REVISION
LABEL org.opencontainers.image.source="https://github.com/Bio-Compass/pkb-service" \
      org.opencontainers.image.revision="${SOURCE_REVISION}"
WORKDIR /opt/e2e
COPY classes/ classes/
COPY lib/ lib/
USER 65532:65532
ENTRYPOINT ["java", "-Xmx256m", "-Dpkb.e2e.target=testing", "-Djdk.httpclient.allowRestrictedHeaders=host", "-cp", "/opt/e2e/classes:/opt/e2e/lib/*", "com.biocompass.pkb.e2e.ClusterE2eRunner"]
