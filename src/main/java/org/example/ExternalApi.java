package org.example;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.example.ExternalApi.Constants.*;

public class ExternalApi {

    private static volatile ExternalApi instance;
    private final int requestLimit;
    private final Semaphore semaphore;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private ExternalApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit, true);
        // инициализируем scheduler, который будет освобождать слоты семафора с заданной периодичностью
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(this::releaseOccupiedSlots, TIME_DELAY, TIME_DELAY, timeUnit);
    }

    /**
     * Метод создания экземпляра ExternalApi (thread-safe).
     *
     * @param timeUnit     единица времени, в которой задается ограничение вызова API
     * @param requestLimit максимальное количество запросов в единицу времени
     * @return             экземпляр ExternalApi
     */
    public static ExternalApi of(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException(
                    String.format("Parameter 'requestLimit' must be positive. Current value [%s].", requestLimit));
        }
        if (instance == null) {
            synchronized (ExternalApi.class) {
                if (instance == null) {
                    instance = new ExternalApi(timeUnit, requestLimit);
                }
            }
        }
        return instance;
    }

    /**
     * Метод вызова публичного API.
     *
     * @param document  создаваемый документ
     * @param authToken токен авторизации
     * @return объект типа {@link HttpResponse}
     * @throws ApiCallException любая ошибка, возникшая при работе метода
     */
    public HttpResponse<String> callDocumentCreationApi(Document document, String authToken) {
        try {
            semaphore.acquire();
            return apiRequest(convertObjectToJson(document), authToken);
        } catch (Exception e) {
            throw new ApiCallException(e);
        }
    }

    private <T> String convertObjectToJson(T object) throws JsonProcessingException {
        return mapper.writeValueAsString(object);
    }

    private HttpResponse<String> apiRequest(String jsonDocument, String authToken) throws URISyntaxException, IOException, InterruptedException {

        log("API request: " + jsonDocument);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(API_ADDRESS))
                .timeout(Duration.of(RESPONSE_TIMEOUT_IN_SECONDS, SECONDS))
                .header("Content-Type", "text/plain;charset=UTF-8")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Освобождает занятые слоты семафора
     */
    private void releaseOccupiedSlots() {
        int countOccupiedSlots = requestLimit - semaphore.availablePermits();
        semaphore.release(countOccupiedSlots);
        log("Semaphore slots are free");
    }

    private static void log(String str) {
        System.out.println(str);
    }

    /**
     * Константы для работы с API
     */
    interface Constants {
        int TIME_DELAY = 1;
        int RESPONSE_TIMEOUT_IN_SECONDS = 30;
        String DATE_FORMAT = "yyyy-MM-dd";
        String API_ADDRESS = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    }

    public record Document(@JsonProperty("doc_id") String docId,
                           @JsonProperty("description") DocDescription description,
                           @JsonProperty("doc_status") String docStatus,
                           @JsonProperty("doc_type") String docType,
                           @JsonProperty("importRequest") boolean importRequest,
                           @JsonProperty("owner_inn") String ownerInn,
                           @JsonProperty("participant_inn") String participantInn,
                           @JsonProperty("producer_inn") String producerInn,
                           @JsonProperty("production_date") @JsonFormat(pattern = DATE_FORMAT) LocalDate productionDate,
                           @JsonProperty("production_type") String productionType,
                           @JsonProperty("products") List<Product> products,
                           @JsonProperty("reg_date") @JsonFormat(pattern = DATE_FORMAT) LocalDate regDate,
                           @JsonProperty("reg_number") String regNumber) {
    }

    public record DocDescription(@JsonProperty("participantInn") String participantInn) {
    }

    public record Product(@JsonProperty("certificate_document") String certificateDocument,
                          @JsonProperty("certificate_document_date") @JsonFormat(pattern = DATE_FORMAT) LocalDate certificateDocumentDate,
                          @JsonProperty("certificate_document_number") String certificateDocumentNumber,
                          @JsonProperty("owner_inn") String ownerInn,
                          @JsonProperty("producer_inn") String producerInn,
                          @JsonProperty("production_date") @JsonFormat(pattern = DATE_FORMAT) LocalDate productionDate,
                          @JsonProperty("tnved_code") String tnvedCode,
                          @JsonProperty("uit_code") String uitCode,
                          @JsonProperty("uitu_code") String uituCode) {
    }

    public static class ApiCallException extends RuntimeException {
        public ApiCallException(Throwable cause) {
            super(cause);
        }
    }
}
