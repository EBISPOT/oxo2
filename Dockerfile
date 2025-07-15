FROM maven:3.9.10-eclipse-temurin-17 AS build

WORKDIR /opt/build

COPY . /opt/build

RUN mvn clean package install


FROM eclipse-temurin:17-jre
WORKDIR /opt/oxo
RUN mkdir oxo2-dataload oxo2-backend && \
    cd /opt/oxo/oxo2-dataload && \
    mkdir oxo2-downloader oxo2-sssom2json oxo2-json2inferences && \
    mkdir oxo2-downloader/target oxo2-sssom2json/target oxo2-json2inferences/target && \
    cd /opt/oxo/oxo2-backend && \
    mkdir /opt/oxo/oxo2-backend/target

WORKDIR /opt/oxo/oxo2-dataload
COPY --from=build /opt/build/oxo2-dataload/oxo2-downloader/target/oxo2-downloader-1.0.0-SNAPSHOT.jar ./oxo2-downloader/target
COPY --from=build /opt/build/oxo2-dataload/oxo2-sssom2json/target/oxo2-sssom2json-1.0.0-SNAPSHOT.jar ./oxo2-sssom2json/target
COPY --from=build /opt/build/oxo2-dataload/oxo2-json2inferences/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar ./oxo2-json2inferences/target

COPY --from=build /opt/build/oxo2-dataload/downloadMappings.sh /opt/build/oxo2-dataload/sssom2json.sh /opt/build/oxo2-dataload/dataload.dockersh ./

RUN chmod +x ./downloadMappings.sh ./sssom2json.sh ./dataload.dockersh

RUN groupadd --gid 1001 oxo && useradd --uid 1001 --gid oxo oxo


ENTRYPOINT ["chown", "oxo:oxo", "/opt/oxo"]
