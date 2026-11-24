package me.rerere.zeroagent.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved SSH host the LLM (or user) can reference by name.
 *
 * Room stores password, private key and passphrase as device-bound ciphertext.
 * SshHostRepository encrypts writes and decrypts values for authorized callers.
 */
@Entity(tableName = "ssh_hosts")
data class SshHostEntity(
    /** Display name; also the lookup key from the LLM. */
    @PrimaryKey val name: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null,
    val createdAtMs: Long,
)
