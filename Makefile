# ==========================================
# Library Management System
# ==========================================

APP_NAME=library-service
MVN=mvnw.cmd
# MVN=mvn
# MVN=./mvnw

.PHONY: help clean compile test package run \
        docker-build docker-up docker-down docker-restart \
        docker-logs docker-ps logs \
        rebuild verify

help:
	@echo ""
	@echo "Available commands:"
	@echo "  make compile         Compile project"
	@echo "  make test            Run tests"
	@echo "  make verify          Run all verification"
	@echo "  make package         Build JAR"
	@echo "  make clean           Clean target directory"
	@echo ""
	@echo "  make run             Run locally"
	@echo ""
	@echo "  make docker-build    Build Docker image"
	@echo "  make docker-up       Start containers"
	@echo "  make docker-down     Stop containers"
	@echo "  make docker-restart  Restart containers"
	@echo "  make docker-logs     Follow Docker logs"
	@echo "  make docker-ps       List running containers"
	@echo ""
	@echo "  make rebuild         Clean + Package + Docker Build"

java:
	java -version

hello:
	echo Hello buddy! Welcome to makefile world

clean:
	$(MVN) clean

compile:
	$(MVN) clean compile

test:
	$(MVN) test

verify:
	$(MVN) verify

package:
	$(MVN) clean package

run:
	$(MVN) spring-boot:run

docker-build:
	docker compose build

docker-up:
	docker compose up -d

docker-up-3-apps:
	docker compose up -d --scale app=3

docker-load-test-up:
	docker compose --profile load-test up -d

docker-down:
	docker compose down

docker-restart:
	docker compose down
	docker compose up -d

docker-logs:
	docker compose logs -f

docker-ps:
	docker compose ps

rebuild:
	$(MVN) clean package
	docker compose build