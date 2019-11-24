# Spokestack Android

Spokestack provides an extensible speech recognition pipeline for the Android
platform. It includes a variety of built-in speech processors for Voice
Activity Detection (VAD) and Automatic Speech Recognition (ASR) via popular
speech recognition services, such as the Google Speech API and Bing Speech
API.

## Status
[![CircleCI](https://circleci.com/gh/spokestack/spokestack-android.svg?style=shield)](https://circleci.com/gh/spokestack/spokestack-android)
[![Coverage](https://coveralls.io/repos/github/spokestack/spokestack-android/badge.svg)](https://coveralls.io/github/spokestack/spokestack-android)
[![Maven](https://maven-badges.herokuapp.com/maven-central/com.pylon/spokestack/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Ccom.pylon.spokestack)
[![Javadocs](https://www.javadoc.io/badge/io.spokestack/spokestack.svg)](https://www.javadoc.io/doc/io.spokestack/spokestack)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

## Configuration

### Google Speech API

```java
SpeechPipeline pipeline = new SpeechPipeline.Builder()
    .setInputClass("io.spokestack.spokestack.android.MicrophoneInput")
    .addStageClass("io.spokestack.spokestack.libfvad.VADTrigger")
    .addStageClass("io.spokestack.spokestack.google.GoogleSpeechRecognizer")
    .setProperty("google-credentials", "<google-credentials>")
    .setProperty("locale", "en-US")
    .build();
```

This example creates an active speech recognition pipeline using the Google
Speech API that is triggered by VAD. The `google-credentials` parameter should
be the contents of a Google Cloud service account credentials file, in JSON
format. For more information, see the [documentation](https://cloud.google.com/speech/docs/streaming-recognize).
See the [javadoc](https://www.javadoc.io/doc/io.spokestack/spokestack) for
other component-specific configuration parameters.

### Microsoft Bing Speech API

```java
SpeechPipeline pipeline = new SpeechPipeline.Builder()
    .setInputClass("io.spokestack.spokestack.android.MicrophoneInput")
    .addStageClass("io.spokestack.spokestack.libfvad.VADTrigger")
    .addStageClass("io.spokestack.spokestack.microsoft.BingSpeechRecognizer")
    .setProperty("sample-rate", 16000)
    .setProperty("frame-width", 20)
    .setProperty("buffer-width", 300)
    .setProperty("vad-rise-delay", 100)
    .setProperty("vad-fall-delay", 500)
    .setProperty("bing-speech-api-key", "<bing-api-key>")
    .setProperty("locale", "fr-CA")
    .build();
```

This example creates a VAD-triggered pipeline with custom rise/fall delays
using the Microsoft Bing Speech API. For more information on this API, check
out the [documentation](https://azure.microsoft.com/en-us/services/cognitive-services/speech/).

### Wakeword Detection
```java
SpeechPipeline pipeline = new SpeechPipeline.Builder()
    .setInputClass("io.spokestack.spokestack.android.MicrophoneInput")
    .addStageClass("io.spokestack.spokestack.webrtc.AutomaticGainControl")
    .addStageClass("io.spokestack.spokestack.webrtc.VoiceActivityDetector")
    .addStageClass("io.spokestack.spokestack.wakeword.WakewordTrigger")
    .addStageClass("io.spokestack.spokestack.google.GoogleSpeechRecognizer")
    .setProperty("vad-fall-delay", 200)
    .setProperty("pre-emphasis", 0.97)
    .setProperty("wake-filter-path", "<tensorflow-lite-filter-path>")
    .setProperty("wake-encode-path", "<tensorflow-lite-encode-path>")
    .setProperty("wake-detect-path", "<tensorflow-lite-detect-path>")
    .setProperty("wake-smooth-length", 50)
    .setProperty("google-credentials", "<google-credentials>")
    .setProperty("locale", "en-US")
    .build();
```

This example creates a wakeword-triggered pipeline with the google speech
recognizer. The wakeword trigger uses three trained
[Tensorflow-Lite](https://www.tensorflow.org/lite/) models: a *filter* model
for spectrum preprocessing, an autoregressive encoder *encode* model, and a
*detect* decoder model for keyword classification. For more information on
the wakeword detector and its configuration parameters, click
[here](https://github.com/spokestack/spokestack-android/wiki/wakeword).

## Development
Maven is used for building/deployment, and the package is hosted at Maven
Central.

This package requires the [Android NDK](https://developer.android.com/ndk/guides/index.html)
to be installed and the `ANDROID_HOME` and `ANDROID_NDK_HOME` variables to be
set. On OSX, ANDROID_HOME is usually set to `~/Library/Android/sdk` and
ANDROID_NDK_HOME is usually set to `~/Library/Android/sdk/ndk/<version>`.

### Testing/Coverage

```bash
mvn test jacoco:report
```

### Lint

```bash
mvn checkstyle:check
```

### Release
On a non-master branch, remove the -SNAPSHOT suffix from the version in `pom.xml`, then run the
following command. This will deploy the package to Maven Central.

```bash
mvn deploy
```

Revert your above change using `git checkout .`, then use the Maven release plugin to tag the release and advance the version number.

```bash
mvn release:clean release:prepare release:perform
```

For additional information about releasing see http://maven.apache.org/maven-release/maven-release-plugin/
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
