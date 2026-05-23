package com.example.data.model

data class Peer(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis()
)
