# micdroid
Original repo on my [personal gitea](https://git.javalsai.dynv6.net/micdroid/android-app/)

This is an app that can record audio with configurable block size and can stream it into a tcp stream with perfect quality. Simple yet effective, you can also stream it to multiple streams at the same time.

# Notes
This is indev quality code and there's a long way to go, but it's enought to be used for now, there's a lot of hard-coded parameters and behaviour for now:
* Audio specs are rate=40800, channels=1 (mono), default mic input (no chooser) and format=pcm16bit (equivalent to s16le). Assuming the rate refers to uits (pcm16) per second and not bytes or other thing, that gives $unitSize \cdot rate \cdot channels$ of bandwidth per stream (you can just multiply this), which equals ~79.7KiB/s ~= 0.08MiB/s.
* Stopping any of the streams causes the app to crash, take it as a way to close it for now.
* Once a stream is opened, you can't close it on the app.

# Usage
First select the buffer size on the bottom left input, if it's smaller than what your OS allows you will be able to change it (thankfully), on local networks just 5K works good for me, but you might be able to go smaller and way larger, just keep in mind that the bigger this is the bigger delay you'll get, but the stream will be more reliable.

Once you're done press the mic icon, it will try to open a mic stream and it should turn green (tap it to toggle mute).

Then, the connect button should become avaliable, just specify the host and port on the input and click connect, it will try to open a socket and list it as an active stream.

To get the stream there will be a linux app, for for now you can just use `pw-play --channels 1 --format s16 --rate 40800 -` (I think you can pass it directly to other nodes with `pw-cat`).

# Dev Notes
Given https://developer.android.com/reference/android/media/AudioRecord, 44100Hz is the only guaranteed rate to work on all devices, but we sould do some detection or input.
> int: the sample rate expressed in Hertz. 44100Hz is currently the only rate that is guaranteed to work on all devices, but other rates such as 22050, 16000, and 11025 may work on some devices. AudioFormat#SAMPLE_RATE_UNSPECIFIED means to use a route-dependent value which is usually the sample rate of the source. getSampleRate() can be used to retrieve the actual sample rate chosen.
