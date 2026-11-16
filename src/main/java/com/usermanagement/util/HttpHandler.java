package com.usermanagement.util;

import java.net.URI;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP calls to the Keycloak REST API (same methods as HttpHandler in bm-usermanagement-v4).
 * Errors are thrown as Spring's RestClientResponseException / ResourceAccessException and translated by
 * KeycloakServiceProvider.
 */
public final class HttpHandler {

    /** Shared client; tests bind a MockRestServiceServer to it. */
    private static final RestTemplate REST_TEMPLATE = new RestTemplate();

    private HttpHandler() {
    }

    public static RestTemplate restTemplate() {
        return REST_TEMPLATE;
    }

    /** POSTs the form body to the token endpoint and returns the raw JSON response. */
    public static String getToken(String url, String body) {
        return REST_TEMPLATE.postForObject(url, setHeader(body, MediaType.APPLICATION_FORM_URLENCODED), String.class);
    }

    /** POSTs the body and returns the value of the given response header (e.g. Location of a created user). */
    public static <B> String postWithLocationResponse(String url, String token, String headerName, B body) {
        ResponseEntity<?> response = httpCall(url, setHeader(body, token), HttpMethod.POST);
        var result = "";
        var headers = response.getHeaders().get(headerName);
        if (headers != null) {
            result = headers.getFirst();
        }
        return result;
    }

    public static <B> String post(String url, String token, B body) {
        return getResponseValue(url, setHeader(body, token), HttpMethod.POST);
    }

    public static <B> String put(String url, String token, B body) {
        return getResponseValue(url, setHeader(body, token), HttpMethod.PUT);
    }

    public static <B> String del(String url, String token, B body) {
        return getResponseValue(url, setHeader(body, token), HttpMethod.DELETE);
    }

    public static <B> String get(String url, String token, B body) {
        return getResponseValue(url, setHeader(body, token), HttpMethod.GET);
    }

    public static String userSearch(String url, String search, String token) {
        var uri = URI.create(url + "?search=" + search);
        return REST_TEMPLATE.exchange(uri, HttpMethod.GET, setHeader(null, token), String.class).getBody();
    }

    private static <B> HttpEntity<?> setHeader(B body, String token) {
        return setHeader(body, token, MediaType.APPLICATION_JSON);
    }

    private static String getResponseValue(String url, HttpEntity<?> request, HttpMethod method) {
        Object response = httpCall(url, request, method).getBody();
        var result = "";
        if (response != null) {
            result = response.toString();
        }
        return result;
    }

    private static <B> HttpEntity<?> setHeader(B body, MediaType mediaType) {
        var headers = new HttpHeaders();
        headers.setContentType(mediaType);
        return setHeader(body, headers);
    }

    private static <B> HttpEntity<?> setHeader(B body, String token, MediaType mediaType) {
        var headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set("Authorization", "Bearer " + token);
        return setHeader(body, headers);
    }

    private static <B> HttpEntity<?> setHeader(B body, HttpHeaders headers) {
        if (body != null) {
            return new HttpEntity<>(body, headers);
        }
        return new HttpEntity<>(headers);
    }

    private static ResponseEntity<?> httpCall(String url, HttpEntity<?> request, HttpMethod method) {
        return REST_TEMPLATE.exchange(url, method, request, String.class);
    }
}
