package dpa.rpa.agent_vibe_rpa_v1.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlannerRequest {
    private String moduleName;
    private String rawDescription; // Текст из чата с разработчиком
}
