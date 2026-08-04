package br.eti.aaguiar.assinador.controller;

import br.eti.aaguiar.assinador.model.CertificateInfo;
import br.eti.aaguiar.assinador.service.CryptoService;
import br.eti.aaguiar.assinador.service.SigningService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;

public class MainController {

    // Serviços
    private final CryptoService cryptoService = new CryptoService();
    private final SigningService signingService = new SigningService();

    // Estado da Aplicação
    private File currentPdfFile;
    private int currentPageIndex = 0;
    private int totalPages = 0;
    private float pageWidthPoints = 0f;
    private float pageHeightPoints = 0f;
    private CertificateInfo activeCertificate;
    private File selectedA1File;
    
    // Dados de posicionamento da assinatura
    private int signaturePageIndex = -1;
    private float signaturePdfX = 0f;
    private float signaturePdfY = 0f;

    // Tamanho padrão da estampa (em pontos PDF)
    private static final float SIG_WIDTH = 200f;
    private static final float SIG_HEIGHT = 50f;

    @FXML private ToggleGroup certTypeGroup;
    @FXML private RadioButton rbA3;
    @FXML private RadioButton rbA1;
    @FXML private VBox vboxA3;
    @FXML private VBox vboxA1;
    @FXML private ComboBox<CertificateInfo> cbA3Certs;
    @FXML private Label lblA1Path;
    @FXML private PasswordField pfA1Password;
    
    @FXML private VBox vboxCertInfo;
    @FXML private Label lblCertName;
    @FXML private Label lblCpfTitle;
    @FXML private Label lblCertCpf;
    @FXML private Label lblCertExpiry;
    
    @FXML private Label lblPdfName;
    @FXML private Label lblSignPage;
    @FXML private Label lblSignX;
    @FXML private Label lblSignY;
    @FXML private Button btnSign;

    @FXML private Button btnPrevPage;
    @FXML private Button btnNextPage;
    @FXML private Label lblPageIndicator;

    @FXML private Pane pdfPane;
    @FXML private ImageView pdfImageView;
    @FXML private Rectangle signatureRect;
    @FXML private Label lblStatus;

    @FXML
    public void initialize() {
        // Alternar visibilidade de A1 e A3 baseado na seleção
        certTypeGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (rbA3.isSelected()) {
                vboxA3.setVisible(true);
                vboxA3.setManaged(true);
                vboxA1.setVisible(false);
                vboxA1.setManaged(false);
                setActiveCertificate(cbA3Certs.getSelectionModel().getSelectedItem());
            } else {
                vboxA1.setVisible(true);
                vboxA1.setManaged(true);
                vboxA3.setVisible(false);
                vboxA3.setManaged(false);
                setActiveCertificate(null); // Limpar até carregar o arquivo A1
                lblA1Path.setText("Nenhum arquivo selecionado");
                selectedA1File = null;
                pfA1Password.clear();
            }
        });

        // Escutar seleção de certificado no ComboBox A3
        cbA3Certs.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (rbA3.isSelected()) {
                setActiveCertificate(newValue);
            }
        });

        // Carregar certificados A3 assincronamente ao iniciar
        Platform.runLater(this::loadA3CertificatesSilently);
    }

    private void loadA3CertificatesSilently() {
        lblStatus.setText("Buscando certificados instalados...");
        try {
            List<CertificateInfo> certs = cryptoService.loadA3();
            cbA3Certs.setItems(FXCollections.observableArrayList(certs));
            if (!certs.isEmpty()) {
                cbA3Certs.getSelectionModel().select(0);
                lblStatus.setText("Certificados A3 carregados.");
            } else {
                lblStatus.setText("Nenhum certificado A3 encontrado no repositório.");
            }
        } catch (Exception e) {
            lblStatus.setText("Não foi possível acessar os certificados A3 do sistema.");
        }
    }

    @FXML
    void handleRefreshA3(ActionEvent event) {
        lblStatus.setText("Atualizando lista de certificados A3...");
        try {
            List<CertificateInfo> certs = cryptoService.loadA3();
            cbA3Certs.setItems(FXCollections.observableArrayList(certs));
            if (!certs.isEmpty()) {
                cbA3Certs.getSelectionModel().select(0);
                lblStatus.setText("Lista de certificados A3 atualizada.");
            } else {
                cbA3Certs.getSelectionModel().clearSelection();
                setActiveCertificate(null);
                lblStatus.setText("Nenhum certificado A3 encontrado.");
                showInfoAlert("Certificados A3", "Nenhum certificado digital foi encontrado no repositório de chaves do sistema.");
            }
        } catch (Exception e) {
            lblStatus.setText("Erro ao listar certificados A3.");
            showErrorAlert("Erro de Acesso", "Erro ao acessar repositório de chaves: " + e.getMessage());
        }
    }

    @FXML
    void handleSelectA1File(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar Certificado A1");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Certificados Digitais (*.pfx, *.p12)", "*.pfx", "*.p12")
        );
        File file = fileChooser.showOpenDialog(getStage());
        if (file != null) {
            selectedA1File = file;
            lblA1Path.setText(file.getName());
            lblStatus.setText("Arquivo A1 selecionado: " + file.getName());
        }
    }

    @FXML
    void handleLoadA1(ActionEvent event) {
        if (selectedA1File == null) {
            showWarningAlert("Carregar Certificado A1", "Por favor, selecione primeiro um arquivo de certificado (.pfx ou .p12).");
            return;
        }
        String password = pfA1Password.getText();
        if (password == null || password.isEmpty()) {
            showWarningAlert("Senha Necessária", "Insira a senha do certificado.");
            return;
        }

        lblStatus.setText("Carregando certificado A1...");
        try {
            List<CertificateInfo> certs = cryptoService.loadA1(selectedA1File, password);
            if (!certs.isEmpty()) {
                // Pegar o primeiro certificado com chave privada do arquivo
                setActiveCertificate(certs.get(0));
                lblStatus.setText("Certificado A1 carregado com sucesso.");
                pfA1Password.clear();
            } else {
                lblStatus.setText("Nenhum certificado válido encontrado no arquivo.");
                showWarningAlert("Certificado Inválido", "Nenhum certificado de chave pública/privada utilizável foi encontrado dentro do arquivo.");
            }
        } catch (Exception e) {
            lblStatus.setText("Falha ao carregar certificado A1.");
            showErrorAlert("Erro de Autenticação", "Senha incorreta ou arquivo corrompido: " + e.getMessage());
        }
    }

    private void setActiveCertificate(CertificateInfo cert) {
        this.activeCertificate = cert;
        if (cert != null) {
            lblCertName.setText(cert.getName());
            if (cert.isPessoaJuridica()) {
                lblCpfTitle.setText("CNPJ: ");
                lblCertCpf.setText(cert.getCnpj());
            } else {
                lblCpfTitle.setText("CPF: ");
                lblCertCpf.setText(cert.getCpf() != null ? cert.getCpf() : "Não encontrado");
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            lblCertExpiry.setText(sdf.format(cert.getExpirationDate()));
        } else {
            lblCertName.setText("Nenhum certificado ativo");
            lblCpfTitle.setText("CPF: ");
            lblCertCpf.setText("N/D");
            lblCertExpiry.setText("N/D");
        }
        checkSignButtonState();
    }

    @FXML
    void handleSelectPdf(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar Arquivo PDF para Assinatura");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos PDF (*.pdf)", "*.pdf"));
        File file = fileChooser.showOpenDialog(getStage());
        
        if (file != null) {
            currentPdfFile = file;
            lblPdfName.setText(file.getName());
            currentPageIndex = 0;
            
            // Resetar marcação de assinatura
            signaturePageIndex = -1;
            signatureRect.setVisible(false);
            lblSignPage.setText("Nenhuma");
            lblSignX.setText("-");
            lblSignY.setText("-");
            
            loadPdfMetadataAndRender();
        }
    }

    private void loadPdfMetadataAndRender() {
        if (currentPdfFile == null) return;
        
        lblStatus.setText("Carregando PDF...");
        try (PDDocument doc = Loader.loadPDF(currentPdfFile)) {
            totalPages = doc.getNumberOfPages();
            
            // Carrega dimensões da página atual
            PDPage page = doc.getPage(currentPageIndex);
            PDRectangle mediaBox = page.getMediaBox();
            pageWidthPoints = mediaBox.getWidth();
            pageHeightPoints = mediaBox.getHeight();
            
            renderCurrentPage();
            
            btnPrevPage.setDisable(currentPageIndex == 0);
            btnNextPage.setDisable(currentPageIndex >= totalPages - 1);
            lblPageIndicator.setText(String.format("Página: %d / %d", currentPageIndex + 1, totalPages));
            lblStatus.setText("PDF carregado.");
        } catch (Exception e) {
            lblStatus.setText("Erro ao abrir PDF.");
            showErrorAlert("Erro no PDF", "Não foi possível carregar o arquivo PDF: " + e.getMessage());
        }
    }

    private void renderCurrentPage() {
        if (currentPdfFile == null) return;
        
        try (PDDocument doc = Loader.loadPDF(currentPdfFile)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            // Renderiza com 110 DPI (um bom meio termo para a tela do visualizador)
            BufferedImage bufferedImage = renderer.renderImageWithDPI(currentPageIndex, 110);
            Image image = SwingFXUtils.toFXImage(bufferedImage, null);
            
            pdfImageView.setImage(image);
            
            // Ajustar o tamanho do container Pane para bater perfeitamente com a imagem renderizada
            // Isso garante que os cliques fiquem restritos exatamente à imagem do PDF
            pdfPane.setMaxWidth(image.getWidth());
            pdfPane.setMaxHeight(image.getHeight());
            
            // Se a assinatura está nessa página, posiciona o retângulo visual
            if (signaturePageIndex == currentPageIndex) {
                updateVisualSignatureRect();
                signatureRect.setVisible(true);
            } else {
                signatureRect.setVisible(false);
            }
        } catch (Exception e) {
            lblStatus.setText("Erro ao renderizar página.");
        }
    }

    @FXML
    void handlePrevPage(ActionEvent event) {
        if (currentPageIndex > 0) {
            currentPageIndex--;
            loadPdfMetadataAndRender();
        }
    }

    @FXML
    void handleNextPage(ActionEvent event) {
        if (currentPageIndex < totalPages - 1) {
            currentPageIndex++;
            loadPdfMetadataAndRender();
        }
    }

    @FXML
    void handlePdfClick(MouseEvent event) {
        if (currentPdfFile == null) return;

        double clickX = event.getX();
        double clickY = event.getY();
        double imgWidth = pdfImageView.getBoundsInLocal().getWidth();
        double imgHeight = pdfImageView.getBoundsInLocal().getHeight();

        // 1. Converter pixels da tela do ImageView para pontos PDF
        float pdfX = (float) (clickX / imgWidth) * pageWidthPoints;
        float pdfY = pageHeightPoints - (float) (clickY / imgHeight) * pageHeightPoints;

        // 2. Centralizar o box de assinatura no ponto clicado
        float finalPdfX = pdfX - (SIG_WIDTH / 2f);
        float finalPdfY = pdfY - (SIG_HEIGHT / 2f);

        // 3. Limitar os limites para não desenhar fora da página PDF
        if (finalPdfX < 0) finalPdfX = 0;
        if (finalPdfX + SIG_WIDTH > pageWidthPoints) finalPdfX = pageWidthPoints - SIG_WIDTH;
        if (finalPdfY < 0) finalPdfY = 0;
        if (finalPdfY + SIG_HEIGHT > pageHeightPoints) finalPdfY = pageHeightPoints - SIG_HEIGHT;

        // Guardar estado do ponto
        signaturePageIndex = currentPageIndex;
        signaturePdfX = finalPdfX;
        signaturePdfY = finalPdfY;

        // Atualizar textos informativos
        lblSignPage.setText(String.valueOf(signaturePageIndex + 1));
        lblSignX.setText(String.format("%.1f", signaturePdfX));
        lblSignY.setText(String.format("%.1f", signaturePdfY));

        // Atualizar o retângulo na tela
        updateVisualSignatureRect();
        signatureRect.setVisible(true);
        
        lblStatus.setText(String.format("Assinatura posicionada em X: %.1f, Y: %.1f", signaturePdfX, signaturePdfY));
        checkSignButtonState();
    }

    private void updateVisualSignatureRect() {
        double imgWidth = pdfImageView.getBoundsInLocal().getWidth();
        double imgHeight = pdfImageView.getBoundsInLocal().getHeight();

        // Converter as dimensões da estampa para pixels proporcionais na tela
        double rectWidthPixels = (SIG_WIDTH / pageWidthPoints) * imgWidth;
        double rectHeightPixels = (SIG_HEIGHT / pageHeightPoints) * imgHeight;

        // Converter a coordenada inferior esquerda do PDF para a coordenada superior esquerda do JavaFX
        double rectLeftPixels = (signaturePdfX / pageWidthPoints) * imgWidth;
        double rectTopPixels = (1.0 - (signaturePdfY + SIG_HEIGHT) / pageHeightPoints) * imgHeight;

        signatureRect.setWidth(rectWidthPixels);
        signatureRect.setHeight(rectHeightPixels);
        signatureRect.setLayoutX(rectLeftPixels);
        signatureRect.setLayoutY(rectTopPixels);
    }

    private void checkSignButtonState() {
        boolean canSign = activeCertificate != null 
                && currentPdfFile != null 
                && signaturePageIndex != -1;
        btnSign.setDisable(!canSign);
    }

    @FXML
    void handleSignDocument(ActionEvent event) {
        if (activeCertificate == null || currentPdfFile == null || signaturePageIndex == -1) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salvar PDF Assinado");
        fileChooser.setInitialFileName(currentPdfFile.getName().replace(".pdf", "_assinado.pdf"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));
        File outputFile = fileChooser.showSaveDialog(getStage());

        if (outputFile != null) {
            lblStatus.setText("Realizando assinatura digital...");
            btnSign.setDisable(true);
            
            // Executa em thread separada para não travar a UI durante a criptografia (essencial para A3)
            new Thread(() -> {
                try {
                    signingService.signPdf(
                            currentPdfFile,
                            outputFile,
                            activeCertificate,
                            signaturePageIndex,
                            signaturePdfX,
                            signaturePdfY,
                            SIG_WIDTH,
                            SIG_HEIGHT
                    );
                    
                    Platform.runLater(() -> {
                        lblStatus.setText("Documento assinado com sucesso!");
                        showInfoAlert("Assinatura Concluída", "O documento foi assinado digitalmente e salvo com sucesso em:\n" + outputFile.getAbsolutePath());
                        checkSignButtonState();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        lblStatus.setText("Erro ao assinar documento.");
                        showErrorAlert("Falha na Assinatura", "Ocorreu um erro ao assinar o PDF:\n" + e.getMessage());
                        checkSignButtonState();
                    });
                }
            }).start();
        }
    }

    private Stage getStage() {
        return (Stage) btnSign.getScene().getWindow();
    }

    private void showInfoAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    void handleOpenITI(ActionEvent event) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://validar.iti.gov.br/index.html"));
        } catch (Exception e) {
            showErrorAlert("Erro", "Não foi possível abrir o link no navegador.");
        }
    }

    private void showWarningAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
