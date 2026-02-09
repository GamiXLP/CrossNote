package crossnote.desktop

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage

class DesktopApp : Application() {

    override fun start(stage: Stage) {

        val loader = FXMLLoader(
            DesktopApp::class.java.getResource("/MainView.fxml")
        )

        val root = loader.load<javafx.scene.Parent>()

        stage.scene = Scene(root, 1050.0, 700.0)
        stage.title = "CrossNote"
        stage.show()
    }
}

fun main() {
    Application.launch(DesktopApp::class.java)
}
