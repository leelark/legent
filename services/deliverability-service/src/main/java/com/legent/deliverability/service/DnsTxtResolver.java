package com.legent.deliverability.service;

import org.xbill.DNS.TextParseException;

import java.util.List;

public interface DnsTxtResolver {
    List<String> lookupTxt(String name) throws TextParseException;
}
