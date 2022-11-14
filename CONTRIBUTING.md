# Welcome to Courier android contributing guide

## Getting Started

1. Clone the repo and open `courier-android` with Android Studio.
2. Open terminal navigate to root directory run 

```bash 
sh env-setup.sh
```

3. Navigate to `android/app/java/com.courier.example/Env`
4. Provide your FCM (Firebase Cloud Messaging) and Courier credentials
	- You can get your Courier keys [here][https://www.courier.com/docs/reference/auth/issue-token/]

From here, you are all set to start working on the package! 🙌

## Testing & Debugging

While developing, you can run the project from android studio to test your changes. To see
Any changes you make in your library code will be reflected in the example app everytime you rebuild the app.

To debug the Android package:
1. Open `android` in Android Studio
2. Click Debug

To start the packager:

connect your android device

check if your device is available under adb devices, run:

```sh
adb devices
```
you can write and run test cases in ``android/java/com.courier.android/CourierTests``
