package dpa.rpa.agent_vibe_rpa_v1.controller;


import dpa.rpa.agent_vibe_rpa_v1.dto.ModulePlan;
import dpa.rpa.agent_vibe_rpa_v1.dto.PlannerRequest;
import dpa.rpa.agent_vibe_rpa_v1.service.PlannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/api/planner")
@RequiredArgsConstructor
public class PlannerController {

    private final PlannerService plannerService;

    @PostMapping("/generate")
    public ResponseEntity<?> generatePlan(@RequestBody PlannerRequest request) {
        try {
            ModulePlan plan = plannerService.createPlan(request);
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            log.error("Ошибка при генерации плана", e);
            return ResponseEntity.internalServerError().body("Ошибка: " + e.getMessage());
        }
    }
}
