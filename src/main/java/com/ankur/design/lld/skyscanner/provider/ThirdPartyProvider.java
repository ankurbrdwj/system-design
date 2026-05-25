package com.ankur.design.lld.skyscanner.provider;

/**
 * Contract every provider adapter must implement.
 * Each adapter translates the canonical SearchRequest into provider-specific
 * HTTP/SOAP/REST calls and maps the response back to List<Quote>.
 */
public interface ThirdPartyProvider {
    String id();
    ProviderType type();
    ProviderResponse fetch(SearchRequest request) throws Exception;
}