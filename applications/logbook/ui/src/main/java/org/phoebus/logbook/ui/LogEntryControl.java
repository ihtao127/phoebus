package org.phoebus.logbook.ui;

import java.io.IOException;

import org.phoebus.logging.LogEntry;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;

public class LogEntryControl extends VBox {

    private final LogEntryController controller;

    public LogEntryControl() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("LogEntryDisplay.fxml"));
        fxmlLoader.setRoot(this);
        this.controller = new LogEntryController();
        fxmlLoader.setController(this.controller);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void setLog(LogEntry logEntry) {
        controller.setLogEntry(logEntry);
    }

}
