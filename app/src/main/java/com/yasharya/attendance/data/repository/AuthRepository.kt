package com.yasharya.attendance.data.repository

import com.yasharya.attendance.data.Session
import com.yasharya.attendance.data.SessionStore
import com.yasharya.attendance.data.local.PasswordHasher
import com.yasharya.attendance.data.local.dao.UserDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed interface SignInResult {
    data class Success(val session: Session) : SignInResult

    /**
     * One failure for both "no such user" and "wrong password", on purpose.
     * Distinguishing them turns the sign-in screen into a tool for discovering
     * which employee IDs exist.
     */
    data object InvalidCredentials : SignInResult
}

class AuthRepository(
    private val userDao: UserDao,
    private val sessionStore: SessionStore,
) {
    val session: Flow<Session?> = sessionStore.session

    suspend fun signIn(username: String, password: String): SignInResult = withContext(Dispatchers.Default) {
        val user = userDao.findByUsername(username.trim().uppercase())
            ?: userDao.findByUsername(username.trim())
            ?: return@withContext SignInResult.InvalidCredentials

        // PBKDF2 at 120k iterations is deliberately slow, so it runs off the
        // main thread even though this is a local lookup.
        if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
            return@withContext SignInResult.InvalidCredentials
        }

        val session = Session(username = user.username, role = user.role, staffId = user.staffId)
        sessionStore.signIn(session)
        SignInResult.Success(session)
    }

    suspend fun signOut() = sessionStore.signOut()
}
