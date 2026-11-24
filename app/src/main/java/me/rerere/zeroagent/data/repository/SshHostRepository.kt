package me.rerere.zeroagent.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.data.ssh.SshHostCredentials
import me.rerere.zeroagent.data.db.dao.SshHostDao
import me.rerere.zeroagent.data.db.entity.SshHostEntity

class SshHostRepository internal constructor(
    private val dao: SshHostDao,
    private val credentials: SshHostCredentials = SshHostCredentials(),
) {
    suspend fun getAll(): List<SshHostEntity> = withContext(Dispatchers.IO) {
        dao.getAll().map(credentials::decrypt)
    }

    suspend fun getByName(name: String): SshHostEntity? = withContext(Dispatchers.IO) {
        dao.getByName(name)?.let(credentials::decrypt)
    }

    suspend fun upsert(host: SshHostEntity) = withContext(Dispatchers.IO) {
        dao.upsert(credentials.encrypt(host))
    }

    suspend fun rename(originalName: String, host: SshHostEntity) = withContext(Dispatchers.IO) {
        dao.rename(originalName, credentials.encrypt(host))
    }

    suspend fun deleteByName(name: String) = dao.deleteByName(name)
}
