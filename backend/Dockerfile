FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY lib ./lib
COPY src ./src
RUN mkdir -p bin data && javac -cp "lib/*" -d bin src/*.java
EXPOSE 8080
CMD ["java", "-cp", "bin:lib/*", "ApiServer"]
