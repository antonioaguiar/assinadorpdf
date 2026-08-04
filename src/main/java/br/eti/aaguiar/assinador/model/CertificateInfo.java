package br.eti.aaguiar.assinador.model;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;

public class CertificateInfo {
    private final String alias;
    private final String name;
    private final String cpf;
    private final String cnpj;
    private final Date expirationDate;
    private final X509Certificate certificate;
    private final KeyStore keyStore;
    private final boolean isA3;
    private final char[] password;

    public CertificateInfo(String alias, String name, String cpf, String cnpj, Date expirationDate, 
                           X509Certificate certificate, KeyStore keyStore, boolean isA3, char[] password) {
        this.alias = alias;
        this.name = name;
        this.cpf = cpf;
        this.cnpj = cnpj;
        this.expirationDate = expirationDate;
        this.certificate = certificate;
        this.keyStore = keyStore;
        this.isA3 = isA3;
        this.password = password;
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public String getCpf() {
        return cpf;
    }

    public String getCnpj() {
        return cnpj;
    }

    public boolean isPessoaJuridica() {
        return cnpj != null;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public boolean isA3() {
        return isA3;
    }

    public char[] getPassword() {
        return password;
    }

    @Override
    public String toString() {
        if (isPessoaJuridica()) {
            return name + " (CNPJ: " + cnpj + ")";
        }
        return name + " (CPF: " + (cpf != null ? cpf : "Não encontrado") + ")";
    }
}
