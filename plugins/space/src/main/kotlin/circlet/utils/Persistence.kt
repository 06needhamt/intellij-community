package circlet.utils

import com.intellij.credentialStore.*
import com.intellij.ide.passwordSafe.*
import runtime.*
import runtime.persistence.*
import runtime.reactive.*

object IdeaPasswordSafePersistence : Persistence {

    override suspend fun put(key: String, value: String) {
        PasswordSafe.getInstance().setPassword(createCredentialAttributes(key), value)
    }

    override suspend fun get(key: String): String? =
        PasswordSafe.getInstance().getPassword(createCredentialAttributes(key))

    override suspend fun delete(key: String) {
        PasswordSafe.getInstance().setPassword(createCredentialAttributes(key), null)
    }

    override suspend fun batchPut(keyValuePairs: List<Pair<String, String>>) {
        TODO("not implemented")
    }

    override suspend fun batchGet(keys: List<String>): List<Pair<String, String?>> {
        TODO("not implemented")
    }

    override suspend fun batchDelete(keys: List<String>) {
        TODO("not implemented")
    }

    override fun forEach(lifetime: Lifetime, key: String, callback: (String?) -> Unit) {
        TODO("not implemented")
    }

    override suspend fun getAllKeys(): List<String> {
        TODO("not implemented")
    }

    override suspend fun clear() {
    }

    private fun createCredentialAttributes(key: String) =
        CredentialAttributes("${javaClass.name}-$key")
}
