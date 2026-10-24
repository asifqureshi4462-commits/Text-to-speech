The Gradle wrapper JAR is a binary that could not be generated in the offline authoring environment.
Generate it once with a local Gradle install:   gradle wrapper --gradle-version 8.9
The GitHub Actions workflow does not need it: it installs Gradle 8.9 itself.
