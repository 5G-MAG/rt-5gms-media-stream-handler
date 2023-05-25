# 5G-MAG Reference Tools: 5GMSd Media Stream Handler

This repository holds the 5GMSd Media Stream Handler implementation of the 5G-MAG Reference Tools.

## Introduction

The 5GMSd Media Stream Handler is a 5GMSd client component that forms part of the 5G Media Services framework as defined in
ETSI TS 126.501. A Media Stream Handler (Media Player) enables playback and rendering of a media
presentation based on a media player entry and exposing some basic controls such as play, pause,
seek, stop to the 5GMSd-Aware Application. It consumes media from the 5GMSd AS at reference point M4d.

### About the implementation

The 5GMSd Media Stream Handler is an Android library that includes
the [ExoPlayer](https://github.com/google/ExoPlayer) as a dependency. The 5GMSd Media Stream Handler
implements an adapter around the ExoPlayer APIs to expose TS.26.512 M7d interface functionality.
Moreover, a `MediaSessionHandlerAdapter` establishes a Messenger connection to the [Media Session
Handler](https://github.com/5G-MAG/rt-5gms-media-session-handler). The 5GMSd Media Stream Handler is included as an Android library by 5GMSd-Aware Application.

### Specifications

A list of specification related to this repository is available in the [Standards Wiki](https://github.com/5G-MAG/Standards/wiki/5G-Downlink-Media-Streaming-Architecture-(5GMSd):-Relevant-Specifications).

## Downloading

Release versions can be downloaded from
the [releases](https://github.com/5G-MAG/rt-5gms-media-stream-handler/releases) page.

We also publish this library as a Maven package on
the [5G-MAG Github Packages](https://github.com/orgs/5G-MAG/packages?repo_name=rt-5gms-media-stream-handler)
.

The source can be obtained by cloning the github repository.

```
cd ~
git clone https://github.com/5G-MAG/rt-5gms-media-stream-handler.git
```

## Building

Call the following command in order to generate the `aar` bundles.

````
./gradlew assemble
````

The resulting `aar` bundles can be found in `app/build/outputs/aar/` and can be included in your
project by specifying the path to the bundle.

## Publish to local Maven Repository

The preferred way to include the 5GMSd Media Stream Handler is via a local or remote Maven repository (see
below). To include the library from a local Maven repository we need to publish it locally first:

````
./gradlew publishToMavenLocal
````

## Include from local Maven Repository

To include the 5GMSd Common Library from a local Maven repository apply the following changes.

Note: When using the other 5G-MAG client-side projects the changes below are already included. In
this case the 5GMSd Media Stream Handler only needs to be published to the local Maven repository (see
above).

#### 1. Add `mavenLocal()` to your project gradle file

````
dependencyResolutionManagement {
   repositories {
   mavenLocal()
   }
}
````

#### 2. Include the 5GMSd Common Library in your module gradle file

````
dependencies {
    // 5GMAG
    implementation 'com.fivegmag:a5gmsmediastreamhandler:1.0.0'
}
````

## Development

This project follows
the [Gitflow workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)
. The `development`
branch of this project serves as an integration branch for new features. Consequently, please make
sure to switch to the `development`
branch before starting the implementation of a new feature.

