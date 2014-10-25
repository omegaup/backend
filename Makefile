LIBINTERACTIVE_SOURCES := $(shell /usr/bin/find libinteractive/src/main -name *.scala)
COMMON_SOURCES := $(shell /usr/bin/find common/src/main -name *.scala)
RUNNER_SOURCES := $(shell /usr/bin/find runner/src/main -name *.scala)
GRADER_SOURCES := $(shell /usr/bin/find grader/src/main -name *.scala)

SCALA_VERSION := 2.10
OMEGAUP_VERSION :=  $(shell grep '^version' build.sbt | sed -e 's/.*"\(.*\)".*/\1/')
LIBINTERACTIVE_VERSION := $(shell grep '^version' libinteractive/build.sbt | sed -e 's/.*"\(.*\)".*/\1/')
RUNNER_JAR := runner/target/scala-$(SCALA_VERSION)/proguard/runner_$(SCALA_VERSION)-$(OMEGAUP_VERSION).jar
GRADER_JAR := grader/target/scala-$(SCALA_VERSION)/proguard/grader_$(SCALA_VERSION)-$(OMEGAUP_VERSION).jar
LIBINTERACTIVE_JAR := libinteractive/target/scala-$(SCALA_VERSION)/proguard/libinteractive_$(SCALA_VERSION)-$(LIBINTERACTIVE_VERSION).jar

all: ../bin/grader.jar ../bin/runner.jar ../bin/libinteractive.jar

clean:
	@rm $(RUNNER_JAR) $(GRADER_JAR) $(LIBINTERACTIVE_JAR)

../bin/grader.jar: $(GRADER_JAR)
	cp $< $@

../bin/runner.jar: $(RUNNER_JAR)
	cp $< $@

../bin/libinteractive.jar: $(LIBINTERACTIVE_JAR)
	cp $< $@

$(GRADER_JAR): $(LIBINTERACTIVE_SOURCES) $(COMMON_SOURCES) $(RUNNER_SOURCES) $(GRADER_SOURCES)
	sbt proguard:proguard

$(RUNNER_JAR): $(LIBINTERACTIVE_SOURCES) $(COMMON_SOURCES) $(RUNNER_SOURCES)
	sbt proguard:proguard

$(LIBINTERACTIVE_JAR): $(LIBINTERACTIVE_SOURCES)
	sbt libinteractive/proguard:proguard
