# 1단계: 빌드 환경 (Gradle)
FROM gradle:8-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# 로컬에서 빌드된 캐시를 제외하고 순수하게 JAR 파일 빌드
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
EXPOSE 8080
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
