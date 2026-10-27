package dpa.rpa.agent_vibe_rpa_v1.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModulePlan {
    private String moduleName;
    private List<ActivityStep> steps;
}
