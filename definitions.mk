SELF_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))

COMMON_SOURCES := $(shell /usr/bin/find $(SELF_DIR)/common/src/main -name *.scala)
RUNNER_SOURCES := $(shell /usr/bin/find $(SELF_DIR)/runner/src/main -name *.scala)
GRADER_SOURCES := $(shell /usr/bin/find $(SELF_DIR)/grader/src/main -name *.scala)
BACKEND_SOURCES := $(COMMON_SOURCES) $(RUNNER_SOURCES) $(GRADER_SOURCES)

SCALA_VERSION := $(shell grep '^\s*scalaVersion' $(SELF_DIR)/build.sbt | sed -e 's/.*"\([0-9]\+\.[0-9]\+\).*".*/\1/')
OMEGAUP_VERSION := $(shell git describe)
RUNNER_JAR := $(SELF_DIR)/runner/target/scala-$(SCALA_VERSION)/proguard/runner_$(SCALA_VERSION)-$(OMEGAUP_VERSION).jar
GRADER_JAR := $(SELF_DIR)/grader/target/scala-$(SCALA_VERSION)/proguard/grader_$(SCALA_VERSION)-$(OMEGAUP_VERSION).jar
