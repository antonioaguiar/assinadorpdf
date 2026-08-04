package br.eti.aaguiar.assinador;

import br.eti.aaguiar.assinador.model.CertificateInfo;
import br.eti.aaguiar.assinador.service.CryptoService;

import java.util.List;

public class DebugCertificates {
    public static void main(String[] args) {
        System.out.println("=== Testando Carga de Certificados A3 (Nativo) ===");
        System.out.println("SO: " + System.getProperty("os.name"));
        
        CryptoService cryptoService = new CryptoService();
        try {
            List<CertificateInfo> certs = cryptoService.loadA3();
            System.out.println("Quantidade de certificados encontrados: " + certs.size());
            for (int i = 0; i < certs.size(); i++) {
                CertificateInfo info = certs.get(i);
                System.out.println(String.format("[%d] %s", i + 1, info.getName()));
                System.out.println("    CPF: " + (info.getCpf() != null ? info.getCpf() : "Não encontrado/não é ICP-Brasil"));
                System.out.println("    Validade: " + info.getExpirationDate());
                System.out.println("    Alias: " + info.getAlias());
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar certificados: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
