MAKEFLAGS += --always-make
SHELL := /bin/bash

help:
	@echo "Tasks:"
	@grep -E '^[a-zA-Z][a-zA-Z0-9_.-]*:.*?' $(MAKEFILE_LIST) \
		| awk -F':' '{printf "  \033[36m%-20s\033[0m\n", $$1}' \
		| uniq

###############################################################################
### build
###############################################################################

build:
	./gradlew publishToMavenLocal \
		--no-daemon \
		--warning-mode=none \
		--parallel \
		--max-workers 3 \
		-x openapi \
		-P "skip.signing" \
		-Dorg.gradle.jvmargs="-Xmx2g"

clean:
	./gradlew clean
