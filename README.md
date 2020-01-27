# Spokestack Android

Spokestack provides an extensible speech recognition pipeline for the Android
platform. It includes a variety of built-in speech processors for Voice
Activity Detection (VAD) and Automatic Speech Recognition (ASR) via popular
speech recognition services such as the Google Speech API and Azure Speech
API.

See the [documentation](https://spokestack.io/docs) for a lot more information
than is in this brief introduction.

## Status
[![CircleCI](https://circleci.com/gh/spokestack/spokestack-android.svg?style=shield)](https://circleci.com/gh/spokestack/spokestack-android)
[![Coverage](https://coveralls.io/repos/github/spokestack/spokestack-android/badge.svg)](https://coveralls.io/github/spokestack/spokestack-android)
[ ![JCenter](https://api.bintray.com/packages/spokestack/io.spokestack/spokestack-android/images/download.svg) ](https://bintray.com/spokestack/io.spokestack/spokestack-android/_latestVersion)
[![Javadocs](https://javadoc.io/badge2/io.spokestack/spokestack-android/javadoc.svg)](https://javadoc.io/doc/io.spokestack/spokestack-android)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

## Configuration

### Google Speech API

```java
SpeechPipeline pipeline = new SpeechPipeline.Builder()
    .useProfile("io.spokestack.spokestack.profile.VADTriggerGoogleASR")
    .setProperty("google-credentials", "<google-credentials>")
    .setProperty("locale", "en-US")
    .build();
```

This example uses a pre-built profile to create a speech recognition pipeline triggered by VAD
that uses the Google Speech API for speech recognition. The `google-credentials` parameter should
be the contents of a Google Cloud service account credentials file, in JSON
format. For more information, see the [documentation](https://cloud.google.com/speech/docs/streaming-recognize).
See the [javadoc](https://javadoc.io/doc/io.spokestack/spokestack-android) for
other component-specific configuration parameters.

### Wakeword Detection
```java
SpeechPipeline pipeline = new SpeechPipeline.Builder()
    .useProfile("io.spokestack.spokestack.profile.TFWakewordGoogleASR")
    .setProperty("wake-filter-path", "<tensorflow-lite-filter-path>")
    .setProperty("wake-encode-path", "<tensorflow-lite-encode-path>")
    .setProperty("wake-detect-path", "<tensorflow-lite-detect-path>")
    .setProperty("wake-threshold", 0.85)
    .setProperty("google-credentials", "<google-credentials>")
    .setProperty("locale", "en-US")
    .build();
```

This example creates a wakeword-triggered pipeline with the Google Speech
recognizer. The wakeword trigger uses three trained
[TensorFlow Lite](https://www.tensorflow.org/lite/) models: a *filter* model
for spectrum preprocessing, an autoregressive encoder *encode* model, and a
*detect* decoder model for keyword classification. For more information on
the wakeword detector and its configuration parameters, click
[here](https://github.com/spokestack/spokestack-android/wiki/wakeword).

The `wake-threshold` property is set by the `TFWakewordGoogleASR` profile, but it is
overridden here to emphasize that properties set after a profile is applied (either directly
in the builder or by another profile) supersede those set by that profile.

To use the demo "Spokestack" wakeword, download the TensorFlow Lite models: [detect](https://d3dmqd7cy685il.cloudfront.net/model/wake/spokestack/detect.lite) | [encode](https://d3dmqd7cy685il.cloudfront.net/model/wake/spokestack/encode.lite) | [filter](https://d3dmqd7cy685il.cloudfront.net/model/wake/spokestack/filter.lite)

## Development
Maven is used for building/deployment, and the package is hosted at JCenter.

This package requires the [Android NDK](https://developer.android.com/ndk/guides/index.html)
to be installed and the `ANDROID_HOME` and `ANDROID_NDK_HOME` variables to be
set. On OSX, `ANDROID_HOME` is usually set to `~/Library/Android/sdk` and
`ANDROID_NDK_HOME` is usually set to `~/Library/Android/sdk/ndk/<version>`.

`ANDROID_NDK_HOME` can also be specified in your local Maven `settings.xml` file as the `android.ndk.path` property.

### Testing/Coverage

```bash
mvn test jacoco:report
```

### Lint

```bash
mvn checkstyle:check
```

### Release
Ensure that your Bintray credentials are in your user Maven `settings.xml`:

```xml
<servers>
    <server>
        <id>bintray-spokestack-io.spokestack</id>
        <username>username</username>
        <password>bintray_api_key</password>
    </server>
</servers>
```

On a non-master branch, remove the -SNAPSHOT suffix from the version in `pom.xml`, then run the
following command. This will deploy the package to Bintray and JCenter.

```bash
mvn deploy
```

Revert your above change using `git checkout .`, then use the Maven release plugin to tag the release and advance the version number.

```bash
mvn release:clean release:prepare release:perform
```

For additional information about releasing see http://maven.apache.org/maven-release/maven-release-plugin/

## License

Copyright 2020 Spokestack, Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
