package org.cursiveerror.securenote

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform