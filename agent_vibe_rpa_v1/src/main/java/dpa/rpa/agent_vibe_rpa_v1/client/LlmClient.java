package dpa.rpa.agent_vibe_rpa_v1.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import dpa.rpa.agent_vibe_rpa_v1.client.tool.ToolDefinitions;
import dpa.rpa.agent_vibe_rpa_v1.dto.ModulePlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmClient {

    @Value("${llm.api.url}")
    private String llmApiUrl;

    @Value("${llm.api.key}")
    private String llmApiKey;

    @Value("${llm.model}")
    private String llmModel;

    @Value("classpath:prompts/planner_system.prompt")
    private Resource promptResource;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CatalogClient catalogClient;

    private String systemPrompt;

    private static final int MAX_ITERATIONS = 15;

    @PostConstruct
    public void init() {
        try {
            this.systemPrompt = StreamUtils.copyToString(promptResource.getInputStream(), StandardCharsets.UTF_8);
            log.info("✅ Системный промпт успешно загружен. Длина: {} символов", systemPrompt.length());
        } catch (IOException e) {
            log.error("❌ Критическая ошибка: не удалось загрузить файл промпта", e);
            throw new RuntimeException("Не удалось инициализировать LlmClient: файл промпта не найден", e);
        }
    }

    /**
     * Генерирует план через цикл tool calling.
     * LLM может вызывать инструменты list_activities и get_activity_card.
     */
    public ModulePlan generatePlanWithToolCalling(String moduleName, String rawDescription) {
        log.info("🚀 Начало генерации плана для модуля: {} (с tool calling)", moduleName);

        // Формируем начальную историю сообщений
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content",
                "Имя модуля: " + moduleName + "\n\nОписание задачи:\n" + rawDescription));

        int iteration = 0;

        while (iteration < MAX_ITERATIONS) {
            iteration++;
            log.info("🔄 Итерация tool calling: {}", iteration);

            // Вызов LLM
            Map<String, Object> response = callLlm(messages);
            Map<String, Object> choice = extractChoice(response);
            String finishReason = (String) choice.get("finish_reason");
            Map<String, Object> message = (Map<String, Object>) choice.get("message");

            // === Случай 1: LLM завершила генерацию (финальный план) ===
            if ("stop".equals(finishReason)) {
                String content = (String) message.get("content");
                log.info("✅ LLM вернула финальный план на итерации {}", iteration);
                return parsePlan(content);
            }

            // === Случай 2: LLM хочет вызвать инструменты ===
            if ("tool_calls".equals(finishReason)) {
                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

                // Добавляем сообщение ассистента с tool_calls в историю
                // Важно: content может быть null при tool_calls
                Map<String, Object> assistantMessage = new HashMap<>();
                assistantMessage.put("role", "assistant");
                assistantMessage.put("content", message.get("content")); // может быть null
                assistantMessage.put("tool_calls", toolCalls);
                messages.add(assistantMessage);

                // Выполняем каждый вызов инструмента
                for (Map<String, Object> toolCall : toolCalls) {
                    String toolCallId = (String) toolCall.get("id");
                    Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                    String functionName = (String) function.get("name");
                    String argumentsJson = (String) function.get("arguments");

                    log.info("🔧 Вызов инструмента: {} (args: {})", functionName, argumentsJson);

                    // Выполняем инструмент
                    String toolResult = executeTool(functionName, argumentsJson);

                    // Добавляем результат в историю
                    messages.add(Map.of(
                            "role", "tool",
                            "tool_call_id", toolCallId,
                            "content", toolResult
                    ));
                }
                continue;
            }

            // === Случай 3: Неожиданный finish_reason ===
            log.warn("⚠️ Неожиданный finish_reason: {}", finishReason);
            throw new RuntimeException("Неожиданный finish_reason от LLM: " + finishReason);
        }

        throw new RuntimeException("Превышен лимит итераций tool calling (" + MAX_ITERATIONS + ")");
    }

    /**
     * Выполняет вызов инструмента и возвращает результат в виде JSON-строки.
     */
    private String executeTool(String functionName, String argumentsJson) {
        try {
            Map<String, Object> args = argumentsJson != null && !argumentsJson.isBlank()
                    ? objectMapper.readValue(argumentsJson, Map.class)
                    : Map.of();

            Object result;
            switch (functionName) {
                case "list_activities":
                    String domain = (String) args.get("domain");
                    if (domain == null || domain.isBlank()) {
                        result = Map.of("error", "Параметр 'domain' обязателен");
                    } else {
                        result = catalogClient.getThinActivitiesByDomain(domain);
                    }
                    break;

                case "get_activity_card":
                    String activityId = (String) args.get("activity_id");
                    if (activityId == null || activityId.isBlank()) {
                        result = Map.of("error", "Параметр 'activity_id' обязателен");
                    } else {
                        result = catalogClient.getActivityCard(activityId);
                    }
                    break;

                default:
                    log.warn("Неизвестный инструмент: {}", functionName);
                    result = Map.of("error", "Unknown function: " + functionName);
            }

            String resultJson = objectMapper.writeValueAsString(result);
            log.debug("Результат инструмента {} ({} символов)", functionName, resultJson.length());
            return resultJson;

        } catch (Exception e) {
            log.error("Ошибка выполнения инструмента {}: {}", functionName, e.getMessage());
            try {
                return objectMapper.writeValueAsString(Map.of("error", e.getMessage()));
            } catch (Exception ex) {
                return "{\"error\": \"internal error\"}";
            }
        }
    }

    /**
     * Отправляет запрос к LLM API с учётом tools.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callLlm(List<Map<String, Object>> messages) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmModel);
        requestBody.put("messages", messages);
        requestBody.put("tools", ToolDefinitions.getTools());
        requestBody.put("tool_choice", "auto");
        requestBody.put("temperature", 0.1);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(llmApiUrl, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Пустой ответ от LLM API");
            }
            return body;
        } catch (Exception e) {
            log.error("Ошибка вызова LLM API", e);
            throw new RuntimeException("Не удалось вызвать LLM: " + e.getMessage());
        }
    }

    /**
     * Извлекает первый choice из ответа LLM.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractChoice(Map<String, Object> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("В ответе LLM отсутствует поле 'choices'");
        }
        return choices.get(0);
    }

    /**
     * Парсит финальный ответ LLM в ModulePlan.
     * Использует надежное извлечение подстроки между первой '{' и последней '}'.
     */
    private ModulePlan parsePlan(String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("LLM вернула пустой ответ");
        }
        try {
            // Находим первую '{' и последнюю '}'
            int firstBrace = content.indexOf('{');
            int lastBrace = content.lastIndexOf('}');

            if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
                log.error("В ответе LLM не найдены валидные JSON-скобки. Ответ:\n{}", content);
                throw new RuntimeException("Ответ LLM не содержит валидного JSON-объекта");
            }

            // Извлекаем только сам JSON, отсекая любые markdown-обертки (```json ... ```)
            String cleanJson = content.substring(firstBrace, lastBrace + 1).trim();

            return objectMapper.readValue(cleanJson, ModulePlan.class);
        } catch (Exception e) {
            log.error("Ошибка парсинга плана из ответа LLM. Сырой ответ:\n{}", content, e);
            throw new RuntimeException("Не удалось распарсить план: " + e.getMessage());
        }
    }
}