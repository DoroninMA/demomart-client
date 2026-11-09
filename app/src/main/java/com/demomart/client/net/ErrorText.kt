package com.demomart.client.net

fun errorText(code: String): String = when (code) {
    "email_taken" -> "This email is already used by another account"
    "cashier_account" -> "Cashier accounts can't use the customer app"
    "unauthenticated", "session_required" -> "Signed out, please log in again"
    "invalid_login" -> "Check your email and password"
    "weak_password" -> "Use a password with at least six characters"
    "storage" -> "Couldn't save your session, please try again"
    "network" -> "No connection to server"
    "unsupported" -> "Google sign-in is unavailable"
    "google_cancelled" -> "Google sign-in was cancelled"
    else -> "Something went wrong"
}
