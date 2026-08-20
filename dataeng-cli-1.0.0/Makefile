# dataeng-cli 构建辅助
# 说明: 若宿主机 mvn 脚本异常，可直接用 `java -classpath ... Launcher` 直调，或使用 Docker 构建。

MVN ?= mvn

.PHONY: build test demo clean docker

build: ## 打包可执行 fat jar (target/dataeng-cli.jar)
	$(MVN) -q package -DskipTests

test: ## 运行单元测试
	$(MVN) -q test

demo: build ## 构建并一键运行全链路演示 (内置 demo 子命令)
	java -Dfile.encoding=UTF-8 -jar target/dataeng-cli.jar demo

clean:
	$(MVN) -q clean

docker: ## 用 Docker 构建镜像
	docker build -t dataeng-cli:1.0.0 .
