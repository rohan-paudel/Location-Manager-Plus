
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

### Summary

This code snippet provides a basic example of how to initialize and use the `LocationUtils` class to handle device location broadcasting and streaming in an Android application.

Feel free to customize this explanation further based on your specific needs! If you have any questions or need more details, let me know.

If you need any more help or have other questions, feel free to ask!

