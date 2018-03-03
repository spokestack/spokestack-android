# Spokestack Android

Speech recognition pipeline for Android.

## Status
[![Maven](https://maven-badges.herokuapp.com/maven-central/com.pylon/spokestack/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Ccom.pylon.spokestack)
[![Test](http://circleci-badges-max.herokuapp.com/img/pylon/spokestack-android?token=:circle-ci-token)](https://circleci.com/gh/pylon/spokestack-android)
[![Coverage](https://coveralls.io/repos/github/pylon/spokestack-android/badge.svg)](https://coveralls.io/github/pylon/spokestack-android)

The API reference is available [here](http://www.javadoc.io/doc/com.pylon/spokestack).

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

```bash
mvn release:clean release:prepare release:perform
mvn deploy
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
