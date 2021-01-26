<a href="https://www.spokestack.io/docs/android/getting-started" title="Getting Started with Android + Spokestack""><img src="images/spokestack-android.png" alt="Spokestack Android"></a>

[![CircleCI](https://circleci.com/gh/spokestack/spokestack-android.svg?style=shield)](https://circleci.com/gh/spokestack/spokestack-android)
[![Coverage](https://coveralls.io/repos/github/spokestack/spokestack-android/badge.svg)](https://coveralls.io/github/spokestack/spokestack-android)
[ ![JCenter](https://api.bintray.com/packages/spokestack/io.spokestack/spokestack-android/images/download.svg) ](https://bintray.com/spokestack/io.spokestack/spokestack-android/_latestVersion)
[![Javadocs](https://javadoc.io/badge2/io.spokestack/spokestack-android/javadoc.svg)](https://javadoc.io/doc/io.spokestack/spokestack-android)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

Spokestack is an all-in-one solution for mobile voice interfaces on Android. It provides every piece of the speech processing puzzle, including voice activity detection, wakeword detection, speech recognition, natural language understanding (NLU), and speech synthesis (TTS). Under its default configuration (on newer Android devices), everything except TTS happens directly on the mobile device—no communication with the cloud means faster results and better privacy.

And Android isn't the only platform it supports!

* [**iOS**](https://github.com/spokestack/spokestack-ios)
* [**React Native**](https://github.com/spokestack/react-native-spokestack)
* [**Python**](https://github.com/spokestack/spokestack-python)

Creating a free account at [spokestack.io](https://spokestack.io) lets you train your own NLU models and test out TTS without adding code to your app. We can even train a custom wakeword and TTS voice for you, ensuring that your app's voice is unique and memorable.

For a brief introduction, read on, but for more detailed guides, see the following:

* [**Quickstart**](https://www.spokestack.io/docs/Android/getting-started)
* [**Configuration Guide**](https://www.spokestack.io/docs/Android/turnkey-configuration)
* [**API Reference**](https://www.javadoc.io/doc/io.spokestack/spokestack-android/latest/index.html)
* [**Tutorials**](https://www.spokestack.io/tutorials)


## Installation

Add the following to your app's `build.gradle`:

```groovy
android {

    // ...

    compileOptions {
          sourceCompatibility JavaVersion.VERSION_1_8
          targetCompatibility JavaVersion.VERSION_1_8
    }
}

// ...

dependencies {
    // ...

    implementation 'io.spokestack:spokestack-android:11.0.0'

    // for TensorFlow Lite-powered wakeword detection and/or NLU, add this one too
    implementation 'org.tensorflow:tensorflow-lite:2.3.0'

    // for automatic playback of TTS audio
    implementation 'androidx.media:media:1.2.0'
    implementation 'com.google.android.exoplayer:exoplayer-core:2.11.7'
}
```

## Usage

See the [quickstart guide](https://www.spokestack.io/docs/Android/getting-started) for more information, but here's the 30-second version of setup:

1. You'll need to list the `INTERNET` permission in your manifest and request the `RECORD_AUDIO` permission at runtime. See our [skeleton project](https://github.com/spokestack/android-skeleton) for an example of this.
1. Add the following code somewhere, probably in an `Activity` if you're just starting out:

```kotlin
private lateinit var spokestack: Spokestack

// ...
spokestack = Spokestack.Builder()
    .setProperty("wake-detect-path", "$cacheDir/detect.tflite")
    .setProperty("wake-encode-path", "$cacheDir/encode.tflite")
    .setProperty("wake-filter-path", "$cacheDir/filter.tflite")
    .setProperty("nlu-model-path", "$cacheDir/nlu.tflite")
    .setProperty("nlu-metadata-path", "$cacheDir/metadata.json")
    .setProperty("wordpiece-vocab-path", "$cacheDir/vocab.txt")
    .setProperty("spokestack-id", "your-client-id")
    .setProperty("spokestack-secret", "your-secret-key")
    // `applicationContext` is available inside all `Activity`s
    .withAndroidContext(applicationContext)
    // see below; `listener` here inherits from `SpokestackAdapter`
    .addListener(listener)
    .build()

// ...

// starting the pipeline makes Spokestack listen for the wakeword
spokestack.start()
```

This example assumes you're storing wakeword and NLU models in your app's [cache directory](https://developer.android.com/reference/android/content/Context#getCacheDir()); again, see the skeleton project for an example of decompressing these files from the assets bundle into this directory.

To use the demo "Spokestack" wakeword, download the TensorFlow Lite models: [detect](https://d3dmqd7cy685il.cloudfront.net/model/wake/spokestack/detect.tflite) | [encode](https://d3dmqd7cy685il.cloudfront.net/model/wake/spokestack/encode.tflite) | [filter](https://d3dmqd7cy685il.cloudfront.net/model/wake/spokestack/filter.tflite)

If you don't want to bother with that yet, just disable wakeword detection and NLU, and you can leave out all the file paths above:

```kotlin
spokestack = Spokestack.Builder()
    .withoutWakeword()
    .withoutNlu()
    // ...
    .build()
```

In this case, you'll still need to `start()` Spokestack as above, but you'll also want to create a button somewhere that calls `spokestack.activate()` when pressed; this starts ASR, which transcribes user speech.

Note also the `addListener()` line during setup. Speech processing happens continuously on a background thread, so your app needs a way to find out when the user has spoken to it. Important events are delivered via events to a subclass of `SpokestackAdapter`. Your subclass can override as many of the following event methods as you like. Choosing to not implement one won't break anything; you just won't receive those events.

* `speechEvent(SpeechContext.Event, SpeechContext)`: This communicates events from the speech pipeline, including everything from notifications that ASR has been activated/deactivated to partial and complete transcripts of user speech.
* `nluResult(NLUResult)`: When the NLU is enabled, user speech is automatically sent through NLU for classification. You'll want the results of that classification to help your app decide what to do next.
* `ttsEvent(TTSEvent)`: If you're managing TTS playback yourself, you'll want to know when speech you've synthesized is ready to play (the `AUDIO_AVAILABLE` event); even if you're not, the `PLAYBACK_COMPLETE` event may be helpful if you want to automatically reactivate the microphone after your app reads a response.
* `trace(SpokestackModule, String)`: This combines log/trace messages from every Spokestack module. Some modules include trace events in their own event methods, but each of those events is also sent here.
* `error(SpokestackModule, Throwable)`: This combines errors from every Spokestack module. Some modules include error events in their own event methods, but each of those events is also sent here.

The [quickstart guide](https://www.spokestack.io/docs/Android/getting-started) contains sample implementations of most of these methods.

As we mentioned, classification is handled automatically if NLU is enabled, so the main methods you need to know about while Spokestack is running are:

* `start()/stop()`: Starts/stops the pipeline. While running, Spokestack uses the microphone to listen for your app's wakeword unless wakeword is disabled, in which case ASR must be activated another way. The pipeline should be stopped when Spokestack is no longer needed (or when the app is suspended) to free resources.
* `activate()/deactivate()`: Activates/deactivates ASR, which listens to and transcribes what the user says.
* `synthesize(SynthesisRequest)`: Sends text to Spokestack's cloud TTS service to be synthesized as audio. Under the default configuration, this audio will be played automatically when available.

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
