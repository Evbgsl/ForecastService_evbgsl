// Подключаем стандартные библиотеки Java для работы с файлами, сетью и временем ожидания (timeout)
import java.io.IOException;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

// Подключаем библиотеку org.json, помогает разбирать JSON-ответ от сервера
import org.json.JSONObject;
import org.json.JSONArray;

public class Main {
    public static void main(String[] args) {



        // ============================================================
        // 1) Загружаем конфигурацию из файла config.properties
        // ============================================================

        // Создаём объект Properties — это как словарь "ключ → значение"
        Properties config = new Properties();

        // Открываем файл src/config.properties для чтения
        // и загружаем все настройки (API ключ, координаты, количество дней)
        // API ключ помещен в конфиг, чтобы не светить его публично
        try (FileInputStream fis = new FileInputStream("src/config.properties")) {
            // считываем пары ключ=значение из файла
            config.load(fis);
        } catch (IOException e) {
            // Если файл не найден или не читается — выводим ошибку и завершаем программу
            System.err.println("Ошибка: не удалось прочитать config.properties");
            return;
        }



        // ============================================================
        // 2) Извлекаем параметры из конфигурации
        // ============================================================

        // Получаем API-ключ (нужен для доступа к Я.Погоде)
        String apiKey = config.getProperty("yandex.api.key");

        // Проверяем, что ключ действительно указан в файле
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Ошибка: API ключ не найден в config.properties");
            return; // завершаем программу, так как запрос без ключа не сработает
        }
        // Получаем координаты точки (широта и долгота) и количество дней прогноза
        // getProperty вернет строку по названию свойства - распарсим ее в double и int
        double lat = Double.parseDouble(config.getProperty("latitude"));
        double lon = Double.parseDouble(config.getProperty("longitude"));
        int days = Integer.parseInt(config.getProperty("days"));



        // ============================================================
        // 3) Формируем URL для запроса
        // ============================================================

        // Подставляем координаты и количество дней в адрес API
        // Чтобы десятичный разделитель был точкой, а не запятой, указавыем Locale.US
        // Формат запроса получаем из документации к API Яндекс
        String url = String.format(Locale.US,
                "https://api.weather.yandex.ru/v2/forecast?lat=%f&lon=%f&limit=%d",
                lat, lon, days);



        // ============================================================
        // 4) Создаём HTTP клиент и формируем запрос
        // ============================================================

        // Используем встроенный в Java инструмент (HttpClient) для отправки HTTP-запросов
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)) // ждём максимум 10 секунд соединения
                .build();

        // HttpRequest — описывает, какой запрос мы отправляем
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))                      // адрес запроса
                .timeout(Duration.ofSeconds(20))           // общее время ожидания
                .header("X-Yandex-Weather-Key", apiKey)    // добавляем API-ключ в заголовок
                .GET()                                     // указываем, что это GET-запрос
                .build();



        // ============================================================
        // 5) Отправляем запрос и обрабатываем ответ
        // ============================================================

        try {
            // Отправляем запрос и получаем ответ в виде строки (JSON)
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Выводим код ответа HTTP (200 — всё ок)
            System.out.println("HTTP status: " + response.statusCode());

            // Выводим весь JSON-ответ для проверки
            System.out.println("Полный JSON-ответ:");
            System.out.println(response.body());



            // ========================================================
            // 6) Разбираем JSON-ответ с помощью библиотеки org.json
            // ========================================================

            // Создаём объект JSON из текстового ответа
            JSONObject json = new JSONObject(response.body());

            // Достаём из JSON объект "fact" — в нём текущая погода
            JSONObject fact = json.getJSONObject("fact");

            // Извлекаем температуру (в градусах Цельсия) и выводим значение
            int currentTemp = fact.getInt("temp");
            System.out.println("\nТекущая температура: " + currentTemp + "°C");



            // ========================================================
            // 7) Работа с прогнозом (массив "forecasts")
            // ========================================================

            System.out.println("\nПрогноз на ближайшие дни:");

            // Извлекаем массив "forecasts" — список прогнозов по дням
            JSONArray forecasts = json.getJSONArray("forecasts");

            // Проходим по каждому дню и выводим дату и среднюю температуру
            for (int i = 0; i < forecasts.length(); i++) {
                JSONObject dayForecast = forecasts.getJSONObject(i); // один элемент массива
                String date = dayForecast.getString("date");         // дата прогноза

                // Внутри "parts" -> "day" лежит средняя температура
                JSONObject parts = dayForecast.getJSONObject("parts");
                JSONObject day = parts.getJSONObject("day");

                int tempAvg = day.getInt("temp_avg"); // средняя температура днём
                System.out.println(date + ": " + tempAvg + "°C");
            }



            // ========================================================
            // 8) Вычисляем среднюю температуру по всем дням прогноза
            // ========================================================
            int sumTemp = 0;
            for (int i = 0; i < forecasts.length(); i++) {
                JSONObject dayForecast = forecasts.getJSONObject(i);
                JSONObject parts = dayForecast.getJSONObject("parts");
                JSONObject day = parts.getJSONObject("day");
                sumTemp += day.getInt("temp_avg"); // суммируем температуры
            }

            // Делим сумму на количество дней, получаем среднее арифметическое
            double avgTemp = (double) sumTemp / forecasts.length();

            System.out.printf("\nСредняя температура по прогнозу на %d дня(дней): %.1f°C\n",
                    forecasts.length(), avgTemp);

        } catch (IOException | InterruptedException e) {
            // Если возникли ошибки при запросе
            System.err.println("Ошибка запроса: " + e.getMessage());

            // Восстанавливаем статус прерывания потока (важно для корректной работы)
            Thread.currentThread().interrupt();
        }
    }
}