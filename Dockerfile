# Image de l'application, construite en deux étapes : compilation par Maven, puis exécution sur un JRE seul.
#   docker build -t subscription-api .

FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
# Les dépendances d'abord : cette couche reste en cache tant que pom.xml ne change pas
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests \
	&& cp target/*.jar application.jar \
	&& java -Djarmode=tools -jar application.jar extract --layers --destination extracted \
	&& mkdir -p extracted/dependencies extracted/spring-boot-loader extracted/snapshot-dependencies extracted/application

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --create-home --uid 1001 app
# Une couche par rythme de changement : les dépendances, rarement modifiées, restent en cache
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
COPY docker/entrypoint.sh /entrypoint.sh
USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
ENTRYPOINT ["sh", "/entrypoint.sh"]
