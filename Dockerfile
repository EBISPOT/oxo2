FROM maven:3.9.10-eclipse-temurin-17 AS build

WORKDIR /opt/build

COPY ./ /opt/build/


RUN cd /opt/build &&  \
    mvn package install


#FROM eclipse-temurin:17-jre
#WORKDIR /opt/oxo
#RUN mkdir oxo2-dataload &&\
#    cd /opt/oxo/oxo2-dataload && \
#    mkdir oxo2-downloader oxo2-sssom2json oxo2-json2inferences && \
#    mkdir oxo2-downloader/target oxo2-sssom2json/target oxo2-json2inferences/target && \
#    mkdir solr-config &&  \
#    mkdir solr-config/oxo2-mappings solr-config/oxo2-mappingsets && \
#    cd /opt/oxo && \
#    mkdir oxo2-backend && \
#    cd /opt/oxo/oxo2-backend && \
#    mkdir target
#
#COPY --from=build /opt/build/startBackend.sh ./
#
#WORKDIR /opt/oxo/oxo2-dataload
#COPY --from=build /opt/build/oxo2-dataload/oxo2-downloader/target/oxo2-downloader-1.0.0-SNAPSHOT.jar ./oxo2-downloader/target
#COPY --from=build /opt/build/oxo2-dataload/oxo2-sssom2json/target/oxo2-sssom2json-1.0.0-SNAPSHOT.jar ./oxo2-sssom2json/target
#COPY --from=build /opt/build/oxo2-dataload/oxo2-json2inferences/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar ./oxo2-json2inferences/target
#
#COPY --from=build /opt/build/oxo2-dataload/loadData.sh /opt/build/oxo2-dataload/downloadMappings.sh /opt/build/oxo2-dataload/sssom2json.sh \
#    /opt/build/oxo2-dataload/makeInferences.sh /opt/build/oxo2-dataload/copySolrConfig.sh /opt/build/oxo2-dataload/json2solr.sh ./
#
#COPY --from=build /opt/build/oxo2-dataload/solr-config/oxo2-mappings/ ./solr-config/oxo2-mappings
#COPY --from=build /opt/build/oxo2-dataload/solr-config/oxo2-mappingsets/ ./solr-config/oxo2-mappingsets
#
#COPY --from=build /opt/build/oxo2-dataload/oxo2-json2inferences/chain-rules.rls /opt/build/oxo2-dataload/oxo2-json2inferences/json2ttl.sh \
#    /opt/build/oxo2-dataload/oxo2-json2inferences/inferences2trace.sh /opt/build/oxo2-dataload/oxo2-json2inferences/explanations2json.sh \
#    ./oxo2-json2inferences/
#
#RUN chmod +x ./loadData.sh ./downloadMappings.sh ./sssom2json.sh ./makeInferences.sh ./copySolrConfig.sh  ./json2solr.sh \
#    ./oxo2-json2inferences/json2ttl.sh ./oxo2-json2inferences/inferences2trace.sh ./oxo2-json2inferences/explanations2json.sh
#
#WORKDIR /opt/oxo/oxo2-backend
#COPY --from=build /opt/build/oxo2-backend/target/oxo2-backend-1.0.0-SNAPSHOT.jar ./target
#
#
#
#RUN groupadd --gid 1001 oxo &&  \
#    useradd --uid 1001 --gid oxo oxo &&  \
#    chown -R oxo:oxo /opt/oxo


ENTRYPOINT ["sleep", "600"]
