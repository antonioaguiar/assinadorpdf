package br.eti.aaguiar.assinador.service;

import br.eti.aaguiar.assinador.model.CertificateInfo;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class SigningService {

    public void signPdf(File inputPdf, File outputPdf, CertificateInfo certInfo, 
                        int pageIndex, float x, float y, float width, float height, String imagePath) throws Exception {
        
        try (PDDocument document = Loader.loadPDF(inputPdf)) {
            // 1. Criar dicionário de assinatura
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(certInfo.getName());
            
            Calendar signDate = Calendar.getInstance();
            signature.setSignDate(signDate);

            // Obter a página desejada
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                throw new IllegalArgumentException("Página selecionada inválida.");
            }

            PDRectangle rect = new PDRectangle(x, y, width, height);
            
            org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions options = new org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions();
            try (InputStream templateStream = createVisualSignatureTemplate(document, pageIndex, rect, signature, certInfo, imagePath)) {
                options.setVisualSignature(templateStream);
                options.setPage(pageIndex);

                // Interface que realiza a assinatura física dos bytes
                SignatureInterface signatureInterface = new SignatureInterface() {
                    @Override
                    public byte[] sign(InputStream content) throws IOException {
                        try {
                            byte[] data = content.readAllBytes();
                            
                            // Obter a chave privada
                            PrivateKey privateKey = (PrivateKey) certInfo.getKeyStore().getKey(
                                    certInfo.getAlias(),
                                    certInfo.getPassword()
                            );
                            
                            Certificate[] certChain = certInfo.getKeyStore().getCertificateChain(certInfo.getAlias());
                            List<Certificate> certList = Arrays.asList(certChain);

                            // Montar gerador CMS do BouncyCastle
                            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                            ContentSigner shaSigner = new JcaContentSignerBuilder("SHA256withRSA")
                                    .setProvider("BC")
                                    .build(privateKey);
                            
                            X509Certificate holderCert = (X509Certificate) certChain[0];
                            X509CertificateHolder certHolder = new X509CertificateHolder(holderCert.getEncoded());
                            
                            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                                    new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                                    .build(shaSigner, certHolder));
                            
                            gen.addCertificates(new JcaCertStore(certList));

                            CMSProcessableByteArray msg = new CMSProcessableByteArray(data);
                            CMSSignedData signedData = gen.generate(msg, false); // detached
                            
                            return signedData.getEncoded();
                        } catch (Exception e) {
                            throw new IOException("Falha ao assinar digitalmente: " + e.getMessage(), e);
                        }
                    }
                };

                // Adicionar BouncyCastle como provider de segurança se não estiver presente
                if (java.security.Security.getProvider("BC") == null) {
                    java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
                }

                // Adicionar assinatura ao documento e salvar incrementalmente
                document.addSignature(signature, signatureInterface, options);
                try (FileOutputStream fos = new FileOutputStream(outputPdf)) {
                    document.saveIncremental(fos);
                }
            }
        }
    }

    private InputStream createVisualSignatureTemplate(PDDocument srcDoc, int pageNum, PDRectangle rect, 
                                                      PDSignature signature, CertificateInfo certInfo, String imagePath) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(srcDoc.getPage(pageNum).getMediaBox());
            doc.addPage(page);
            PDAcroForm acroForm = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(acroForm);
            PDSignatureField signatureField = new PDSignatureField(acroForm);
            PDAnnotationWidget widget = signatureField.getWidgets().get(0);
            acroForm.getFields().add(signatureField);
            acroForm.setSignaturesExist(true);
            acroForm.setAppendOnly(true);
            acroForm.getCOSObject().setDirect(true);
            
            widget.setRectangle(rect);
            widget.setPage(page);
            widget.setPrinted(true);
            page.getAnnotations().add(widget);
            
            PDAppearanceDictionary appearance = new PDAppearanceDictionary();
            appearance.getCOSObject().setDirect(true);
            PDAppearanceStream appearanceStream = new PDAppearanceStream(doc);
            appearanceStream.setBBox(new PDRectangle(rect.getWidth(), rect.getHeight()));
            appearanceStream.setResources(new PDResources());
            appearance.setNormalAppearance(appearanceStream);
            widget.setAppearance(appearance);
            
            Calendar signDate = signature.getSignDate();
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            String formattedDate = sdf.format(signDate.getTime());
            
            try (PDPageContentStream cs = new PDPageContentStream(doc, appearanceStream)) {
                cs.setNonStrokingColor(Color.WHITE);
                cs.addRect(0, 0, rect.getWidth(), rect.getHeight());
                cs.fill();

                float textOffsetX = 6;
                if (imagePath != null && new File(imagePath).exists()) {
                    try {
                        org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdImage = org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromFile(imagePath, doc);
                        float imgSize = 40f;
                        float imgY = (rect.getHeight() - imgSize) / 2f;
                        cs.drawImage(pdImage, 5, imgY, imgSize, imgSize);
                        textOffsetX = imgSize + 15;
                    } catch (Exception e) {
                        System.err.println("Erro ao carregar imagem para a assinatura: " + e.getMessage());
                    }
                }

                cs.beginText();
                cs.setNonStrokingColor(Color.DARK_GRAY);
                
                PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                cs.setFont(titleFont, 7.5f);
                cs.newLineAtOffset(textOffsetX, rect.getHeight() - 11);
                cs.showText("Assinado digitalmente por:");

                String cleanName = sanitizeText(certInfo.getName());
                float maxNameWidth = rect.getWidth() - textOffsetX - 5;
                float nameFontSize = 7.5f;
                
                java.util.List<String> nameLines = new java.util.ArrayList<>();
                String[] words = cleanName.split(" ");
                StringBuilder currentLine = new StringBuilder();
                
                for (String word : words) {
                    if (currentLine.length() == 0) {
                        currentLine.append(word);
                    } else {
                        String testLine = currentLine.toString() + " " + word;
                        float testWidth = titleFont.getStringWidth(testLine) / 1000f * nameFontSize;
                        if (testWidth > maxNameWidth) {
                            nameLines.add(currentLine.toString());
                            currentLine = new StringBuilder(word);
                        } else {
                            currentLine.append(" ").append(word);
                        }
                    }
                }
                if (currentLine.length() > 0) {
                    nameLines.add(currentLine.toString());
                }

                cs.setFont(titleFont, nameFontSize);
                for (String line : nameLines) {
                    cs.newLineAtOffset(0, -9.5f);
                    cs.showText(line);
                }

                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7.0f);
                cs.newLineAtOffset(0, -9.5f);
                if (certInfo.isPessoaJuridica()) {
                    cs.showText("CNPJ: " + certInfo.getCnpj());
                } else {
                    String cpf = certInfo.getCpf() != null ? certInfo.getCpf() : "CPF N/D";
                    cs.showText("CPF: " + cpf);
                }

                cs.newLineAtOffset(0, -9.5f);
                cs.showText("Data: " + formattedDate);

                cs.endText();
            }
            
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            doc.save(baos);
            return new java.io.ByteArrayInputStream(baos.toByteArray());
        }
    }

    private String sanitizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("[áàâãä]", "a")
                   .replaceAll("[éèêë]", "e")
                   .replaceAll("[íìîï]", "i")
                   .replaceAll("[óòôõö]", "o")
                   .replaceAll("[úùûü]", "u")
                   .replaceAll("[ç]", "c")
                   .replaceAll("[ÁÀÂÃÄ]", "A")
                   .replaceAll("[ÉÈÊË]", "E")
                   .replaceAll("[ÍÌÎÏ]", "I")
                   .replaceAll("[ÓÒÔÕÖ]", "O")
                   .replaceAll("[ÚÙÛÜ]", "U")
                   .replaceAll("[Ç]", "C")
                   .replaceAll("[^\\p{ASCII}]", ""); // Remove caracteres especiais não ASCII restantes
    }
}
