package com.courier.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.courier.android.models.*
import com.courier.android.modules.*
import com.courier.android.repositories.InboxRepository
import com.courier.android.utils.trackNotification
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@RunWith(AndroidJUnit4::class)
class CourierTests {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        Courier.initialize(context)
    }

    private suspend fun signUserIn(shouldUseJWT: Boolean = true) {

        // Add listener. Just to make sure the listener is working
        val listener = Courier.shared.addAuthenticationListener { userId ->
            print(userId ?: "No userId found")
        }

        // Sign the user out, if there is one
        Courier.shared.signOut()

        // Check if we need to use the access token
        var accessToken = Env.COURIER_AUTH_KEY

        if (shouldUseJWT) {

            accessToken = ExampleServer().generateJWT(
                authKey = Env.COURIER_AUTH_KEY,
                userId = Env.COURIER_USER_ID
            )

        }

        // Sign the user in
        Courier.shared.signIn(
            accessToken = accessToken,
            clientKey = Env.COURIER_CLIENT_KEY,
            userId = Env.COURIER_USER_ID
        )

        // Remove the listener
        listener.remove()

        // Check values
        assertEquals(Courier.shared.accessToken, accessToken)
        assertEquals(Courier.shared.userId, Env.COURIER_USER_ID)
        assertEquals(Courier.shared.clientKey, Env.COURIER_CLIENT_KEY)

    }

    private suspend fun loadInboxMessages() = suspendCoroutine { cont ->

        var canPage = true
        var error: String? = null

        val listener = Courier.shared.addInboxListener(
            onInitialLoad = {
                print("Loading")
            },
            onError = { e ->
                print(e)
                error = e.message
            },
            onMessagesChanged = { _, _, _, canPaginate ->

                if (canPaginate) {

                    Courier.shared.fetchNextPageOfMessages(
                        onSuccess = {
                            print("New messages fetched")
                        },
                        onFailure = { e ->
                            error = e.message
                        }
                    )

                }

                canPage = canPaginate

            }
        )

        while (canPage) {
            // Hold
        }

        if (error != null) {
            cont.resumeWithException(Exception())
            return@suspendCoroutine
        }

        // Return the listener
        cont.resume(listener)

    }

    @Test
    fun setFCMTokenBeforeUser() = runBlocking {

        print("🔬 Setting FCM Token before User")

        val token = "8af92b6e587cbadf3c2e3d1a"

        Courier.shared.signOut()

        Courier.shared.setFCMToken(token = token)

        val fcmToken0 = Courier.shared.fcmToken
        val fcmToken1 = Courier.shared.getToken(provider = "firebase-fcm")
        val fcmToken2 = Courier.shared.getToken(provider = CourierPushProvider.FIREBASE_FCM)

        assertEquals(fcmToken0, token)
        assertEquals(fcmToken1, token)
        assertEquals(fcmToken2, token)

        assertEquals(Courier.shared.userId, null)
        assertEquals(Courier.shared.accessToken, null)

    }

    @Test
    fun signInWithAuthKey() = runBlocking {

        print("🔬 Setting credentials")

        val options = FirebaseOptions.Builder().apply {
            setApiKey(Env.FIREBASE_API_KEY)
            setApplicationId(Env.FIREBASE_APP_ID)
            setProjectId(Env.FIREBASE_PROJECT_ID)
            setGcmSenderId(Env.FIREBASE_GCM_SENDER_ID)
        }.build()

        FirebaseApp.initializeApp(context, options)

        // Firebase is started
        val app = FirebaseApp.getInstance()
        assertEquals(app.options.apiKey, Env.FIREBASE_API_KEY)

        Courier.shared.signOut()

        Courier.shared.signIn(
            accessToken = Env.COURIER_AUTH_KEY,
            userId = Env.COURIER_USER_ID,
            clientKey = Env.COURIER_CLIENT_KEY
        )

        assertEquals(Courier.shared.userId, Env.COURIER_USER_ID)
        assertEquals(Courier.shared.clientKey, Env.COURIER_CLIENT_KEY)
        assertEquals(Courier.shared.accessToken, Env.COURIER_AUTH_KEY)

        val fcmToken0 = Courier.shared.fcmToken
        val fcmToken1 = Courier.shared.getToken(provider = "firebase-fcm")
        val fcmToken2 = Courier.shared.getToken(provider = CourierPushProvider.FIREBASE_FCM)

        assertNotNull(fcmToken0)
        assertNotNull(fcmToken1)
        assertNotNull(fcmToken2)

    }

    @Test
    fun signInWithJWT() = runBlocking {

        print("🔬 Setting credentials with JWT")

        val jwt = ExampleServer().generateJWT(
            authKey = Env.COURIER_AUTH_KEY,
            userId = Env.COURIER_USER_ID
        )

        Courier.shared.signOut()

        Courier.shared.signIn(
            accessToken = jwt,
            userId = Env.COURIER_USER_ID,
            clientKey = Env.COURIER_CLIENT_KEY
        )

        assertEquals(Courier.shared.userId, Env.COURIER_USER_ID)
        assertEquals(Courier.shared.clientKey, Env.COURIER_CLIENT_KEY)
        assertEquals(Courier.shared.accessToken, jwt)

    }

    @Test
    fun setTokens() = runBlocking {

        print("🔬 Setting Messaging Tokens")

        val fcm = "6b7a8c9d1e2f3a4g5h6i7j8k"
        val expo = "a1b2c3d4e5f6g7h8i9j0k1l"

        signUserIn()

        Courier.shared.setFCMToken(token = fcm)
        Courier.shared.setToken(provider = CourierPushProvider.EXPO, token = expo)

        assertEquals(Courier.shared.fcmToken, fcm)
        assertEquals(Courier.shared.getToken(provider = CourierPushProvider.EXPO), expo)

    }

    @Test
    fun sendPush() = runBlocking {

        print("🔬 Sending Push")

        val requestId = Courier.shared.sendMessage(
            authKey = Env.COURIER_AUTH_KEY,
            userIds = listOf(Env.COURIER_USER_ID),
            title = "🐤 Chirp Chirp!",
            body = "Message sent from Android Studio tests",
            channels = listOf(
                FirebaseCloudMessagingChannel(
                    data = mapOf(
                        "DATA_FCM" to "YOUR_CUSTOM_VALUE"
                    ),
                    fcmData = mapOf(
                        "FCM_CUSTOM_KEY" to "YOUR_CUSTOM_VALUE"
                    ),
                    aps = mapOf(
                        "sound" to "ping.aiff",
                        "badge" to 123,
                        "APNS_CUSTOM_NUMBER" to 456,
                        "APNS_CUSTOM_BOOLEAN" to true,
                        "APNS_CUSTOM_KEY" to "YOUR_CUSTOM_VALUE"
                    )
                )
            ),
        )

        print("Request ID: $requestId")
        assertEquals(requestId.isEmpty(), false)

    }

    @Test
    fun trackPush() = runBlocking {

        print("🔬 Tracking Push")

        val message = RemoteMessage.Builder(context.packageName)
            .addData("trackingUrl", "https://af6303be-0e1e-40b5-bb80-e1d9299cccff.ct0.app/t/tzgspbr4jcmcy1qkhw96m0034bvy")
            .build()

        Courier.shared.trackNotification(
            message = message,
            event = CourierPushEvent.DELIVERED
        )

        Courier.shared.trackNotification(
            message = message,
            event = CourierPushEvent.CLICKED
        )

        print("Message tracked")

    }

    @Test
    fun setupInbox() = runBlocking {

        print("🔬 Testing Inbox Get Messages")

        signUserIn()

        val listener = loadInboxMessages()

        assertNotNull(Courier.shared.inboxMessages)

        listener.remove()

    }

    private suspend fun sendInboxMessage(): String {

        return Courier.shared.sendMessage(
            authKey = Env.COURIER_AUTH_KEY,
            userIds = listOf(Env.COURIER_USER_ID),
            title = "🐤 Inbox Message",
            body = "Message sent from Android Studio tests",
            channels = listOf(
                CourierInboxChannel(
                    elements = listOf(
                        CourierElement(
                            type = "action",
                            content = "Button 1",
                            data = mapOf(
                                "CUSTOM_KEY" to "YOUR_CUSTOM_VALUE"
                            )
                        ),
                        CourierElement(
                            type = "action",
                            content = "Button 2",
                            data = mapOf(
                                "CUSTOM_KEY" to "YOUR_CUSTOM_VALUE"
                            )
                        )
                    ),
                    data = mapOf(
                        "DATA_INBOX" to "YOUR_CUSTOM_VALUE",
                    )
                )
            )
        )

    }

    @Test
    fun setPaginationLimits() = runBlocking {

        print("🔬 Setting Inbox Pagination Limit")

        Courier.shared.inboxPaginationLimit = 10
        assertEquals(Courier.shared.inboxPaginationLimit, 10)

        Courier.shared.inboxPaginationLimit = -1000
        assertEquals(Courier.shared.inboxPaginationLimit, 1)

        Courier.shared.inboxPaginationLimit = 1000
        assertEquals(Courier.shared.inboxPaginationLimit, 100)

    }

    @Test
    fun getPreferences() = runBlocking {

        print("🔬 Get All User Preferences")

        signUserIn()

        val preferences = Courier.shared.getUserPreferences()

        print(preferences)

    }

    @Test
    fun getPreference() = runBlocking {

        print("🔬 Get Topic")

        signUserIn()

        val topic = Courier.shared.getUserPreferenceTopic(
            topicId = "VFPW1YD8Y64FRYNVQCKC9QFQCFVF",
        )

        print(topic)

    }

    @Test
    fun updatePreference() = runBlocking {

        print("🔬 Update Topic")

        signUserIn()

        Courier.shared.putUserPreferenceTopic(
            topicId = "VFPW1YD8Y64FRYNVQCKC9QFQCFVF",
            status = CourierPreferenceStatus.OPTED_IN,
            hasCustomRouting = true,
            customRouting = listOf(CourierPreferenceChannel.SMS, CourierPreferenceChannel.PUSH)
        )

    }

    @Test
    fun signOut() = runBlocking {

        print("🔬 Signing Out")

        Courier.shared.signOut()

        assertNotNull(Courier.shared.fcmToken)

        assertEquals(Courier.shared.userId, null)
        assertEquals(Courier.shared.accessToken, null)
        assertEquals(Courier.shared.clientKey, null)

    }

    @Test
    fun openMessage() = runBlocking {

        print("🔬 Testing Open Message")

        signUserIn()

        val messageId = sendInboxMessage()

        InboxRepository().openMessage(
            clientKey = Env.COURIER_CLIENT_KEY,
            userId = Env.COURIER_USER_ID,
            messageId = messageId
        )

    }

    @Test
    fun clickMessage() = runBlocking {

        print("🔬 Clicking Message")

        signUserIn()

        sendInboxMessage()

        val listener = loadInboxMessages()

        val message = Courier.shared.inboxMessages?.firstOrNull()

        assertNotNull(message)

        Courier.shared.clickMessage(messageId = message!!.messageId)

        listener.remove()

    }

    @Test
    fun readMessage() = runBlocking {

        print("🔬 Read Message")

        signUserIn()

        val messageId = sendInboxMessage()

        Courier.shared.readMessage(messageId = messageId)

    }

    @Test
    fun unreadMessage() = runBlocking {

        print("🔬 Unread Message")

        signUserIn()

        val messageId = sendInboxMessage()

        Courier.shared.unreadMessage(messageId = messageId)

    }

}