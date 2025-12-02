FROM maven:3.9.10-eclipse-temurin-17 AS builder

WORKDIR /build

COPY  pom.xml /build/
COPY oxo2-shared/ /build/oxo2-shared
COPY oxo2-dataload/ /build/oxo2-dataload


RUN cd oxo2-shared \
    && mvn -e -B package install


RUN cd oxo2-dataload \
    && mvn -e -B package install


FROM eclipse-temurin:17

RUN addgroup --system oxo && adduser --system --ingroup oxo oxo

RUN mkdir -p /opt/nemo \
    && curl -L https://github.com/knowsys/nemo/releases/download/v0.9.1/nemo_v0.9.1_x86_64-unknown-linux-gnu.tar.gz | tar --strip-components=1 -C /opt/nemo -xzf - \
    && chown -R oxo:oxo /opt/nemo

RUN mkdir -p /opt/solr  \
    && curl -L https://archive.apache.org/dist/solr/solr/9.9.0/solr-9.9.0.tgz | tar --strip-components=1 -C /opt/solr -xzf - \
    && rm -rf /opt/solr/server/solr/* \
    && chown -R oxo:oxo /opt/solr


RUN mkdir -p /tmp/data \
    && mkdir -p /tmp/logs \
    && chown -R oxo:oxo /tmp/data \
    && chown -R oxo:oxo /tmp/logs

ENV PATH="${PATH}:/opt/nemo:/opt/solr" \
    OXO2_DATA="/tmp/data" \
    SOLR_HOME="/opt/solr/server/solr" \
    SOLR_SCRIPT="/opt/solr/bin"

RUN mkdir -p /opt/oxo

COPY     ./oxo2-dataload/oxo2-json2inferences/chain-rules.rls \
         ./oxo2-dataload/oxo2-json2inferences/explanations2json.sh \
         ./oxo2-dataload/oxo2-json2inferences/inferences2trace.sh \
         ./oxo2-dataload/oxo2-json2inferences/json2ttl.sh \
         /opt/oxo/oxo2-dataload/oxo2-json2inferences/

COPY     ./oxo2-dataload/solr-config/ \
         /opt/oxo/oxo2-dataload/solr-config/

COPY     ./oxo2-dataload/copySolrConfig.sh \
         ./oxo2-dataload/downloadMappings.sh \
         ./oxo2-dataload/determineInferencesAndExplanations.sh \
         ./oxo2-dataload/json2solr.sh \
         ./oxo2-dataload/loadData.sh \
         ./oxo2-dataload/sssom2json.sh \
         /opt/oxo/oxo2-dataload/

COPY --from=builder \
    /build/oxo2-dataload/oxo2-downloader/target/oxo2-downloader-1.0.0-SNAPSHOT.jar \
    /opt/oxo/oxo2-dataload/oxo2-downloader/target/

COPY --from=builder \
    /build/oxo2-dataload/oxo2-json2inferences/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar \
    /opt/oxo/oxo2-dataload/oxo2-json2inferences/target/

COPY --from=builder \
    /build/oxo2-dataload/oxo2-sssom2json/target/oxo2-sssom2json-1.0.0-SNAPSHOT.jar \
    /opt/oxo/oxo2-dataload/oxo2-sssom2json/target/

ENV OXO2_CONFIG=/opt/oxo/config.json

RUN chown -R oxo:oxo /opt/* /tmp/logs
RUN chmod -R 777 /opt/* /tmp/logs


USER oxo


WORKDIR /opt/oxo/oxo2-dataload

CMD ["sh", "-c", "./loadData.sh > /tmp/logs/dataload.logs"]

