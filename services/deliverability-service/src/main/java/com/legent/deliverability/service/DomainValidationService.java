package com.legent.deliverability.service;

import com.legent.deliverability.domain.DomainConfig;
import com.legent.deliverability.repository.DomainConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.Hashtable;

@Service
@RequiredArgsConstructor
public class DomainValidationService {
    private final DomainConfigRepository domainRepo;

    @Value("${legent.deliverability.mock-dns:false}")
    private boolean mockDns;

    public DomainConfig validateDomain(String domain) {
        DomainConfig config = domainRepo.findByDomain(domain);
        if (config == null) return null;

        if (mockDns) {
            config.setSpfStatus("PASS");
            config.setDkimStatus("PASS");
            config.setDmarcStatus("PASS");
        } else {
            config.setSpfStatus(checkDnsTxtRecord(domain, "v=spf1") ? "PASS" : "FAIL");
            config.setDkimStatus(checkDnsTxtRecord("legent._domainkey." + domain, "v=DKIM1") ? "PASS" : "FAIL");
            config.setDmarcStatus(checkDnsTxtRecord("_dmarc." + domain, "v=DMARC1") ? "PASS" : "FAIL");
        }

        config.setLastChecked(Instant.now());
        config.setStatus("PASS".equals(config.getSpfStatus()) && "PASS".equals(config.getDkimStatus()) ? "VERIFIED" : "PENDING");
        return Objects.requireNonNull(domainRepo.save(config));
    }

    private boolean checkDnsTxtRecord(String recordName, String requiredValue) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            InitialDirContext dirContext = new InitialDirContext(env);
            Attributes attrs = dirContext.getAttributes(recordName, new String[]{"TXT"});
            Attribute txt = attrs.get("TXT");
            if (txt != null) {
                for (int i = 0; i < txt.size(); i++) {
                    String value = (String) txt.get(i);
                    if (value != null && value.contains(requiredValue)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
