package br.eti.aaguiar.assinador;

import br.eti.aaguiar.assinador.model.CertificateInfo;
import br.eti.aaguiar.assinador.service.CryptoService;
import br.eti.aaguiar.assinador.service.SigningService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

public class TestSignPdf {

    public static void main(String[] args) {
        System.out.println("=== Iniciando Teste de Assinatura Digital PDF ===");
        Security.addProvider(new BouncyCastleProvider());

        try {
            // 1. Gerar Certificado de Teste A1 (PKCS12)
            System.out.println("1. Gerando certificado digital de teste...");
            String password = "senha_teste_123";
            String alias = "teste_signer";
            String cn = "JOAO DA SILVA SA";
            String cpf = "12345678901";
            
            KeyStore ks = generateTestKeyStore(password, alias, cn, cpf);
            
            File pfxFile = new File("certificado_teste.pfx");
            try (FileOutputStream fos = new FileOutputStream(pfxFile)) {
                ks.store(fos, password.toCharArray());
            }
            System.out.println("   Certificado A1 gerado: " + pfxFile.getAbsolutePath());

            // 2. Gerar PDF de Teste
            System.out.println("2. Gerando PDF de teste...");
            File pdfFile = new File("documento_teste.pdf");
            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage();
                doc.addPage(page);
                
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                    cs.newLineAtOffset(50, 700);
                    cs.showText("DOCUMENTO DE TESTE DE ASSINATURA DIGITAL");
                    
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(0, -30);
                    cs.showText("Este documento serve para testar o fluxo completo de assinatura digital.");
                    cs.newLineAtOffset(0, -20);
                    cs.showText("O assinador Java colocará a estampa visual na área selecionada.");
                    cs.endText();
                }
                doc.save(pdfFile);
            }
            System.out.println("   PDF gerado: " + pdfFile.getAbsolutePath());

            // 3. Carregar Certificado com o CryptoService
            System.out.println("3. Carregando certificado usando CryptoService...");
            CryptoService cryptoService = new CryptoService();
            List<CertificateInfo> certs = cryptoService.loadA1(pfxFile, password);
            if (certs.isEmpty()) {
                throw new RuntimeException("Nenhum certificado carregado pelo CryptoService!");
            }
            CertificateInfo certInfo = certs.get(0);
            System.out.println("   Nome extraído: " + certInfo.getName());
            System.out.println("   CPF extraído: " + certInfo.getCpf());
            System.out.println("   Validade: " + certInfo.getExpirationDate());

            // 4. Assinar PDF
            System.out.println("4. Assinando o PDF com SigningService...");
            File signedPdfFile = new File("documento_teste_assinado.pdf");
            SigningService signingService = new SigningService();
            
            // Assinar na página 0, coordenadas X=100, Y=200, largura=200, altura=50
            signingService.signPdf(
                    pdfFile,
                    signedPdfFile,
                    certInfo,
                    0,    // page index
                    100f, // x
                    200f, // y
                    200f, // width
                    50f,  // height
                    null
            );
            System.out.println("   PDF assinado com sucesso: " + signedPdfFile.getAbsolutePath());

            // Limpar arquivos temporários gerados exceto o assinado para inspeção visual
            pfxFile.delete();
            pdfFile.delete();
            System.out.println("\n=== Teste finalizado com SUCESSO! ===");
            System.out.println("Arquivo de resultado para validação visual: " + signedPdfFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Erro durante o teste: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static KeyStore generateTestKeyStore(String password, String alias, String cn, String cpf) throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        
        X500Name dnName = new X500Name("CN=" + cn + ", C=BR, O=ICP-Brasil");
        
        // Generate certificate
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName,
                BigInteger.valueOf(System.currentTimeMillis()),
                new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24),
                new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365),
                dnName,
                keyPair.getPublic()
        );
        
        // Add SAN OID for CPF (2.16.76.1.3.1)
        // DOB (8 chars) + CPF (11 chars)
        String valueStr = "01011990" + cpf + "000000000000000000000000000";
        DERUTF8String derString = new DERUTF8String(valueStr);
        DERTaggedObject taggedObj = new DERTaggedObject(true, 0, derString);
        
        ASN1EncodableVector vector = new ASN1EncodableVector();
        vector.add(new ASN1ObjectIdentifier("2.16.76.1.3.1"));
        vector.add(taggedObj);
        DERSequence otherNameSeq = new DERSequence(vector);
        
        GeneralName gn = new GeneralName(GeneralName.otherName, otherNameSeq);
        GeneralNames gns = new GeneralNames(gn);
        certBuilder.addExtension(Extension.subjectAlternativeName, false, gns);
        
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());
                
        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(alias, keyPair.getPrivate(), password.toCharArray(), new Certificate[]{cert});
        return ks;
    }
}
