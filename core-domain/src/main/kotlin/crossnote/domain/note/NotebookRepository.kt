package crossnote.domain.note

interface NotebookRepository {
    fun save(notebook: Notebook)
    fun findAll(): List<Notebook>
    fun findById(id: NotebookId): Notebook?
    fun delete(id: NotebookId)
}