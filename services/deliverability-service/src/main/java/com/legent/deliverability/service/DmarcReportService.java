package com.legent.deliverability.service;

import com.legent.deliverability.domain.DmarcReport;
import com.legent.deliverability.repository.DmarcReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DmarcReportService {
    private final DmarcReportRepository repo;

    public DmarcReport ingestReport(String domain, String xml, String summary, String type) {
        DmarcReport report = new DmarcReport();
        report.setDomain(domain);
        report.setXmlContent(xml);
        report.setParsedSummary(summary);
        report.setReportType(type);
        report.setReceivedAt(Instant.now());
        return repo.save(report);
    }
}
