package com.legent.deliverability.service;

import org.springframework.stereotype.Component;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.util.ArrayList;
import java.util.List;

@Component
public class DnsJavaTxtResolver implements DnsTxtResolver {

    @Override
    public List<String> lookupTxt(String name) throws TextParseException {
        Lookup lookup = new Lookup(name, Type.TXT);
        Record[] records = lookup.run();
        if (records == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Record record : records) {
            TXTRecord txt = (TXTRecord) record;
            values.add(String.join(" ", txt.getStrings()));
        }
        return values;
    }
}
