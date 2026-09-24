package com.example.appblocker.navigation

import com.example.appblocker.data.auth.AuthUser

enum class Screen {
    AppList,
    Insights,
    Scheduler,
    Profile,
    Statistics
}

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val user: AuthUser) : AuthState()
    object Unauthenticated : AuthState()
}
