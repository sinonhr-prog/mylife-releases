package local.mylife.core.security.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class RecoverableFileBatchTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun failureAfterReplacementRestoresAllOriginals() {
        val root = folder.newFolder()
        val first = File(root, "main.db").apply { writeText("old-main") }
        val second = File(root, "vault.db").apply { writeText("old-vault") }
        val source = File(root, "new").apply { writeText("new-data") }
        val journal = File(root, "journal")
        try {
            RecoverableFileBatch(journal, root).apply(linkedMapOf(first to source, second to source)) {
                if (it == 0) throw IOException("synthetic disk failure")
            }
            fail("Failure must propagate")
        } catch (_: IOException) {
        }
        assertEquals("old-main", first.readText())
        assertEquals("old-vault", second.readText())
        assertFalse(journal.exists())
    }

    @Test fun interruptedProcessRecoversBeforeOpeningStorage() {
        val root = folder.newFolder()
        val first = File(root, "main.db").apply { writeText("old-main") }
        val newFile = File(root, "new-attachment")
        val source = File(root, "new").apply { writeText("new-data") }
        val journal = File(root, "journal")
        try {
            RecoverableFileBatch(journal, root).apply(linkedMapOf(first to source, newFile to source)) {
                if (it == 1) throw AssertionError("synthetic process death")
            }
        } catch (_: AssertionError) {
        }
        assertTrue(journal.exists())
        RecoverableFileBatch(journal, root).recover()
        assertEquals("old-main", first.readText())
        assertFalse(newFile.exists())
    }

    @Test fun committedBatchKeepsNewFilesAndDeletion() {
        val root = folder.newFolder()
        val first = File(root, "main.db").apply { writeText("old") }
        val obsolete = File(root, "obsolete").apply { writeText("old") }
        val source = File(root, "new").apply { writeText("new") }
        val batch = RecoverableFileBatch(File(root, "journal"), root)
        batch.apply(linkedMapOf(first to source, obsolete to null))
        batch.recover()
        assertEquals("new", first.readText())
        assertFalse(obsolete.exists())
    }
}
