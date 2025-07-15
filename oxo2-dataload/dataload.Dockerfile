FROM eclipse-temurin:17-jre

RUN mkdir /tmp/data

RUN groupadd --gid 1001 oxo && useradd --uid 1001 --gid oxo oxo
RUN chown oxo:oxo /tmp/data

USER oxo



WORKDIR /opt/oxo/oxo2-dataload

CMD ./dataload.dockersh
