
val locationUtils = LocationUtils(
    this, true, true
)
locationUtils.broadcastDeviceLocation()
locationUtils.broadcastDeviceLocationStream()
locationUtils.stopBroadcastingDeviceLocationStream()
print(LocationDataHolder.locationLiveData.value?.latitude)
