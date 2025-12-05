FROM maven:3.9.10-eclipse-temurin-17

WORKDIR /opt/oxo

COPY  pom.xml /opt/oxo
COPY  startBackend.sh /opt/oxo
COPY oxo2-shared/ /opt/oxo/oxo2-shared
COPY oxo2-backend/ /opt/oxo/oxo2-backend

RUN cd /opt/oxo/oxo2-shared \
    && mvn -e -B package install

RUN cd /opt/oxo/oxo2-backend \
    && mvn -e -B package install


RUN addgroup --system oxo && adduser --system --ingroup oxo oxo

RUN chown -R oxo:oxo /opt/oxo
RUN chmod -R 777 /opt/oxo


USER oxo

EXPOSE 8081
ENTRYPOINT ["/opt/oxo/startBackend.sh"]