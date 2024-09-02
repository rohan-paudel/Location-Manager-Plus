
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
