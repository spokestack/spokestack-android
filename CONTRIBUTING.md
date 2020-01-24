## How to contribute to Spokestack Android
Interested in contributing to Spokestack Android? We appreciate all kinds of help!

### Pull Requests
We gladly welcome pull requests.

Before making any changes, we recommend opening an issue (if it doesn’t
already exist) and discussing your proposed changes. This will let us give
you advice on the proposed changes. If the changes are minor, then feel free
to make them without discussion.

### Development Process
To make changes, fork the repo. Write code in this fork, ensuring it passes the following
checks (it might be useful to put these in a pre-commit hook with `|| exit 1`
at the end of each line):

```bash
mvn checkstyle:check
mvn verify
```

These checks are performed automatically by CI whenever
a branch is pushed and must pass before a PR will be
merged.

#### Test Coverage
We strive to keep test coverage as high as possible using `jacoco`. The `pom.xml`
file includes a minumum coverage ratio; the previously mentioned checks will not
pass unless this minimum is met.

#### Native Code
This package requires the [Android NDK](https://developer.android.com/ndk/guides/index.html)
to be installed and the `ANDROID_HOME` and `ANDROID_NDK_HOME` variables to be
set. On OSX, `ANDROID_HOME` is usually set to `~/Library/Android/sdk` and
`ANDROID_NDK_HOME` is usually set to `~/Library/Android/sdk/ndk/<version>`.

`ANDROID_NDK_HOME` can also be specified in your local Maven `settings.xml` file
as the `android.ndk.path` property.
