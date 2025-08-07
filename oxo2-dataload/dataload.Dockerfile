FROM oxo2-build

RUN mkdir -p /opt/solr && \
   curl -L https://archive.apache.org/dist/solr/solr/9.9.0/solr-9.9.0.tgz | tar --strip-components=1 -C /opt/solr -xzf - && \
   chown -R oxo:oxo /opt/solr

RUN mkdir -p /opt/nemo && \
    curl -L https://github.com/knowsys/nemo/releases/download/v0.8.0/nemo_v0.8.0_x86_64-unknown-linux-gnu.tar.gz | tar --strip-components=1 -C /opt/nemo -xzf - && \
    chown -R oxo:oxo /opt/nemo

RUN mkdir -p /tmp/data \
    && mkdir -p /tmp/logs \
    && chown -R oxo:oxo /tmp/data \
    && chown -R oxo:oxo /tmp/logs



ENV PATH="${PATH}:/opt/nemo:/opt/solr"
ENV SOLR_HOME="/opt/solr"
ENV OXO2_DATA="/tmp/data"

USER oxo

WORKDIR /opt/oxo/oxo2-dataload

#RUN ./loadData.sh
#ENTRYPOINT ["sleep", "600"]
CMD ./loadData.sh > /tmp/logs/dataload.logs


#
#
#
#FROM ghcr.io/knowsys/nemo:latest AS inferences
#WORKDIR /opt/oxo/oxo2-dataload
#
## Copy the generated inferences from the build stage
#COPY --from=mapping-facts /tmp/data/inferences/ /tmp/data/inferences/
#COPY --from=mapping-facts /opt/oxo/oxo2-dataload/oxo2-json2inferences /opt/oxo/oxo2-dataload/oxo2-json2inferences
#
#RUN /opt/oxo/oxo2-dataload/oxo2-json2inferences/chain-rules.rls -o -v -D /tmp/data/inferences/
#
#
#FROM eclipse-temurin:17-jre
#WORKDIR /opt/oxo/oxo2-dataload
#
#COPY --from=inferences /tmp/data/inferences/inferredMapping.ttl /tmp/data/inferences/inferredMapping.ttl
#
#CMD ./oxo2-json2inferences/inferences2trace.sh /tmp/data/inferences/inferredMapping.ttl /tmp/data/inferences/inferencesToTrace.txt
