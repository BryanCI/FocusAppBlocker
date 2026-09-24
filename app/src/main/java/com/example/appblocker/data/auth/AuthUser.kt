package com.example.appblocker.data.auth

data class AuthUser(
    val id: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isGuest: Boolean = false
)
