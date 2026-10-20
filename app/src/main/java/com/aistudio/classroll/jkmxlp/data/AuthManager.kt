package com.aistudio.classroll.jkmxlp.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.aistudio.classroll.jkmxlp.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {
    val auth: FirebaseAuth = Firebase.auth
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    fun attemptAutoSignIn(
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit = {},
        onUnauthenticated: () -> Unit = {}
    ) {
        val user = auth.currentUser
        if (user != null) {
            onSuccess(user)
            return
        }
        try {
            val clientId = context.getString(R.string.default_web_client_id)
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(true)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(true)
                        .build()
                )
                .build()

            scope.launch {
                try {
                    val result = credentialManager.getCredential(context, request)
                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken.idToken, null)
                        val authResult = auth.signInWithCredential(authCredential).await()
                        authResult.user?.let(onSuccess) ?: onUnauthenticated()
                    } else {
                        onUnauthenticated()
                    }
                } catch (e: Exception) {
                    onUnauthenticated()
                }
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Missing default_web_client_id", e)
            onUnauthenticated()
        }
    }

    fun signInWithGoogle(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        try {
            val clientId = context.getString(R.string.default_web_client_id)
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(clientId).build()
                )
                .build()

            scope.launch {
                try {
                    val result = credentialManager.getCredential(activity, request)
                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken.idToken, null)
                        val authResult = auth.signInWithCredential(authCredential).await()
                        authResult.user?.let(onSuccess) ?: onError("Failed to get Firebase user")
                    } else {
                        onError("Unexpected credential type: ${credential.type}")
                    }
                } catch (e: GetCredentialCancellationException) {
                    onCancelled()
                } catch (e: Exception) {
                    onError(e.localizedMessage ?: "Sign-in failed")
                }
            }
        } catch (e: Exception) {
            onError("Google Sign-In configuration missing: default_web_client_id not found")
        }
    }

    fun signOut(scope: CoroutineScope, onComplete: () -> Unit = {}) {
        auth.signOut()
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("AuthManager", "Clear credential failed", e)
            }
            onComplete()
        }
    }
}
