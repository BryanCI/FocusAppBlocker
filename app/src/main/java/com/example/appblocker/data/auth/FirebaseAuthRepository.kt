package com.example.appblocker.data.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser?.let {
                AuthUser(
                    id = it.uid,
                    email = it.email,
                    displayName = it.displayName,
                    photoUrl = it.photoUrl?.toString(),
                    isGuest = it.isAnonymous
                )
            }
            trySend(user)
        }
        firebaseAuth.addAuthStateListener(authStateListener)
        awaitClose { firebaseAuth.removeAuthStateListener(authStateListener) }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        val user = result.user!!
        Result.success(
            AuthUser(
                id = user.uid,
                email = user.email,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
                isGuest = user.isAnonymous
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> = try {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val user = result.user!!
        Result.success(
            AuthUser(
                id = user.uid,
                email = user.email,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
                isGuest = user.isAnonymous
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun signUpWithEmail(email: String, password: String, fullName: String?): Result<AuthUser> = try {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user!!

        // Update profile if fullName is provided
        if (fullName != null) {
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
                .build()
            user.updateProfile(profileUpdates).await()

            // Sync with Firestore
            firestore.collection("users").document(user.uid)
                .set(mapOf("displayName" to fullName, "email" to user.email), com.google.firebase.firestore.SetOptions.merge())
                .await()
        }

        Result.success(
            AuthUser(
                id = user.uid,
                email = user.email,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
                isGuest = user.isAnonymous
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun signInAnonymously(): Result<AuthUser> = try {
        val result = firebaseAuth.signInAnonymously().await()
        val user = result.user!!
        Result.success(
            AuthUser(
                id = user.uid,
                email = user.email,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
                isGuest = user.isAnonymous
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    override suspend fun linkWithGoogle(idToken: String, fullName: String?): Result<AuthUser> = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val guestUid = if (firebaseAuth.currentUser?.isAnonymous == true) firebaseAuth.currentUser?.uid else null
        
        // If we are a guest, fetch data BEFORE signing in as a permanent user
        val guestData = guestUid?.let { uid ->
            firestore.collection("users").document(uid).get().await().data
        }

        val userResult = try {
            firebaseAuth.currentUser?.linkWithCredential(credential)?.await()?.user
        } catch (e: FirebaseAuthUserCollisionException) {
            // Email already exists, sign in instead of linking
            val signInResult = firebaseAuth.signInWithCredential(credential).await()
            val permanentUser = signInResult.user!!
            
            // Migrate data if we were a guest
            if (guestData != null && guestUid != permanentUser.uid) {
                firestore.collection("users").document(permanentUser.uid)
                    .set(guestData, com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }
            permanentUser
        }
        
        val user = userResult ?: throw Exception("Linking failed")

        // Update profile if fullName is provided
        if (fullName != null) {
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
                .build()
            user.updateProfile(profileUpdates).await()
            
            // Sync with Firestore
            firestore.collection("users").document(user.uid)
                .set(mapOf("displayName" to fullName, "email" to user.email), com.google.firebase.firestore.SetOptions.merge())
                .await()
        }

        Result.success(
            AuthUser(
                id = user.uid,
                email = user.email,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
                isGuest = user.isAnonymous
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun linkWithEmail(email: String, password: String, fullName: String?): Result<AuthUser> = try {
        val credential = EmailAuthProvider.getCredential(email, password)
        val guestUid = if (firebaseAuth.currentUser?.isAnonymous == true) firebaseAuth.currentUser?.uid else null

        // If we are a guest, fetch data BEFORE signing in as a permanent user
        val guestData = guestUid?.let { uid ->
            firestore.collection("users").document(uid).get().await().data
        }

        val userResult = try {
            firebaseAuth.currentUser?.linkWithCredential(credential)?.await()?.user
        } catch (e: FirebaseAuthUserCollisionException) {
            // Email already exists, sign in instead of linking
            val signInResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val permanentUser = signInResult.user!!

            // Migrate data if we were a guest
            if (guestData != null && guestUid != permanentUser.uid) {
                firestore.collection("users").document(permanentUser.uid)
                    .set(guestData, com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }
            permanentUser
        }

        val user = userResult ?: throw Exception("Linking failed")

        // Update profile if fullName is provided
        if (fullName != null) {
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
                .build()
            user.updateProfile(profileUpdates).await()

            // Sync with Firestore
            firestore.collection("users").document(user.uid)
                .set(mapOf("displayName" to fullName, "email" to user.email), com.google.firebase.firestore.SetOptions.merge())
                .await()
        }

        Result.success(
            AuthUser(
                id = user.uid,
                email = user.email,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
                isGuest = user.isAnonymous
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun isPremium(): Result<Boolean> = try {
        val uid = firebaseAuth.currentUser?.uid ?: throw Exception("Not signed in")
        val document = firestore.collection("users").document(uid).get().await()
        val isPremium = document.getBoolean("isPremium") ?: false
        Result.success(isPremium)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun setPremium(isPremium: Boolean): Result<Unit> = try {
        val uid = firebaseAuth.currentUser?.uid ?: throw Exception("Not signed in")
        firestore.collection("users").document(uid)
            .update("isPremium", isPremium)
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        // If update fails, try set
        try {
            val uid = firebaseAuth.currentUser?.uid ?: throw Exception("Not signed in")
            firestore.collection("users").document(uid)
                .set(mapOf("isPremium" to isPremium), com.google.firebase.firestore.SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e2: Exception) {
            Result.failure(e2)
        }
    }

    override suspend fun migrateUserData(fromUid: String, toUid: String): Result<Unit> = try {
        val guestDoc = firestore.collection("users").document(fromUid).get().await()
        if (guestDoc.exists()) {
            val data = guestDoc.data ?: emptyMap()
            firestore.collection("users").document(toUid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .await()
            
            // Optionally delete the old guest document
            // firestore.collection("users").document(fromUid).delete().await()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
