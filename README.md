# Spokestack Android

Speech recognition pipeline for Android.

## Status
[![Maven](https://maven-badges.herokuapp.com/maven-central/com.pylon/spokestack/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Ccom.pylon.spokestack)
[![Test](http://circleci-badges-max.herokuapp.com/img/pylon/spokestack-android?token=:circle-ci-token)](https://circleci.com/gh/pylon/spokestack-android)
[![Coverage](https://coveralls.io/repos/github/pylon/spokestack-android/badge.svg)](https://coveralls.io/github/pylon/spokestack-android)
[![Javadocs](https://www.javadoc.io/badge/com.pylon/spokestack.svg)](https://www.javadoc.io/doc/com.pylon/spokestack)

## Development
Maven is used for building/deployment, and the package is hosted at Maven
Central.

This package requires the [Android NDK](https://developer.android.com/ndk/guides/index.html)
to be installed and the `ANDROID_NDK_HOME` variable to be set. On OSX, this
variable is usually set to `~/Library/Android/sdk/ndk-bundle`.

### Testing/Coverage

```bash
mvn test jacoco:report
```

### Lint

```bash
mvn checkstyle:check
```

### Release
Remove the -SNAPSHOT suffix from the version in `pom.xml`, then run the
following command. This will deploy the package to Maven Central.

```bash
mvn deploy
```

Use the Maven release plugin to tag the release and advance the version number.

```bash
mvn release:clean release:prepare release:perform
```

## License

Copyright 2018 Pylon, Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
