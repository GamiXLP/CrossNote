package crossnote.app.note

import crossnote.domain.note.Clock
import crossnote.domain.note.IdGenerator
import crossnote.domain.note.NoteId
import crossnote.domain.note.Notebook
import crossnote.domain.note.NotebookId
import crossnote.domain.note.NotebookRepository
import crossnote.infra.persistence.InMemoryNoteRepository
import crossnote.infra.persistence.InMemoryRevisionRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteAppServiceTest {

    @Test
    fun `createNote saves a new note`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()
        val service = createService(
            repo = repo,
            revisionRepo = revisionRepo,
            idGenerator = FixedIdGenerator("note-1"),
            clock = FixedClock(Instant.parse("2025-01-01T10:00:00Z"))
        )

        val id = service.createNote("  Mein Titel  ", "Inhalt")

        val saved = repo.findById(id)
        requireNotNull(saved)

        assertEquals("note-1", id.value)
        assertEquals("Mein Titel", saved.title)
        assertEquals("Inhalt", saved.content)
        assertNull(saved.notebookId)
        assertFalse(saved.isTrashed())
    }

    @Test
    fun `updateNote updates note and stores revision snapshot`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()
        val initialTime = Instant.parse("2025-01-01T10:00:00Z")
        val updateTime = Instant.parse("2025-01-01T11:00:00Z")

        val noteId = NoteId("note-1")
        repo.save(
            crossnote.domain.note.Note(
                id = noteId,
                notebookId = null,
                title = "Alt",
                content = "Alter Inhalt",
                createdAt = initialTime,
                updatedAt = initialTime,
                trashedAt = null
            )
        )

        val service = createService(
            repo = repo,
            revisionRepo = revisionRepo,
            idGenerator = FixedIdGenerator("unused"),
            clock = SequenceClock(listOf(updateTime, updateTime))
        )

        service.updateNote(noteId, "Neu", "Neuer Inhalt")

        val updated = repo.findById(noteId)
        requireNotNull(updated)

        assertEquals("Neu", updated.title)
        assertEquals("Neuer Inhalt", updated.content)
        assertEquals(updateTime, updated.updatedAt)

        val revisions = revisionRepo.findByNoteId(noteId)
        assertEquals(1, revisions.size)
        assertEquals("Alt", revisions.first().title)
        assertEquals("Alter Inhalt", revisions.first().content)
    }

    @Test
    fun `moveToTrash marks note as trashed`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()
        val now = Instant.parse("2025-01-02T10:00:00Z")
        val noteId = seedNote(repo)

        val service = createService(
            repo = repo,
            revisionRepo = revisionRepo,
            idGenerator = FixedIdGenerator("unused"),
            clock = FixedClock(now)
        )

        service.moveToTrash(noteId)

        val note = repo.findById(noteId)
        requireNotNull(note)

        assertTrue(note.isTrashed())
        assertEquals(now, note.trashedAt)
        assertEquals(now, note.updatedAt)
    }

    @Test
    fun `restore removes trashed flag`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()
        val trashedAt = Instant.parse("2025-01-02T08:00:00Z")
        val restoreTime = Instant.parse("2025-01-03T09:00:00Z")
        val noteId = NoteId("note-1")

        repo.save(
            crossnote.domain.note.Note(
                id = noteId,
                notebookId = null,
                title = "Titel",
                content = "Inhalt",
                createdAt = Instant.parse("2025-01-01T10:00:00Z"),
                updatedAt = Instant.parse("2025-01-02T08:00:00Z"),
                trashedAt = trashedAt
            )
        )

        val service = createService(
            repo = repo,
            revisionRepo = revisionRepo,
            idGenerator = FixedIdGenerator("unused"),
            clock = FixedClock(restoreTime)
        )

        service.restore(noteId)

        val restored = repo.findById(noteId)
        requireNotNull(restored)

        assertFalse(restored.isTrashed())
        assertNull(restored.trashedAt)
        assertEquals(restoreTime, restored.updatedAt)
    }

    @Test
    fun `listActiveNotes returns only active notes sorted by updatedAt descending`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()

        repo.save(
            crossnote.domain.note.Note(
                id = NoteId("1"),
                notebookId = null,
                title = "Älter",
                content = "",
                createdAt = Instant.parse("2025-01-01T10:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T10:00:00Z"),
                trashedAt = null
            )
        )
        repo.save(
            crossnote.domain.note.Note(
                id = NoteId("2"),
                notebookId = null,
                title = "Neuer",
                content = "",
                createdAt = Instant.parse("2025-01-01T11:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T11:00:00Z"),
                trashedAt = null
            )
        )
        repo.save(
            crossnote.domain.note.Note(
                id = NoteId("3"),
                notebookId = null,
                title = "Papierkorb",
                content = "",
                createdAt = Instant.parse("2025-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T12:00:00Z"),
                trashedAt = Instant.parse("2025-01-02T12:00:00Z")
            )
        )

        val service = createService(repo, revisionRepo)

        val result = service.listActiveNotes()

        assertEquals(2, result.size)
        assertEquals("2", result[0].id)
        assertEquals("1", result[1].id)
    }

    @Test
    fun `listTrashedNotes returns only trashed notes`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()

        repo.save(
            crossnote.domain.note.Note(
                id = NoteId("1"),
                notebookId = null,
                title = "Aktiv",
                content = "",
                createdAt = Instant.parse("2025-01-01T10:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T10:00:00Z"),
                trashedAt = null
            )
        )
        repo.save(
            crossnote.domain.note.Note(
                id = NoteId("2"),
                notebookId = null,
                title = "Gelöscht",
                content = "",
                createdAt = Instant.parse("2025-01-01T11:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T11:00:00Z"),
                trashedAt = Instant.parse("2025-01-02T12:00:00Z")
            )
        )

        val service = createService(repo, revisionRepo)

        val result = service.listTrashedNotes()

        assertEquals(1, result.size)
        assertEquals("2", result.first().id)
    }

    @Test
    fun `purgeNotePermanently deletes trashed note and its revisions`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()
        val noteId = NoteId("note-1")
        val time = Instant.parse("2025-01-01T10:00:00Z")

        repo.save(
            crossnote.domain.note.Note(
                id = noteId,
                notebookId = null,
                title = "Titel",
                content = "Inhalt",
                createdAt = time,
                updatedAt = time,
                trashedAt = Instant.parse("2025-01-02T10:00:00Z")
            )
        )

        revisionRepo.save(
            crossnote.domain.revision.Revision(
                id = crossnote.domain.revision.RevisionId("rev-1"),
                noteId = noteId,
                title = "Titel",
                content = "Inhalt",
                createdAt = time
            )
        )

        val service = createService(repo, revisionRepo)

        service.purgeNotePermanently(noteId)

        assertNull(repo.findById(noteId))
        assertTrue(revisionRepo.findByNoteId(noteId).isEmpty())
    }

    @Test
    fun `purgeNotePermanently throws for active note`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()
        val noteId = seedNote(repo)

        val service = createService(repo, revisionRepo)

        assertFailsWith<IllegalStateException> {
            service.purgeNotePermanently(noteId)
        }
    }

    @Test
    fun `moveNoteToNotebook updates notebookId`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()
        val noteId = seedNote(repo)
        val targetNotebookId = NotebookId("nb-1")
        val moveTime = Instant.parse("2025-01-03T12:00:00Z")

        val service = createService(
            repo = repo,
            revisionRepo = revisionRepo,
            clock = FixedClock(moveTime)
        )

        service.moveNoteToNotebook(noteId, targetNotebookId)

        val moved = repo.findById(noteId)
        requireNotNull(moved)

        assertEquals(targetNotebookId, moved.notebookId)
        assertEquals(moveTime, moved.updatedAt)
    }

    @Test
    fun `moveNoteToNotebook throws for trashed note`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()
        val noteId = NoteId("note-1")

        repo.save(
            crossnote.domain.note.Note(
                id = noteId,
                notebookId = null,
                title = "Titel",
                content = "Inhalt",
                createdAt = Instant.parse("2025-01-01T10:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T10:00:00Z"),
                trashedAt = Instant.parse("2025-01-02T10:00:00Z")
            )
        )

        val service = createService(repo, revisionRepo)

        assertFailsWith<IllegalStateException> {
            service.moveNoteToNotebook(noteId, NotebookId("nb-1"))
        }
    }

    @Test
    fun `listNotebookTree groups root notes and notebook notes alphabetically`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()

        val notebookA = Notebook(NotebookId("a"), "Arbeit")
        val notebookB = Notebook(NotebookId("b"), "Privat")

        repo.save(
            crossnote.domain.note.Note(
                id = NoteId("1"),
                notebookId = null,
                title = "Zoo",
                content = "",
                createdAt = Instant.parse("2025-01-01T10:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T10:00:00Z"),
                trashedAt = null
            )
        )
        repo.save(
            crossnote.domain.note.Note(
                id = NoteId("2"),
                notebookId = null,
                title = "Alpha",
                content = "",
                createdAt = Instant.parse("2025-01-01T11:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T11:00:00Z"),
                trashedAt = null
            )
        )
        repo.save(
            crossnote.domain.note.Note(
                id = NoteId("3"),
                notebookId = notebookB.id,
                title = "B-Note",
                content = "",
                createdAt = Instant.parse("2025-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T12:00:00Z"),
                trashedAt = null
            )
        )
        repo.save(
            crossnote.domain.note.Note(
                id = NoteId("4"),
                notebookId = notebookA.id,
                title = "A-Note",
                content = "",
                createdAt = Instant.parse("2025-01-01T13:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T13:00:00Z"),
                trashedAt = null
            )
        )

        val service = createService(repo, revisionRepo)

        val tree = service.listNotebookTree(
            notebookRepo = FakeNotebookRepository(listOf(notebookB, notebookA))
        )

        assertEquals(listOf("Alpha", "Zoo"), tree.rootNotes.map { it.title })
        assertEquals(listOf("Arbeit", "Privat"), tree.notebooks.map { it.name })
        assertEquals(listOf("A-Note"), tree.notebooks[0].notes.map { it.title })
        assertEquals(listOf("B-Note"), tree.notebooks[1].notes.map { it.title })
    }

    private fun createService(
        repo: InMemoryNoteRepository = InMemoryNoteRepository(),
        revisionRepo: InMemoryRevisionRepository = InMemoryRevisionRepository(),
        idGenerator: IdGenerator = FixedIdGenerator("generated-id"),
        clock: Clock = FixedClock(Instant.parse("2025-01-01T10:00:00Z"))
    ): NoteAppService {
        return NoteAppService(
            repo = repo,
            revisionRepo = revisionRepo,
            ids = idGenerator,
            clock = clock
        )
    }

    private fun seedNote(repo: InMemoryNoteRepository): NoteId {
        val noteId = NoteId("note-1")
        repo.save(
            crossnote.domain.note.Note(
                id = noteId,
                notebookId = null,
                title = "Titel",
                content = "Inhalt",
                createdAt = Instant.parse("2025-01-01T10:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T10:00:00Z"),
                trashedAt = null
            )
        )
        return noteId
    }

    private class FixedIdGenerator(private val id: String) : IdGenerator {
        override fun newId(): NoteId = NoteId(id)
    }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private class SequenceClock(private val instants: List<Instant>) : Clock {
        private var index = 0

        override fun now(): Instant {
            val result = instants[index]
            if (index < instants.lastIndex) {
                index++
            }
            return result
        }
    }

    private class FakeNotebookRepository(
        private val notebooks: List<Notebook>
    ) : NotebookRepository {
        override fun save(notebook: Notebook) = Unit

        override fun findAll(): List<Notebook> = notebooks

        override fun findById(id: NotebookId): Notebook? =
            notebooks.find { it.id == id }

        override fun delete(id: NotebookId) = Unit
    }

    @Test
    fun `restoreFromRevision restores note content from revision`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()

        val noteId = NoteId("note-1")

        val original = crossnote.domain.note.Note(
            id = noteId,
            notebookId = null,
            title = "Alt",
            content = "Alt Inhalt",
            createdAt = Instant.parse("2025-01-01T10:00:00Z"),
            updatedAt = Instant.parse("2025-01-01T10:00:00Z"),
            trashedAt = null
        )

        repo.save(original)

        val revision = crossnote.domain.revision.Revision(
            id = crossnote.domain.revision.RevisionId("rev-1"),
            noteId = noteId,
            title = "Revision Titel",
            content = "Revision Inhalt",
            createdAt = Instant.parse("2025-01-01T09:00:00Z")
        )

        revisionRepo.save(revision)

        val restoreTime = Instant.parse("2025-01-02T10:00:00Z")

        val service = createService(
            repo = repo,
            revisionRepo = revisionRepo,
            clock = FixedClock(restoreTime)
        )

        service.restoreFromRevision(noteId, revision.id)

        val restored = repo.findById(noteId)!!
        val revisions = revisionRepo.findByNoteId(noteId)

        assertEquals("Revision Titel", restored.title)
        assertEquals("Revision Inhalt", restored.content)
        assertEquals(restoreTime, restored.updatedAt)

        // wichtig: Snapshot wurde erstellt
        assertEquals(2, revisions.size)
    }

    @Test
    fun `restoreFromRevision throws if revision belongs to different note`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()

        val noteId = seedNote(repo)

        val foreignRevision = crossnote.domain.revision.Revision(
            id = crossnote.domain.revision.RevisionId("rev-1"),
            noteId = NoteId("other"),
            title = "X",
            content = "Y",
            createdAt = Instant.now()
        )

        revisionRepo.save(foreignRevision)

        val service = createService(repo, revisionRepo)

        assertFailsWith<IllegalStateException> {
            service.restoreFromRevision(noteId, foreignRevision.id)
        }
    }

    @Test
    fun `purgeTrashedOlderThan removes only old trashed notes`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()

        val now = Instant.parse("2025-01-10T10:00:00Z")

        val oldTrashed = crossnote.domain.note.Note(
            id = NoteId("old"),
            notebookId = null,
            title = "",
            content = "",
            createdAt = now.minusSeconds(100000),
            updatedAt = now.minusSeconds(100000),
            trashedAt = now.minusSeconds(100000)
        )

        val recentTrashed = crossnote.domain.note.Note(
            id = NoteId("recent"),
            notebookId = null,
            title = "",
            content = "",
            createdAt = now.minusSeconds(1000),
            updatedAt = now.minusSeconds(1000),
            trashedAt = now.minusSeconds(1000)
        )

        val active = crossnote.domain.note.Note(
            id = NoteId("active"),
            notebookId = null,
            title = "",
            content = "",
            createdAt = now,
            updatedAt = now,
            trashedAt = null
        )

        repo.save(oldTrashed)
        repo.save(recentTrashed)
        repo.save(active)

        val service = createService(
            repo = repo,
            revisionRepo = revisionRepo,
            clock = FixedClock(now)
        )

        service.purgeTrashedOlderThan(1) // 1 Tag

        assertNull(repo.findById(NoteId("old")))
        assertEquals(2, repo.findAll().size)
    }

    @Test
    fun `createNotebook returns id for valid name`() {
        val service = createService()

        val id = service.createNotebook("Mein Ordner")

        assertTrue(id.value.isNotBlank())
    }

    @Test
    fun `getNote returns existing note`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()
        val noteId = seedNote(repo)

        val service = createService(repo, revisionRepo)

        val note = service.getNote(noteId)

        assertEquals(noteId, note.id)
        assertEquals("Titel", note.title)
    }

    @Test
    fun `getNote throws when note does not exist`() {
        val service = createService()

        assertFailsWith<IllegalStateException> {
            service.getNote(NoteId("missing"))
        }
    }

    @Test
    fun `getRevision returns existing revision`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()

        val revision = crossnote.domain.revision.Revision(
            id = crossnote.domain.revision.RevisionId("rev-1"),
            noteId = NoteId("note-1"),
            title = "Titel",
            content = "Inhalt",
            createdAt = Instant.parse("2025-01-01T10:00:00Z")
        )
        revisionRepo.save(revision)

        val service = createService(repo, revisionRepo)

        val result = service.getRevision(revision.id)

        assertEquals(revision.id, result.id)
        assertEquals("Titel", result.title)
    }

    @Test
    fun `getRevision throws when revision does not exist`() {
        val service = createService()

        assertFailsWith<IllegalStateException> {
            service.getRevision(crossnote.domain.revision.RevisionId("missing"))
        }
    }

    @Test
    fun `deleteRevision removes revision`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()

        val revisionId = crossnote.domain.revision.RevisionId("rev-1")
        revisionRepo.save(
            crossnote.domain.revision.Revision(
                id = revisionId,
                noteId = NoteId("note-1"),
                title = "Titel",
                content = "Inhalt",
                createdAt = Instant.parse("2025-01-01T10:00:00Z")
            )
        )

        val service = createService(repo, revisionRepo)

        service.deleteRevision(revisionId)

        assertEquals(null, revisionRepo.findById(revisionId))
    }

    @Test
    fun `clockNowForUi returns clock time`() {
        val now = Instant.parse("2025-01-05T15:30:00Z")

        val service = createService(clock = FixedClock(now))

        assertEquals(now, service.clockNowForUi())
    }

    @Test
    fun `restoreFromRevision restores note from revision`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()

        val noteId = NoteId("note-1")
        repo.save(
            crossnote.domain.note.Note(
                id = noteId,
                notebookId = null,
                title = "Aktuell",
                content = "Aktueller Inhalt",
                createdAt = Instant.parse("2025-01-01T10:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T10:00:00Z"),
                trashedAt = null
            )
        )

        val revision = crossnote.domain.revision.Revision(
            id = crossnote.domain.revision.RevisionId("rev-1"),
            noteId = noteId,
            title = "Alter Titel",
            content = "Alter Inhalt",
            createdAt = Instant.parse("2025-01-01T09:00:00Z")
        )
        revisionRepo.save(revision)

        val restoreTime = Instant.parse("2025-01-02T10:00:00Z")
        val service = createService(
            repo = repo,
            revisionRepo = revisionRepo,
            clock = FixedClock(restoreTime)
        )

        service.restoreFromRevision(noteId, revision.id)

        val restored = repo.findById(noteId)
        requireNotNull(restored)

        assertEquals("Alter Titel", restored.title)
        assertEquals("Alter Inhalt", restored.content)
        assertEquals(restoreTime, restored.updatedAt)
    }

    @Test
    fun `restoreFromRevision throws when revision belongs to another note`() {
        val repo = InMemoryNoteRepository()
        val revisionRepo = InMemoryRevisionRepository()
        val noteId = seedNote(repo)

        val revision = crossnote.domain.revision.Revision(
            id = crossnote.domain.revision.RevisionId("rev-x"),
            noteId = NoteId("different-note"),
            title = "X",
            content = "Y",
            createdAt = Instant.parse("2025-01-01T10:00:00Z")
        )
        revisionRepo.save(revision)

        val service = createService(repo, revisionRepo)

        assertFailsWith<IllegalStateException> {
            service.restoreFromRevision(noteId, revision.id)
        }
    }

    @Test
    fun `createNotebook throws for empty name`() {
        val service = createService()

        assertFailsWith<IllegalArgumentException> {
            service.createNotebook("   ")
        }
    }

    @Test
    fun `listRevisions returns empty list when none exist`() {
        val service = createService()

        val result = service.listRevisions(NoteId("unknown"))

        assertEquals(0, result.size)
    }
}