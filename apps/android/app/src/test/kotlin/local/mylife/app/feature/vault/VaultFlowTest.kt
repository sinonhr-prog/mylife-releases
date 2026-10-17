package local.mylife.app.feature.vault

import kotlinx.coroutines.runBlocking
import local.mylife.core.model.VaultEntry
import local.mylife.core.model.VaultRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Тесты сценариев использования Сейфа (H09: vault-flow & vault-exposure)
 */
class VaultFlowTest {

    private lateinit var fakeVaultRepository: FakeVaultRepository

    @Before
    fun setUp() {
        fakeVaultRepository = FakeVaultRepository()
    }

    @Test
    fun testPasswordGenerator_LengthAndEntropy() {
        // Проверка генерации паролей разной длины и наборов символов
        val p1 = PasswordGenerator.generatePassword(length = 16, includeSymbols = true)
        assertEquals(16, p1.length)
        assertTrue("Пароль должен содержать спецсимвол", p1.any { "!@#$%^&*()-_=+[]{}<>".contains(it) })
        assertTrue("Пароль должен содержать цифры", p1.any { it.isDigit() })
        assertTrue("Пароль должен содержать заглавные буквы", p1.any { it.isUpperCase() })
        assertTrue("Пароль должен содержать строчные буквы", p1.any { it.isLowerCase() })

        val p2 = PasswordGenerator.generatePassword(length = 8, includeSymbols = false)
        assertEquals(8, p2.length)
        assertFalse("Пароль без спецсимволов не должен содержать знаки пунктуации", p2.any { "!@#$%^&*()-_=+[]{}<>".contains(it) })

        // Проверка уникальности последовательных генераций (CSPRNG)
        val p3 = PasswordGenerator.generatePassword(length = 20)
        val p4 = PasswordGenerator.generatePassword(length = 20)
        assertFalse("Два сгенерированных пароля не должны совпадать", p3 == p4)
    }

    @Test
    fun testVaultEntryCrud_IsolatedStorage() = runBlocking {
        // Создание записи в Сейфе
        val entry = VaultEntry(
            id = UUID.randomUUID().toString(),
            title = "Банк",
            website = "https://bank.ru",
            username = "user@test.ru",
            password = "VerySecurePassword123!",
            secretNote = "Секретное кодовое слово",
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis(),
        )

        fakeVaultRepository.saveVaultEntry(entry)

        // Чтение и проверка
        val retrieved = fakeVaultRepository.getVaultEntryById(entry.id)
        assertNotNull(retrieved)
        assertEquals("Банк", retrieved?.title)
        assertEquals("user@test.ru", retrieved?.username)
        assertEquals("VerySecurePassword123!", retrieved?.password)

        // Получение всех записей
        val list = fakeVaultRepository.getVaultEntries()
        assertEquals(1, list.size)
        assertEquals(entry.id, list.first().id)

        // Обновление записи
        val updated = entry.copy(title = "Банк Новый", password = "EvenBetterPass#2026")
        fakeVaultRepository.saveVaultEntry(updated)

        val retrievedUpdated = fakeVaultRepository.getVaultEntryById(entry.id)
        assertEquals("Банк Новый", retrievedUpdated?.title)
        assertEquals("EvenBetterPass#2026", retrievedUpdated?.password)

        // Удаление записи
        fakeVaultRepository.deleteVaultEntry(entry.id)
        assertNull(fakeVaultRepository.getVaultEntryById(entry.id))
        assertEquals(0, fakeVaultRepository.getVaultEntries().size)
    }

    @Test
    fun testVaultViewModel_Integration() = runBlocking {
        val viewModel = VaultViewModel(fakeVaultRepository, externalScope = this)
        viewModel.openAddEntry()
        assertTrue(viewModel.uiState.value.showAddEditDialog)

        viewModel.saveEntry(
            title = "Почта",
            website = "mail.ru",
            username = "test@mail.ru",
            password = "SecretPassword456",
            secretNote = "Заметка",
        )

        val entries = fakeVaultRepository.getVaultEntries()
        assertEquals(1, entries.size)
        assertEquals("Почта", entries.first().title)

        // Скрытие / показ пароля
        val id = entries.first().id
        viewModel.togglePasswordVisibility(id)
        assertTrue(viewModel.uiState.value.revealedPasswordIds.contains(id))

        viewModel.togglePasswordVisibility(id)
        assertFalse(viewModel.uiState.value.revealedPasswordIds.contains(id))
    }

    private class FakeVaultRepository : VaultRepository {
        private val entries = mutableMapOf<String, VaultEntry>()

        override suspend fun getVaultEntries(): List<VaultEntry> = entries.values.toList()

        override suspend fun getVaultEntryById(id: String): VaultEntry? = entries[id]

        override suspend fun saveVaultEntry(entry: VaultEntry) {
            entries[entry.id] = entry
        }

        override suspend fun deleteVaultEntry(id: String) {
            entries.remove(id)
        }
    }
}
