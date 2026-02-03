package crossnote.infra.persistence

import crossnote.domain.note.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryNoteRepository : NoteRepository {
    private val data = ConcurrentHashMap<String, Note>()

    override fun save(note: Note) {
        data[note.id.value] = note
    }

    override fun findById(id: NoteId): Note? = data[id.value]

    override fun findAll(): List<Note> = data.values.toList()

    override fun deleteById(id: NoteId) {
        data.remove(id.value)
    }
}

class UuidIdGenerator : IdGenerator {
    override fun newId(): NoteId = NoteId(UUID.randomUUID().toString())
}

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}