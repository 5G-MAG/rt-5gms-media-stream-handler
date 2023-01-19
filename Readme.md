# 5GMS-MediaStreamHandler

## Installation
1. Open the project in Android Studio
2. Build an APK or run the project directly on an Android device

## Workflow

1. Single index.json file that only contains provisioningSessionIDs
2. Once the app starts, the provisioningSessionIDs are populated to the selection dropdown
3. An entry in the dropdown is selected
4. The MediaStreamHandler sends an IPC message to the Media Session Handler Background Service including the selected provisioningSessionID
5. The Media Session Handler Background Service contacts the AF (or a mocked REST server) using the route syntax defined in 26.512 11.2.2: {apiRoot}/3gpp-m5/{apiVersion}/service-access-information/ adding the {provisioningSessionId}
6. Once it has received the Service Access Information it sends back an IPC message to the Media Stream Handler with the whole Service Access Information
7. The Media Stream Handler parses the Service Access Information
8. The Media Stream Handler plays the MPD defined in mediaPlayerEntry