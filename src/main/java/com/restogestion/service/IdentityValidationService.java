package com.restogestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class IdentityValidationService {

    private final RestClient restClient;
    private final String token;

    public IdentityValidationService(
            RestClient.Builder restClientBuilder,
            @Value("${app.identity-api.base-url}") String baseUrl,
            @Value("${app.identity-api.token}") String token) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.token = token;
    }

    public DocumentoValidado validar(String tipo, String numero) {
        String tipoNormalizado = tipo == null ? "" : tipo.trim().toUpperCase();
        String numeroNormalizado = numero == null ? "" : numero.trim();

        validarFormato(tipoNormalizado, numeroNormalizado);

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Falta configurar IDENTITY_API_TOKEN para consultar DNI/RUC");
        }

        JsonNode response = restClient.get()
                .uri("/v1/{tipo}/{numero}", tipoNormalizado.toLowerCase(), numeroNormalizado)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, result) -> {
                    throw new IllegalArgumentException("La API de validación respondió con HTTP " + result.getStatusCode().value());
                })
                .body(JsonNode.class);

        if (response == null || !response.path("success").asBoolean(false)) {
            String mensaje = response == null ? "Respuesta vacía" : response.path("message").asText("Documento no encontrado");
            throw new IllegalArgumentException(mensaje);
        }

        JsonNode data = response.path("data");
        if ("DNI".equals(tipoNormalizado)) {
            String nombre = unir(
                    data.path("nombres").asText(),
                    data.path("apellido_paterno").asText(),
                    data.path("apellido_materno").asText());
            return new DocumentoValidado(tipoNormalizado, numeroNormalizado, nombre, "", "VALIDADO");
        }

        String razonSocial = data.path("razon_social").asText("");
        String direccion = data.path("direccion_completa").asText(data.path("direccion").asText(""));
        String estado = data.path("estado").asText("VALIDADO");
        return new DocumentoValidado(tipoNormalizado, numeroNormalizado, razonSocial, direccion, estado);
    }

    private void validarFormato(String tipo, String numero) {
        if ("DNI".equals(tipo) && !numero.matches("\\d{8}")) {
            throw new IllegalArgumentException("El DNI debe tener 8 dígitos");
        }
        if ("RUC".equals(tipo) && !numero.matches("\\d{11}")) {
            throw new IllegalArgumentException("El RUC debe tener 11 dígitos");
        }
        if (!"DNI".equals(tipo) && !"RUC".equals(tipo)) {
            throw new IllegalArgumentException("Tipo de documento no válido");
        }
    }

    private String unir(String... partes) {
        return java.util.Arrays.stream(partes)
                .filter(parte -> parte != null && !parte.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    public record DocumentoValidado(String tipo, String numero, String nombre, String direccion, String estado) {}
}
