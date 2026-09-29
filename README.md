<p align="center">
  <img src=".github/banner.svg" width="100%" alt="Reference Tools · 5G Media Streaming (5GMS): 5GMS Media Stream Handler">
</p>

<p align="center">
  An Android library implementing the downlink 5GMS Media Stream Handler (Media Player), a 5GMS
  Client component defined in ETSI TS 126.501, with the M7d interface of TS 26.512.
</p>

<p align="center">
  <img alt="Status: under development"
    src="https://img.shields.io/badge/Status-Under%20Development-e67e22">
  <a href="https://github.com/5G-MAG/rt-5gms-media-stream-handler/releases"><img alt="Version"
    src="https://img.shields.io/github/v/release/5G-MAG/rt-5gms-media-stream-handler?label=Version"></a>
  <a href="License.md"><img alt="License: 5G-MAG Public License v1.0"
    src="https://img.shields.io/badge/License-5G--MAG%20PL%20v1.0-blue"></a>
</p>

<p align="center">
  <a href="https://www.5g-mag.com/reference-tools/5gms/">Project page</a> &nbsp;&middot;&nbsp;
  <a href="https://github.com/5G-MAG/rt-5gms-media-stream-handler/issues">Issues</a> &nbsp;&middot;&nbsp;
  <a href="https://www.5g-mag.com/contributing">Contributing</a>
</p>

---

## At a glance

|  |  |
|---|---|
| **Implements** | ETSI TS 126.501; TS 26.512 M7d interface (no versions stated in the repository) |
| **Part of** | [5G Media Streaming (5GMS)](https://www.5g-mag.com/reference-tools/5gms/), alongside [cmcd-toolkit](https://github.com/5G-MAG/cmcd-toolkit), [rt-5gc-service-consumers](https://github.com/5G-MAG/rt-5gc-service-consumers), [rt-5gms-application](https://github.com/5G-MAG/rt-5gms-application), [rt-5gms-application-function](https://github.com/5G-MAG/rt-5gms-application-function), [rt-5gms-application-provider](https://github.com/5G-MAG/rt-5gms-application-provider), [rt-5gms-application-server](https://github.com/5G-MAG/rt-5gms-application-server), [rt-5gms-common-android-library](https://github.com/5G-MAG/rt-5gms-common-android-library), [rt-5gms-examples](https://github.com/5G-MAG/rt-5gms-examples), [rt-5gms-media-session-handler](https://github.com/5G-MAG/rt-5gms-media-session-handler), [rt-cmmf-encoder](https://github.com/5G-MAG/rt-cmmf-encoder), [rt-media-origin](https://github.com/5G-MAG/rt-media-origin) |

## Introduction

This repository is the downlink 5GMS Media Stream Handler (Media Player), an Android library. It
plays back and renders a media presentation based on a media player entry, exposes basic controls
such as play, pause, seek and stop to the 5GMSd-Aware Application, and consumes media from the
[5GMSd AS](https://github.com/5G-MAG/rt-5gms-application-server) at reference point M4d. The
[5GMSd-Aware Application](https://github.com/5G-MAG/rt-5gms-application) includes it as an Android
library.

More information is on the [project page](https://www.5g-mag.com/reference-tools/5gms/).

### About the implementation

The library includes the [ExoPlayer](https://github.com/google/ExoPlayer), in its AndroidX Media3
form, as a dependency, and implements an adapter around the ExoPlayer APIs that exposes the M7d
interface functionality of TS 26.512. A `MediaSessionHandlerAdapter` establishes a Messenger
connection to the [Media Session Handler](https://github.com/5G-MAG/rt-5gms-media-session-handler).

## Specification

The repository names ETSI TS 126.501 and TS 26.512 but no version of either.

Clause-by-clause coverage, and what is still absent, is recorded on the project page rather than
here: <https://www.5g-mag.com/reference-tools/5gms/>

## Downloading

Release versions are on the [releases](https://github.com/5G-MAG/rt-5gms-media-stream-handler/releases)
page. The library is also published as a Maven package on the
[5G-MAG GitHub Packages](https://github.com/orgs/5G-MAG/packages?repo_name=rt-5gms-media-stream-handler).

To get the source, clone the repository:

```
cd ~
git clone https://github.com/5G-MAG/rt-5gms-media-stream-handler.git
```

## Building

To generate the `aar` bundles, run this command from the repository root:

````
./gradlew assemble
````

The `aar` bundles are written to `app/build/outputs/aar/`. A project can include one by specifying
the path to the bundle.

## Installing

The preferred way to include the Media Stream Handler is from a local or remote Maven repository.

### Publish to local Maven repository

To include the library from a local Maven repository, first publish it locally:

````
./gradlew publishToMavenLocal
````

### Include from local Maven repository

To include the 5GMS Media Stream Handler from a local Maven repository, make the two changes below.
The other 5G-MAG client-side projects already include them; with those, the Media Stream Handler
only needs to be [published to the local Maven repository](#publish-to-local-maven-repository).

#### 1. Add `mavenLocal()` to your project gradle file

````
dependencyResolutionManagement {
   repositories {
   mavenLocal()
   }
}
````

#### 2. Include the 5GMS Media Stream Handler in your module gradle file

Replace the version number in the example below with the version you are using, e.g. `1.2.0`
instead of `1.0.0`.

````
dependencies {
    // 5GMAG
    implementation 'com.fivegmag:a5gmsmediastreamhandler:1.0.0'
}
````

## Development

This project follows the
[Gitflow workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow).
The `development` branch is the integration branch for new features, so switch to it before starting
work on a new feature.

## Contributing

Contributions are welcome. How to raise an issue, fork the repository and open a pull request, and
the Contributor License Agreement required before code can be merged, are described at
<https://www.5g-mag.com/contributing>.

## License

Distributed under the 5G-MAG Public License v1.0. See [License.md](License.md).
