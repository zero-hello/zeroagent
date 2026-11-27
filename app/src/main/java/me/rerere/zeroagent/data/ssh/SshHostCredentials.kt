package me.rerere.zeroagent.data.ssh

import me.rerere.zeroagent.data.db.entity.SshHostEntity
import me.rerere.zeroagent.data.security.CredentialCipher

internal class SshHostCredentials(private val cipher: CredentialCipher = CredentialCipher.device) {
    fun encrypt(host: SshHostEntity): SshHostEntity = host.copy(
        password = encrypt(host.name, "password", host.password),
        privateKey = encrypt(host.name, "privateKey", host.privateKey),
        passphrase = encrypt(host.name, "passphrase", host.passphrase),
    )

    fun decrypt(host: SshHostEntity): SshHostEntity = host.copy(
        password = decrypt(host.name, "password", host.password),
        privateKey = decrypt(host.name, "privateKey", host.privateKey),
        passphrase = decrypt(host.name, "passphrase", host.passphrase),
    )

    fun encrypt(name: String, field: String, value: String?): String? =
        value?.let { cipher.encrypt(it, scope(name, field)) }

    private fun decrypt(name: String, field: String, value: String?): String? =
        value?.let { cipher.decrypt(it, scope(name, field)) }

    private fun scope(name: String, field: String) = "ssh_hosts/$field/$name"
}
