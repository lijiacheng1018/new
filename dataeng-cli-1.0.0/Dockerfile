# dataeng-cli —— 多阶段构建（加分项：容器化交付）
# 构建阶段
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
RUN mvn -q -B package -DskipTests

# 运行阶段
FROM eclipse-temurin:8-jre
WORKDIR /opt/dataeng
COPY --from=build /app/target/dataeng-cli.jar /opt/dataeng/dataeng-cli.jar
COPY bin/dataeng-cli /usr/local/bin/dataeng-cli
RUN chmod +x /usr/local/bin/dataeng-cli
VOLUME ["/data"]
ENTRYPOINT ["dataeng-cli"]
CMD ["--help"]
