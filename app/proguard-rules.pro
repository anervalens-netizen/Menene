# Media3 and AndroidX publish their own consumer rules.
# Keep only the DeviceAdmin receiver, which Android instantiates by class name.
-keep class ro.mehene.app.kiosk.MeheneDeviceAdminReceiver { *; }
-keep class ro.mehene.app.kiosk.BootReceiver { *; }
