package crossnote.desktop

import crossnote.desktop.controller.MainController
import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage

class DesktopApp : Application() {

    private var mainController: MainController? = null

    override fun start(stage: Stage) {
        val loader = FXMLLoader(
            DesktopApp::class.java.getResource("/MainView.fxml")
        )

        val root = loader.load<javafx.scene.Parent>()

        // Controller merken, damit wir im stop() auch sauber schließen können
        mainController = loader.getController()

        val scene = Scene(root, 1050.0, 700.0)
        scene.stylesheets.add(DesktopApp::class.java.getResource("/styles.css")!!.toExternalForm())
        stage.scene = scene

        stage.title = "CrossNote"
        stage.icons.add(
            javafx.scene.image.Image(
                DesktopApp::class.java.getResourceAsStream("/images/CrossNote_Icon.png")
            )
        )

        stage.setOnCloseRequest {
            try {
                mainController?.close()
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                // JavaFX sauber beenden
                Platform.exit()
            }
        }

        stage.show()
    }

    override fun stop() {
        // Fallback: wird bei Platform.exit() ebenfalls aufgerufen
        try {
            mainController?.close()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}

fun main() {
    Application.launch(DesktopApp::class.java)
}
