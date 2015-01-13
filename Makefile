include definitions.mk

.PHONY: all clean
all: $(RUNNER_JAR) $(GRADER_JAR)

clean:
	@rm $(RUNNER_JAR) $(GRADER_JAR)

$(RUNNER_JAR) $(GRADER_JAR): $(BACKEND_SOURCES)
	sbt proguard:proguard
