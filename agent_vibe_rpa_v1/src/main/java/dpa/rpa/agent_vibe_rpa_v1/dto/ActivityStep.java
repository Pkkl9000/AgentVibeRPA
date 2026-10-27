package dpa.rpa.agent_vibe_rpa_v1.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStep {
    private String activityId;
    private String purpose; // Краткое описание для отладки
    private Map<String, Object> params; // Параметры активности

    // Для контейнеров (например, WFIf: {"then": [...], "else": [...]})
    private Map<String, List<ActivityStep>> children;
}
