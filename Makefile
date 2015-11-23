SELF_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
include definitions.mk

.PHONY: all clean
all: $(RUNNER_JAR) $(GRADER_JAR)

clean:
	@rm $(RUNNER_JAR) $(GRADER_JAR)

$(RUNNER_JAR): $(BACKEND_SOURCES)
	sbt update proguard:proguard

$(GRADER_JAR): $(RUNNER_JAR)
