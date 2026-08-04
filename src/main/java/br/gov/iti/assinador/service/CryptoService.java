package br.gov.iti.assinador.service;

import br.gov.iti.assinador.model.CertificateInfo;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1OctetString;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

public class CryptoService {

    public List<CertificateInfo> loadA1(File pfxFile, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(pfxFile)) {
            ks.load(fis, password.toCharArray());
        }
        return readCertificates(ks, false, password.toCharArray());
    }

    public List<CertificateInfo> loadA3() throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        KeyStore ks;
        if (os.contains("win")) {
            ks = KeyStore.getInstance("Windows-MY");
        } else if (os.contains("mac")) {
            ks = KeyStore.getInstance("KeychainStore");
        } else {
            throw new UnsupportedOperationException("Sistema operacional não suportado para certificados A3 nativos.");
        }
        ks.load(null, null);
        return readCertificates(ks, true, null);
    }

    private List<CertificateInfo> readCertificates(KeyStore ks, boolean isA3, char[] password) throws Exception {
        List<CertificateInfo> certs = new ArrayList<>();
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) {
                X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
                if (cert != null) {
                    // Filtrar certificados vencidos ou não recomendados
                    // Para facilitar uso do usuário, listamos todos mas extraímos informações
                    String cn = extractCN(cert.getSubjectX500Principal().getName());
                    String cpf = extractCPF(cert);
                    String cnpj = extractCNPJ(cert);
                    
                    certs.add(new CertificateInfo(
                            alias,
                            cn,
                            cpf,
                            cnpj,
                            cert.getNotAfter(),
                            cert,
                            ks,
                            isA3,
                            password
                    ));
                }
            }
        }
        return certs;
    }

    private String extractCN(String dn) {
        try {
            LdapName ldapDN = new LdapName(dn);
            for (Rdn rdn : ldapDN.getRdns()) {
                if (rdn.getType().equalsIgnoreCase("CN")) {
                    return (String) rdn.getValue();
                }
            }
        } catch (Exception e) {
            // fallback para retornar o próprio DN se falhar
        }
        return dn;
    }

    private String extractCPF(X509Certificate cert) {
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans == null) {
                return null;
            }
            for (List<?> list : sans) {
                int type = (Integer) list.get(0);
                if (type == 0) { // otherName
                    Object value = list.get(1);
                    byte[] derBytes = null;
                    if (value instanceof byte[]) {
                        derBytes = (byte[]) value;
                    }
                    if (derBytes != null) {
                        try (ASN1InputStream asn1Input = new ASN1InputStream(derBytes)) {
                            ASN1Primitive primitive = asn1Input.readObject();
                            if (primitive instanceof ASN1Sequence) {
                                ASN1Sequence seq = (ASN1Sequence) primitive;
                                if (seq.size() >= 2) {
                                    ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) seq.getObjectAt(0);
                                    // OID do CPF no ICP-Brasil
                                    if ("2.16.76.1.3.1".equals(oid.getId())) {
                                        ASN1TaggedObject taggedObject = (ASN1TaggedObject) seq.getObjectAt(1);
                                        // Obtém o objeto encapsulado na tag
                                        ASN1Primitive innerObj = (ASN1Primitive) taggedObject.getBaseObject();
                                        
                                        String content = null;
                                        if (innerObj instanceof ASN1String) {
                                            content = ((ASN1String) innerObj).getString();
                                        } else if (innerObj instanceof ASN1OctetString) {
                                            content = new String(((ASN1OctetString) innerObj).getOctets());
                                        }
                                        
                                        if (content != null && content.length() >= 19) {
                                            // ICP-Brasil CPF format: DDMMAAAACPF... (CPF starts at index 8 and is 11 chars)
                                            return content.substring(8, 19);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log ou ignorar se não conseguir extrair
        }
        return null;
    }

    private String extractCNPJ(X509Certificate cert) {
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans == null) {
                return null;
            }
            for (List<?> list : sans) {
                int type = (Integer) list.get(0);
                if (type == 0) {
                    Object value = list.get(1);
                    byte[] derBytes = null;
                    if (value instanceof byte[]) {
                        derBytes = (byte[]) value;
                    }
                    if (derBytes != null) {
                        try (ASN1InputStream asn1Input = new ASN1InputStream(derBytes)) {
                            ASN1Primitive primitive = asn1Input.readObject();
                            if (primitive instanceof ASN1Sequence) {
                                ASN1Sequence seq = (ASN1Sequence) primitive;
                                if (seq.size() >= 2) {
                                    ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) seq.getObjectAt(0);
                                    if ("2.16.76.1.3.3".equals(oid.getId())) {
                                        ASN1TaggedObject taggedObject = (ASN1TaggedObject) seq.getObjectAt(1);
                                        ASN1Primitive innerObj = (ASN1Primitive) taggedObject.getBaseObject();
                                        
                                        String content = null;
                                        if (innerObj instanceof ASN1String) {
                                            content = ((ASN1String) innerObj).getString();
                                        } else if (innerObj instanceof ASN1OctetString) {
                                            content = new String(((ASN1OctetString) innerObj).getOctets());
                                        }
                                        
                                        if (content != null && content.length() >= 14) {
                                            return content.substring(0, 14);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log ou ignorar se não conseguir extrair
        }
        return null;
    }
}
