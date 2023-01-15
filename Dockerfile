FROM debian:bullseye-slim

RUN apt-get update && apt-get install -y \
    openjdk-17-jdk-headless gnuplot \
 && rm -rf /var/lib/apt/lists/*

EXPOSE 19090

WORKDIR /rsam-ssam

COPY target/logback.xml logback.xml
COPY target/conf conf
COPY target/gnuplot_scripts gnuplot_scripts
COPY target/web web

COPY target/rsam-ssam.jar rsam-ssam.jar

CMD ["java", "-jar", "rsam-ssam.jar"]
