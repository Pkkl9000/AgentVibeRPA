package dpa.rpa.agent_vibe_rpa_v1.client.tool;

import java.util.List;
import java.util.Map;

public class ToolDefinitions {

    /**
     * Возвращает список инструментов, доступных LLM в формате OpenAI function calling.
     */
    public static List<Map<String, Object>> getTools() {
        return List.of(
                // Инструмент 1: list_activities
                Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "list_activities",
                                "description", "Получить тонкий список активностей для указанного домена. Возвращает id, name, short, is_container для каждой активности. Используй этот инструмент, чтобы узнать, какие активности доступны в нужном домене.",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "domain", Map.of(
                                                        "type", "string",
                                                        "description", "Домен активностей. Доступные значения: workflow, excel, mail, system, data, browser."
                                                )
                                        ),
                                        "required", List.of("domain")
                                )
                        )
                ),
                // Инструмент 2: get_activity_card
                Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "get_activity_card",
                                "description", "Получить полную карточку активности по её id. Включает inputParams, outputParams, xmlSkeleton, notes. Вызывай этот инструмент, когда нужно уточнить параметры конкретной активности или её XML-скелет.",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "activity_id", Map.of(
                                                        "type", "string",
                                                        "description", "ID активности из каталога (например, WFAttachExcel, WFIf, WFAddToLog)."
                                                )
                                        ),
                                        "required", List.of("activity_id")
                                )
                        )
                )
        );
    }
}