##### **EEG Streamer**

Written by Scott Kildall
With Sheet Metal Alchemist


##### **Description**
This is a modified version of TestLibMuseAndroid, which captures EEG data from the Muse EEG headset.

It integrates the OSCP5 libraries to stream EEG data via OSC to applications such as Processing.

Layout is designed for 1024x600 tablet devices, Android build is KitKat (4.4)


##### **Timing Packets**
Touching Forehead — gets sent from headset ever 150-250ms or so

##### **Current state**
Missing: Timing defaults, better layout options, efficiency-testing

##### **Release Notes**
**version 0.1:** Stable test build, sends OSC data for absolute alpha, beta, delta, gamma, theta waves. Shows connection status and battery life on Android.
Missing: Timing feedback, better layout options, right now is hard-coded to send information every 5 packets, but this is an arbitrary decision, ability to clear data fields like number of wave packets, etc., needs icon

**version 0.11:** 
Added OSC stream for horseshoe values:
OSC message is "/muse/elements/horseshoe"