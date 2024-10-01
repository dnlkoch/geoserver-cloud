all: install test build-image

TAG=$(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
COSIGN_PASSWORD := $(COSIGN_PASSWORD)
COMPOSE_PGCONFIG_OPTIONS ?= -f compose.yml -f catalog-pgconfig.yml
COMPOSE_DATADIR_OPTIONS ?= -f compose.yml -f catalog-datadir.yml
COMPOSE_ACCEPTANCE_PGCONFIG_OPTIONS ?= $(COMPOSE_PGCONFIG_OPTIONS) -f acceptance.yml
COMPOSE_ACCEPTANCE_DATADIR_OPTIONS ?= $(COMPOSE_DATADIR_OPTIONS) -f acceptance.yml
UID=$(shell id -u)
GID=$(shell id -g)

clean:
	./mvnw clean

lint:
	./mvnw fmt:check sortpom:verify -Dsort.verifyFailOn=strict -Dsort.verifyFail=stop -ntp

format:
	./mvnw sortpom:sort fmt:format -ntp

install:
	./mvnw clean install -DskipTests -ntp -T4 -U

test:
	./mvnw verify -ntp -T4

build-base-images:
	./mvnw clean package -f src/apps/base-images -DskipTests -T4 && \
	COMPOSE_DOCKER_CLI_BUILD=1 \
	DOCKER_BUILDKIT=1 \
	TAG=$(TAG) \
	docker compose -f docker-build/base-images.yml build 

build-image-infrastructure:
	./mvnw clean package -f src/apps/infrastructure -DskipTests -T4 && \
	COMPOSE_DOCKER_CLI_BUILD=1 \
	DOCKER_BUILDKIT=1 \
	TAG=$(TAG) \
	docker compose -f docker-build/infrastructure.yml build

build-image-geoserver:
	./mvnw clean package -f src/apps/geoserver -DskipTests -T4 && \
	COMPOSE_DOCKER_CLI_BUILD=1 \
	DOCKER_BUILDKIT=1 \
	TAG=$(TAG) \
	docker compose -f docker-build/geoserver.yml build 
  
build-image: build-base-images build-image-infrastructure build-image-geoserver

push-image:
	TAG=$(TAG) \
	docker compose \
	-f docker-build/infrastructure.yml \
	-f docker-build/geoserver.yml \
	push

.PHONY: sign-image
sign-image:
	@bash -c '\
	images=$$(docker images --format "{{.Repository}}@{{.Digest}}" | grep "geoserver-cloud-"); \
	for image in $$images; do \
	  echo "Signing $$image"; \
	  output=$$(cosign sign --yes --key env://COSIGN_KEY --recursive $$image 2>&1); \
	  if [ $$? -ne 0 ]; then \
	    echo "Error occurred: $$output"; \
	    exit 1; \
	  else \
	    echo "Signing successful: $$output"; \
	  fi; \
	done'

.PHONY: verify-image
verify-image:
	@bash -c '\
	images=$$(docker images --format "{{.Repository}}@{{.Digest}}" | grep "geoserver-cloud-"); \
	for image in $$images; do \
	  echo "Verifying $$image"; \
	  output=$$(cosign verify --key env://COSIGN_PUB_KEY $$image 2>&1); \
	  if [ $$? -ne 0 ]; then \
	    echo "Error occurred: $$output"; \
	    exit 1; \
	  else \
	    echo "Verification successful: $$output"; \
	  fi; \
	done'

.PHONY: build-acceptance
build-acceptance:
	docker build --tag=acceptance:$(TAG) acceptance_tests

.PHONY: acceptance-tests
acceptance-tests:
acceptance-tests: build-acceptance
	(cd compose/ && TAG=$(TAG) GS_USER=$(UID):$(GID) docker compose $(COMPOSE_ACCEPTANCE_DATADIR_OPTIONS) up -d)
	sleep 30
	(cd compose/ && TAG=$(TAG) GS_USER=$(UID):$(GID) docker compose $(COMPOSE_ACCEPTANCE_DATADIR_OPTIONS) exec -T acceptance pytest . -vvv --color=yes)

.PHONY: stop-acceptance-tests
stop-acceptance-tests: build-acceptance
	(cd compose/ && TAG=$(TAG) GS_USER=$(UID):$(GID) docker compose $(COMPOSE_ACCEPTANCE_DATADIR_OPTIONS) down -v)
