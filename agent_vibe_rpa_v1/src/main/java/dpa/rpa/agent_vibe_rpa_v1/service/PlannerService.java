package dpa.rpa.agent_vibe_rpa_v1.service;

import dpa.rpa.agent_vibe_rpa_v1.client.LlmClient;
import dpa.rpa.agent_vibe_rpa_v1.dto.ModulePlan;
import dpa.rpa.agent_vibe_rpa_v1.dto.PlannerRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;


@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerService {

    private final LlmClient llmClient;

    @Value("${planner.output.dir}")
    private String outputDir;

    public ModulePlan createPlan(PlannerRequest request) {
        log.info("🎯 Начало планирования модуля: {}", request.getModuleName());

        // Генерируем план через цикл tool calling
        // LLM сама запросит нужные активности через инструменты
        ModulePlan plan = llmClient.generatePlanWithToolCalling(
                request.getModuleName(),
                request.getRawDescription()
        );

        // Валидация
        if (plan.getModuleName() == null || plan.getSteps() == null) {
            throw new RuntimeException("LLM вернула некорректную структуру плана");
        }

        // Сохраняем в файл
        savePlanToFile(plan);

        log.info("✅ План успешно создан и сохранен: {}", request.getModuleName());
        return plan;
    }

    private void savePlanToFile(ModulePlan plan) {
        try {
            Path dirPath = Paths.get(outputDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String fileName = "plan_" + plan.getModuleName() + ".json";
            Path filePath = dirPath.resolve(fileName);

            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), plan);

            log.info("💾 План сохранен в: {}", filePath.toAbsolutePath());
        } catch (Exception e) {
            log.error("Ошибка сохранения плана в файл", e);
            throw new RuntimeException("Не удалось сохранить план", e);
        }
    }
}