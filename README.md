#Sunshine (Wearable Watch Face)
# Project 6 ("Go Ubiquitous") of the Udacity Android Developer Nanodegree

Synchronizes weather information from OpenWeatherMap on Android Phones, Tablets, and Wearables.

###### Detail
Project 6 specifically focuses on creating the watch face for wearables so that users can access Sunshine's weather information at a glance.  The mobile app utilizes the GoogleAPIClient to send weather info via the data layer. The watch face service receives the data and draws watch face with time, date, and weather.

# Screen
![Screen_after_response](https://github.com/ashokslsk/Sunshine-Wear-P6/blob/master/Screens/light.png)


Getting Started
-------------
1. Weather information is acquired through openweathermap.org API. An account must be created on this site and an API Key must be requested.
2. A Google developer account is required along with the following actions:
  * A project must be created in your google developer [console] (https://console.developers.google.com)
  * Google Cloud Messaging API must be requested for this project
  * Configuration file "google-services.json" must be requested.  Follow instructions [here] (https://developers.google.com/cloud-messaging/android/client) to request file.

Installation
------------
1. Clone the GitHub repository
2. gradle.properties must exist in "Sunshine" and have this line of code, with your own API KEY in quotation marks

 ```MyOpenWeatherMapApiKey = "PUT_MY_API_KEY_HERE"```

3. Place "google-services.json" file in Sunshine/app directory.
4. Add "local.properties" file into the Sunshine base directory.  Add this line with the path to your Android sdk location:
 ```sdk.dir = "PUT_MY_PATH_TO_SDK_HERE"```

5. This project uses the Gradle build system.  To build this project, use the gradlew build" at command line or open project in Android Studio and build. 


Getting Started
---------------
This sample uses the Gradle build system.  To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Support
-------

- Google+ Community: https://plus.google.com/communities/105153134372062985968
- Stack Overflow: http://stackoverflow.com/questions/tagged/android

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.

**License**
```
-------
Copyright (c) 2016 Ashok Kumar S

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
