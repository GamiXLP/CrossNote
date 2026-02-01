package crossnote.desktop

import crossnote.app.note.NoteAppService
import crossnote.infra.persistence.InMemoryNoteRepository
import crossnote.infra.persistence.SystemClock
import crossnote.infra.persistence.UuidIdGenerator
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.stage.Stage

class DesktopApp : Application() {
    override fun start(stage: Stage) {
        val service = NoteAppService(
            repo = InMemoryNoteRepository(),
            ids = UuidIdGenerator(),
            clock = SystemClock()
        )

        val label = Label("CrossNote läuft ✅")
        val button = Button("Test-Notiz erstellen").apply {
            setOnAction {
                service.createNote("Hallo", "Erste Notiz")
                label.text = "Notizen: ${service.listNotes().size}"
            }
        }

        val root = VBox(10.0, label, button)
        stage.scene = Scene(root, 400.0, 200.0)
        stage.title = "CrossNote MVP"
        stage.show()
    }
}

fun main() {
    Application.launch(DesktopApp::class.java)
}