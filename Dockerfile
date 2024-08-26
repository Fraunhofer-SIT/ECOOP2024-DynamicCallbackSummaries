FROM adoptopenjdk/maven-openjdk11:latest

COPY Dependencies/maven-repository/ /root/.m2/repository/
COPY Code /Code
COPY Datasets /Datasets
COPY Results /Results

WORKDIR /

