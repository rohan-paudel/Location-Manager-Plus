
## LocationUtils Usage

This code snippet demonstrates how to use the `LocationUtils` class to broadcast device location and manage location streams.

### Initialization

First, we initialize the `LocationUtils` object with the current context and two boolean parameters:

```kotlin
val locationUtils = LocationUtils(
    this, true, true
)
```

## Broadcasting Device Location

To start broadcasting the device’s location, we call the `broadcastDeviceLocation` method:

```kotlin
locationUtils.broadcastDeviceLocation()
```

## Managing Location Streams

To broadcast the device’s location as a stream, we use the `broadcastDeviceLocationStream` method:

```kotlin
locationUtils.broadcastDeviceLocationStream()
```
To stop broadcasting the device’s location stream, we call the `stopBroadcastingDeviceLocationStream` method:

```kotlin
locationUtils.stopBroadcastingDeviceLocationStream()
```

## Accessing Location Data

Finally, to access the current latitude from the `LocationDataHolder`, we use:

```kotlin
print(LocationDataHolder.locationLiveData.value?.latitude)
```
This will print the current latitude value if available.

