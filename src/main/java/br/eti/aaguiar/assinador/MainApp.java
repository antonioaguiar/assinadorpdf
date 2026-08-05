package br.eti.aaguiar.assinador;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/br/gov/iti/assinador/main.fxml"));
            Parent root = loader.load();
            
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/br/gov/iti/assinador/icon.png")));
            primaryStage.setTitle("Assinador Digital PDF - ICP-Brasil");
            primaryStage.setScene(new Scene(root));
            primaryStage.setMinWidth(960);
            primaryStage.setMinHeight(700);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Erro ao carregar FXML da aplicação.");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
