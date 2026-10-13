package com.timevault.app

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class AuthRepository {
    private val auth = SupabaseClient.client.auth

    suspend fun signUp(emailInput: String, passwordInput: String): Boolean {
        return try {
            auth.signUpWith(Email) {
                email = emailInput
                password = passwordInput
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun signIn(emailInput: String, passwordInput: String): Boolean {
        return try {
            auth.signInWith(Email) {
                email = emailInput
                password = passwordInput
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}