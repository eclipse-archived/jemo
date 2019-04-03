FROM openjdk:8-jre-alpine
WORKDIR /
ADD target/jemo-*.jar jemo.jar
EXPOSE 80 443
CMD java -jar jemo.jar