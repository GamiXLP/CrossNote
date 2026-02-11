package crossnote.domain.note

object TextConstraints {
    const val NOTE_TITLE_MAX = 120
    const val NOTE_TITLE_MIN = 0 // leer erlaubt, wird im UI als "(Ohne Titel)" dargestellt

    const val NOTEBOOK_NAME_MAX = 60
    const val NOTEBOOK_NAME_MIN = 1 // Ordnername darf nicht leer sein
}

class ValidationException(message: String) : IllegalArgumentException(message)

fun validateNoteTitle(raw: String): String {
    val t = raw.trim()
    if (t.length > TextConstraints.NOTE_TITLE_MAX) {
        throw ValidationException("Titel ist zu lang (max. ${TextConstraints.NOTE_TITLE_MAX} Zeichen).")
    }
    return t
}

fun validateNotebookName(raw: String): String {
    val n = raw.trim()
    if (n.isEmpty()) {
        throw ValidationException("Ordnername darf nicht leer sein.")
    }
    if (n.length > TextConstraints.NOTEBOOK_NAME_MAX) {
        throw ValidationException("Ordnername ist zu lang (max. ${TextConstraints.NOTEBOOK_NAME_MAX} Zeichen).")
    }
    return n
}
