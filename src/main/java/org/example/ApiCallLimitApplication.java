package org.example;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiCallLimitApplication {

    public static void main(String[] args) {

        exampleOfCallingApiInMultipleThreads();

    }

    /**
     * Метод демонстрирует работу с объектом ExternalApi в многопоточной среде.
     */
    public static void exampleOfCallingApiInMultipleThreads() {

        /*
         * для наглядности можно изменить значение константы ExternalApi.Constants.TIME_DELAY (по умолчанию = 1)
         * например, указав TIME_DELAY = 5 со значениями параметров ниже будет означать:
         * максимум 3 запроса (requestLimit) каждые 5 секунд
         */

        int requestLimit = 3;
        int nThreads = 30;
        int nTasks = 100;
        TimeUnit timeUnit = TimeUnit.SECONDS;

        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        AtomicInteger counter = new AtomicInteger(0);
        List<CompletableFuture<HttpResponse<String>>> taskList = new ArrayList<>(nTasks);

        for (int i = 0; i < nTasks; i++) {
            var task = CompletableFuture.supplyAsync(() ->
                            ExternalApi.of(timeUnit, requestLimit).callDocumentCreationApi(
                                    newDocument(counter.incrementAndGet()), "token"),
                            executor);
            taskList.add(task);
        }

        taskList.forEach(ApiCallLimitApplication::getTaskResult);
        System.exit(1);
    }

    private static <T> T getTaskResult(CompletableFuture<T> task) {
        try {
            return task.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static ExternalApi.Document newDocument(int docNumber) {
        ExternalApi.Product product = new ExternalApi.Product(
                "",
                LocalDate.now(),
                "",
                "",
                "",
                LocalDate.now(),
                "",
                "",
                "");

        return new ExternalApi.Document(
                Objects.toString(docNumber),
                new ExternalApi.DocDescription(""),
                "",
                "",
                false,
                "",
                "",
                "",
                LocalDate.now(),
                "",
                List.of(product),
                LocalDate.now(),
                "");
    }
}
