package com.mvt.derpcast.device;

import com.connectsdk.device.ConnectableDevice;

public interface DeviceAddedListener {
    public void onDeviceAdded(ConnectableDevice device);
}