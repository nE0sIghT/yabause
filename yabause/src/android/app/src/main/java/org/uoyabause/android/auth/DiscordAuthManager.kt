package org.uoyabause.android.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.devmiyax.yabasanshiro.R

/**
 * Discord OAuth2 authentication provider with PKCE support for Firebase Auth integration
 */
class DiscordAuthManager(private val context: Context) {
    private val TAG = "DiscordAuthManager"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val client = OkHttpClient()

    companion object {
        // PKCE constants
        private const val CODE_VERIFIER_LENGTH = 64 // Recommended length (43-128 chars)
        const val EXTRA_CANCELLED = "discord_auth_cancelled"

        // Firestore collection for storing Discord account links
        private const val COLLECTION_DISCORD_LINKS = "discord_links"
    }

    // Configuration from local_security.xml
    private val CLIENT_ID: String = context.getString(R.string.discord_client_id)
    private val CLIENT_SECRET: String = context.getString(R.string.discord_client_secret)
    private val REDIRECT_URI: String = context.getString(R.string.discord_redirect_uri)
    private val DISCORD_AUTH_URL: String = context.getString(R.string.discord_auth_url)
    private val DISCORD_TOKEN_URL: String = context.getString(R.string.discord_token_url)
    private val DISCORD_USER_URL: String = context.getString(R.string.discord_user_url)

    // PKCE state is now handled by TokenStorage

    /**
     * Generate a code verifier for PKCE (RFC 7636)
     * @return The generated code verifier
     */
    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val codeVerifierBytes = ByteArray(CODE_VERIFIER_LENGTH)
        secureRandom.nextBytes(codeVerifierBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes)
    }

    /**
     * Generate a code challenge using SHA-256 (S256 method in RFC 7636)
     * @param codeVerifier The code verifier to hash
     * @return The generated code challenge
     */
    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Start the Discord OAuth2 authorization flow with PKCE
     */
    fun startDiscordLogin() {
        // Generate code verifier and challenge
        val tokenStorage = TokenStorage(context)
        val localCodeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(localCodeVerifier)

        // Store the code verifier securely with a 10-minute timeout
        tokenStorage.saveCodeVerifier(localCodeVerifier)

        Log.d(TAG, "Generated PKCE Code Verifier: $localCodeVerifier")
        Log.d(TAG, "Generated PKCE Code Challenge: $codeChallenge")

        // Build the authorization URL with PKCE parameters
        val authUrl = Uri.parse(DISCORD_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "identify email")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        try {
            // Launch the authorization URL in a CustomTabsIntent
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, authUrl)
            Log.d(TAG, "Opening Discord Auth URL: $authUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Discord authentication", e)
        }
    }

    /**
     * Alias for startDiscordLogin for compatibility with existing code
     */
    fun startAuthorization() {
        startDiscordLogin()
    }

    /**
     * Handle the redirect URI from Discord OAuth2 authorization
     * @param uri The redirect URI with authorization code
     * @return Boolean true if the process succeeded, false otherwise
     */
    suspend fun handleRedirectAndSignIn(uri: Uri): Boolean {
        // Check for errors in the redirect
        val error = uri.getQueryParameter("error")
        if (error != null) {
            val errorDescription = uri.getQueryParameter("error_description") ?: error
            Log.e(TAG, "OAuth Error: $error - $errorDescription")
            TokenStorage(context).clearCodeVerifier()
            return false
        }

        // Extract the authorization code
        val code = uri.getQueryParameter("code")
        if (code == null) {
            Log.e(TAG, "Authorization code not found in redirect URI")
            TokenStorage(context).clearCodeVerifier()
            return false
        }

        Log.d(TAG, "Received authorization code: $code")

        // Retrieve the stored code verifier from secure storage
        val tokenStorage = TokenStorage(context)
        val storedCodeVerifier = tokenStorage.getCodeVerifier()
        if (storedCodeVerifier == null) {
            Log.e(TAG, "Code verifier not found - authentication session may have timed out")
            return false
        }

        // Clear the code verifier immediately to prevent reuse
        tokenStorage.clearCodeVerifier()

        try {
            // 1. Exchange the code for access token
            val tokenResponseJson = exchangeCodeForTokenResponse(code, storedCodeVerifier)
            val accessToken = tokenResponseJson.optString("access_token")
            val refreshToken = tokenResponseJson.optString("refresh_token", null)
            val expiresIn = tokenResponseJson.getLong("expires_in")

            Log.d(TAG, "Access token obtained successfully")

            // Store tokens securely for future use
            val tokenStorage = TokenStorage(context)
            tokenStorage.saveDiscordTokens(accessToken, refreshToken ?: "", expiresIn)

            // 2. Fetch user info from Discord
            val userInfo = getUserInfo(accessToken)
            val discordId = userInfo.getString("id")
            Log.d(TAG, "Discord user info obtained for ID: $discordId")

            // 3. Update Firebase user profile with Discord info
            val firebaseSuccess = updateFirebaseWithDiscordInfo(discordId, accessToken, userInfo)
            if (!firebaseSuccess) {
                Log.e(TAG, "Failed to update Firebase with Discord info")
                return false
            }

            Log.i(TAG, "Discord OAuth flow completed successfully for user: ${userInfo.getString("username")}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error during Discord authentication process", e)
            return false
        }
    }

    /**
     * Handle authorization response from AppAuth (for compatibility with existing code)
     * @param response The authorization response
     * @param exception Any authorization exception
     * @return JSONObject? The Discord user info if successful, null otherwise
     */
/*
    suspend fun handleAuthorizationResponse(
        response: net.openid.appauth.AuthorizationResponse?,
        exception: net.openid.appauth.AuthorizationException?
    ): JSONObject? {
        if (exception != null) {
            Log.e(TAG, "Authorization error: ${exception.error} - ${exception.errorDescription}")
            return null
        }

        if (response == null) {
            Log.e(TAG, "Authorization response is null")
            return null
        }

        val code = response.authorizationCode
        if (code == null) {
            Log.e(TAG, "Authorization code is null")
            return null
        }

        // Use the stored code verifier from secure storage
        val tokenStorage = TokenStorage(context)
        val storedCodeVerifier = tokenStorage.getCodeVerifier() ?: run {
            Log.e(TAG, "Code verifier not found")
            return null
        }

        tokenStorage.clearCodeVerifier()

        try {
            val tokenResponse = exchangeCodeForTokenResponse(code, storedCodeVerifier)
            val accessToken = tokenResponse.getString("access_token")
            return getUserInfo(accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling authorization response", e)
            return null
        }
    }
*/
    /**
     * Exchange authorization code for access token using PKCE
     * @param code The authorization code from Discord
     * @param codeVerifier The original code verifier used in the authorization request
     * @return JSONObject The token response
     * @throws Exception on network error or failed response
     */
    private suspend fun exchangeCodeForTokenResponse(code: String, codeVerifier: String): JSONObject = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url(DISCORD_TOKEN_URL)
            .post(formBody)
            .header("Accept", "application/json")
            .build()

        Log.d(TAG, "Requesting token from Discord")
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful || responseBody == null) {
             val errorBody = responseBody ?: "Empty body"
             val errorMessage = "Token exchange failed: ${response.code} - $errorBody"
             Log.e(TAG, errorMessage)
             response.close()
             throw Exception(errorMessage)
        }

        response.close()
        JSONObject(responseBody)
    }

    /**
     * Fetch user information from Discord API
     * @param accessToken The Discord access token
     * @return JSONObject The user information
     * @throws Exception on network error or failed response
     */
    private suspend fun getUserInfo(accessToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(DISCORD_USER_URL)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()

        Log.d(TAG, "Fetching user info from Discord")
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful || responseBody == null) {
            val errorBody = responseBody ?: "Empty body"
            val errorMessage = "Failed to fetch user info: ${response.code} - $errorBody"
            Log.e(TAG, errorMessage)
            response.close()
            throw Exception(errorMessage)
        }

        response.close()
        val jsonObject = JSONObject(responseBody)

        // Extract and format user data
        val username = jsonObject.optString("username", "Unknown")
        val discriminator = jsonObject.optString("discriminator", "0000")
        val avatarId = jsonObject.optString("avatar", null)
        val userId = jsonObject.getString("id")

        // Generate avatar URL
        val avatarUrl = if (avatarId != null && avatarId != "null") {
            "https://cdn.discordapp.com/avatars/$userId/$avatarId.png"
        } else {
            try {
                val defaultAvatarIndex = (discriminator.toInt()) % 5
                "https://cdn.discordapp.com/embed/avatars/$defaultAvatarIndex.png"
            } catch (e: NumberFormatException) {
                "https://cdn.discordapp.com/embed/avatars/0.png" // Fallback default
            }
        }

        Log.d(TAG, "Discord User: $username#$discriminator (ID: $userId)")
        Log.d(TAG, "Discord Avatar URL: $avatarUrl")

        // Add derived fields to the JSON object
        jsonObject.put("_avatarUrl", avatarUrl)
        jsonObject.put("_displayName", username)

        jsonObject
    }

    /**
     * Update Firebase with Discord user information
     * @param discordId The Discord user ID
     * @param accessToken The Discord access token
     * @param discordUserInfo The Discord user information
     * @return Boolean true if the operation was successful, false otherwise
     */
    private suspend fun updateFirebaseWithDiscordInfo(
        discordId: String,
        accessToken: String,
        discordUserInfo: JSONObject
    ): Boolean = withContext(Dispatchers.IO) {
        // Get Discord profile information
        val discordDisplayName = discordUserInfo.getString("global_name")
        val discordAvatarUrl = discordUserInfo.getString("_avatarUrl")

        // Get current Firebase user
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "No Firebase user is currently signed in")
            return@withContext false
        }

        try {
            // 1. Store Discord link in Firestore
            storeDiscordLink(currentUser.uid, discordId)

            // 2. Update Firebase user profile with Discord info
            // We'll store the Discord display name and avatar, but preserve the Firebase UID
            updateUserProfile(currentUser, discordDisplayName, discordAvatarUrl)

            // 3. Store additional Discord info in Firestore to ensure we have both IDs
            storeDiscordUserInfo(currentUser.uid, discordId, discordDisplayName, discordAvatarUrl)

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Firebase with Discord info", e)
            return@withContext false
        }
    }

    /**
     * Store Discord account link in Firestore
     * @param firebaseUid The Firebase user ID
     * @param discordId The Discord user ID
     */
    private suspend fun storeDiscordLink(firebaseUid: String, discordId: String) = withContext(Dispatchers.IO) {
        try {
            val linkData = hashMapOf(
                "discordId" to discordId,
                "linkedAt" to System.currentTimeMillis()
            )

            firestore.collection(COLLECTION_DISCORD_LINKS)
                .document(firebaseUid)
                .set(linkData)
                .await()

            Log.d(TAG, "Discord link stored in Firestore for user $firebaseUid")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing Discord link in Firestore", e)
            throw e
        }
    }

    /**
     * Store detailed Discord user information in Firestore
     * @param firebaseUid The Firebase user ID
     * @param discordId The Discord user ID
     * @param discordDisplayName The Discord display name
     * @param discordAvatarUrl The Discord avatar URL
     */
    private suspend fun storeDiscordUserInfo(
        firebaseUid: String,
        discordId: String,
        discordDisplayName: String,
        discordAvatarUrl: String
    ) = withContext(Dispatchers.IO) {
        try {
            val userInfoData = hashMapOf(
                "firebaseUid" to firebaseUid,
                "discordId" to discordId,
                "discordDisplayName" to discordDisplayName,
                "discordAvatarUrl" to discordAvatarUrl,
                "updatedAt" to System.currentTimeMillis()
            )

            // Store in a separate collection to keep track of both IDs
            firestore.collection("user_profiles")
                .document(firebaseUid)
                .set(userInfoData)
                .await()

            Log.d(TAG, "Discord user info stored in Firestore for user $firebaseUid")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing Discord user info in Firestore", e)
            // Don't throw here, as this is an additional step that shouldn't break the flow
            // if it fails
        }
    }

    /**
     * Check if a Firebase user has linked their Discord account
     * @param firebaseUid The Firebase user ID
     * @return Boolean true if Discord is linked, false otherwise
     */
    suspend fun isDiscordLinked(firebaseUid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val document = firestore.collection(COLLECTION_DISCORD_LINKS)
                .document(firebaseUid)
                .get()
                .await()

            return@withContext document.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Discord link status", e)
            return@withContext false
        }
    }

    /**
     * Unlink Discord account from Firebase user
     * @param firebaseUid The Firebase user ID
     * @return Boolean true if unlink was successful, false otherwise
     */
    suspend fun unlinkDiscord(firebaseUid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection(COLLECTION_DISCORD_LINKS)
                .document(firebaseUid)
                .delete()
                .await()

            Log.d(TAG, "Discord account unlinked for user $firebaseUid")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error unlinking Discord account", e)
            return@withContext false
        }
    }

    /**
     * Update Firebase user profile with Discord information
     * @param user The Firebase user to update
     * @param displayName The display name from Discord
     * @param photoUrl The avatar URL from Discord
     * @return Boolean true if update was successful, false otherwise
     */
    suspend fun updateFirebaseUserProfile(discordUserInfo: JSONObject): Boolean {
        val currentUser = auth.currentUser ?: return false
        val displayName = discordUserInfo.getString("global_name")
        val photoUrl = discordUserInfo.getString("_avatarUrl")
        return updateUserProfile(currentUser, displayName, photoUrl)
    }

    /**
     * Update Firebase user profile
     * @param user The Firebase user to update
     * @param displayName The display name to set
     * @param photoUrl The photo URL to set
     * @return Boolean true if update was successful, false otherwise
     */
    private suspend fun updateUserProfile(user: FirebaseUser, displayName: String, photoUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Store the original Firebase user ID and email to ensure we don't lose them
            val originalUid = user.uid
            val originalEmail = user.email

            // Create a profile update that preserves the Firebase user ID
            val profileUpdatesBuilder = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)

            try {
                profileUpdatesBuilder.setPhotoUri(Uri.parse(photoUrl))
            } catch (e: Exception) {
                Log.w(TAG, "Invalid photo URL format: $photoUrl", e)
            }

            // Update the profile
            user.updateProfile(profileUpdatesBuilder.build()).await()

            // Log the update with both IDs to help with debugging
            Log.d(TAG, "Updated Firebase profile for user ${user.uid} with Discord display name: $displayName")
            Log.d(TAG, "Original Firebase UID: $originalUid, Email: $originalEmail")

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Firebase profile", e)
            return@withContext false
        }
    }

    // clearPkceState method removed as we now use TokenStorage

    /**
     * TokenStorage for securely storing Discord tokens and PKCE state
     */
    private class TokenStorage(context: Context) {
        private val TAG = "DiscordTokenStorage"
        private val prefsName = "discord_auth_prefs"
        private val keyAccessToken = "discord_access_token"
        private val keyRefreshToken = "discord_refresh_token"
        private val keyExpiresAt = "discord_expires_at"
        private val keyCodeVerifier = "discord_code_verifier"
        private val keyCodeVerifierTimestamp = "discord_code_verifier_timestamp"
        private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        /**
         * Save Discord tokens to SharedPreferences
         * @param accessToken The Discord access token
         * @param refreshToken The Discord refresh token
         * @param expiresIn The token expiration time in seconds
         */
        fun saveDiscordTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
            val expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000
            Log.d(TAG, "Saving Discord tokens, expires in $expiresIn seconds")
            prefs.edit()
                .putString(keyAccessToken, accessToken)
                .putString(keyRefreshToken, refreshToken)
                .putLong(keyExpiresAt, expiresAtMillis)
                .apply()
        }

        /**
         * Get the stored Discord access token
         * @return The access token or null if not found or expired
         */
        fun getAccessToken(): String? {
            val expiresAt = prefs.getLong(keyExpiresAt, 0)
            if (System.currentTimeMillis() > expiresAt) {
                Log.d(TAG, "Access token has expired")
                return null
            }
            return prefs.getString(keyAccessToken, null)
        }

        /**
         * Save the PKCE code verifier
         * @param codeVerifier The code verifier to save
         * @param timeoutMinutes How long the code verifier should be valid (in minutes)
         */
        fun saveCodeVerifier(codeVerifier: String, timeoutMinutes: Int = 10) {
            val expiresAtMillis = System.currentTimeMillis() + (timeoutMinutes * 60 * 1000)
            Log.d(TAG, "Saving PKCE code verifier, expires in $timeoutMinutes minutes")
            prefs.edit()
                .putString(keyCodeVerifier, codeVerifier)
                .putLong(keyCodeVerifierTimestamp, expiresAtMillis)
                .apply()
        }

        /**
         * Get the stored PKCE code verifier if it's still valid
         * @return The code verifier or null if not found or expired
         */
        fun getCodeVerifier(): String? {
            val expiresAt = prefs.getLong(keyCodeVerifierTimestamp, 0)
            if (System.currentTimeMillis() > expiresAt) {
                Log.d(TAG, "Code verifier has expired")
                clearCodeVerifier()
                return null
            }
            return prefs.getString(keyCodeVerifier, null)
        }

        /**
         * Clear the stored PKCE code verifier
         */
        fun clearCodeVerifier() {
            prefs.edit()
                .remove(keyCodeVerifier)
                .remove(keyCodeVerifierTimestamp)
                .apply()
            Log.d(TAG, "PKCE code verifier cleared")
        }

        /**
         * Clear all stored Discord tokens and PKCE state
         */
        fun clearTokens() {
            prefs.edit()
                .remove(keyAccessToken)
                .remove(keyRefreshToken)
                .remove(keyExpiresAt)
                .remove(keyCodeVerifier)
                .remove(keyCodeVerifierTimestamp)
                .apply()
            Log.d(TAG, "Discord tokens and PKCE state cleared")
        }
    }
}
