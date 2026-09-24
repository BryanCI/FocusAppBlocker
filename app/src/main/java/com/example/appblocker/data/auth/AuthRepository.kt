package com.example.appblocker.data.auth

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<AuthUser?>
    
    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signUpWithEmail(email: String, password: String, fullName: String? = null): Result<AuthUser>
    suspend fun signInAnonymously(): Result<AuthUser>
    suspend fun signOut()
    suspend fun linkWithGoogle(idToken: String, fullName: String? = null): Result<AuthUser>
    suspend fun linkWithEmail(email: String, password: String, fullName: String? = null): Result<AuthUser>
    
    suspend fun isPremium(): Result<Boolean>
    suspend fun setPremium(isPremium: Boolean): Result<Unit>
    suspend fun migrateUserData(fromUid: String, toUid: String): Result<Unit>
}
