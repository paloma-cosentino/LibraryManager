package org.example.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import java.util.*

object JwtService {
    private val secret = System.getenv("JWT_SECRET")
        ?: error("Variável de ambiente JWT_SECRET não definida")
    val algorithm: Algorithm = Algorithm.HMAC256(secret)
    val verifier: JWTVerifier = JWT.require(algorithm).build()

    fun generateToken(userId: Int, email: String): String {
        return JWT.create().withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 86400000))
            .sign(algorithm)
    }

    fun verifyToken(token: String): DecodedJWT {
        return verifier.verify(token)
    }

    fun validateToken(token: String): Boolean {
        try {
            verifyToken(token)
            return true
        } catch (_: JWTVerificationException) {
            return false
        }
    }
}