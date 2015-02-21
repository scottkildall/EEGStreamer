##### **EEG Streamer**

Written by Scott Kildall



##### **Description**
This is a modified version of TestLibMuseAndroid, which captures EEG data from the Muse EEG headset.

It integrates the OSCP5 libraries to stream EEG data via OSC to applications such as Processing.

Layout is designed for 1024x600 tablet devices, Android build is KitKat (4.4)


##### **Current state**
- Add time stamped packets, so we can see that we are receiving data
- Add code for other waveforms, theta etc
- Figure out connection percentages
- Figure out battery display