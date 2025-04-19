-include .env
export
SHELL := /bin/bash
APP_NAME := kotlinkubemanager

PRJ_REPO := git@github.com:redacid/ktKubeManager.git
RELEASE_VERSION ?= 1.0.0

# colors
GREEN = $(shell tput -Txterm setaf 2)
YELLOW = $(shell tput -Txterm setaf 3)
WHITE = $(shell tput -Txterm setaf 7)
RESET = $(shell tput -Txterm sgr0)
GRAY = $(shell tput -Txterm setaf 6)
TARGET_MAX_CHAR_NUM = 30

.EXPORT_ALL_VARIABLES:

all: help

## Build and Publish
git-publish:
	make clean-workspace
	make git-release
	make git-upload-release
	make clean-workspace

.ONESHELL:
clean-workspace:
	./gradlew clean

build:
	./gradlew packageDeb

git-release:
	gh release delete $(RELEASE_VERSION) --cleanup-tag -y --repo $(PRJ_REPO) 2>/dev/null;
	git tag -d $(RELEASE_VERSION) 2>/dev/null;
	gh release create $(RELEASE_VERSION) --generate-notes --notes "$(RELEASE_VERSION)" --repo $(PRJ_REPO)

.ONESHELL:
git-upload-release:
	gh release upload $(RELEASE_VERSION) "./build/compose/binaries/main/deb/"$(APP_NAME)"_"$(RELEASE_VERSION)"-1_amd64.deb" --repo $(PRJ_REPO)


#git-update:
#	git pull && git fetch && git fetch --all

## Shows help. | Help
help:
	@echo ''
	@echo 'Usage:'
	@echo ''
	@echo '  ${YELLOW}make${RESET} ${GREEN}<target>${RESET}'
	@echo ''
	@echo 'Targets:'
	@awk '/^[a-zA-Z0-9\-_]+:/ { \
		helpMessage = match(lastLine, /^## (.*)/); \
		if (helpMessage) { \
		    if (index(lastLine, "|") != 0) { \
				stage = substr(lastLine, index(lastLine, "|") + 1); \
				printf "\n ${GRAY}%s: \n\n", stage;  \
			} \
			helpCommand = substr($$1, 0, index($$1, ":")-1); \
			helpMessage = substr(lastLine, RSTART + 3, RLENGTH); \
			if (index(lastLine, "|") != 0) { \
				helpMessage = substr(helpMessage, 0, index(helpMessage, "|")-1); \
			} \
			printf "  ${YELLOW}%-$(TARGET_MAX_CHAR_NUM)s${RESET} ${GREEN}%s${RESET}\n", helpCommand, helpMessage; \
		} \
	} \
	{ lastLine = $$0 }' $(MAKEFILE_LIST)
	@echo ''