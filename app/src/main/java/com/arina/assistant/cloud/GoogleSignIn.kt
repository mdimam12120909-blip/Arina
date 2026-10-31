package com.arina.assistant.cloud

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.arina.assistant.Defaults
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.io.IOException

/** Shows Google's account chooser and returns the ID token that proves who signed in. */
object GoogleSignIn {
    /** Null when the person closed the chooser. */
    suspend fun idToken(activity: Activity): String? {
        val option = GetSignInWithGoogleOption.Builder(Defaults.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                throw IOException("Google returned an unexpected sign-in type.")
            }
        } catch (e: GetCredentialCancellationException) {
            null
        } catch (e: GetCredentialException) {
            throw IOException("Google sign-in failed: ${e.message ?: e.type}")
        }
    }
}
