# Beacon Scanner

It find all beacons in your proximity and is colouring them based on their signal strength, means the app is a beacon scanner AND a beacon finder :).

Funny enough ... I actually wrote the app the find my beacons after I moved house.

## How to make it work

* clone the repo and build it with `sbt compile`
* connect your device with an USB cable
    * the device must run android 4.3 (android-18)
	* run `adb devices` to check that you can see the device and that it is the first/only one in the list
* install the app with `sbt install`

## TODOs

* look/grep for `@todo`
