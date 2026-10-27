package dpa.rpa.agent_vibe_rpa_v1.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import java.net.URI;


@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogClient {

    @Value("${catalog.service.url}")
    private String catalogUrl;

    private final RestTemplate restTemplate;

    /**
     * Получает тонкий каталог активностей для указанного домена.
     */
    public List<Map<String, Object>> getThinActivitiesByDomain(String domain) {
        log.info("Запрос тонкого каталога для домена: {}", domain);

        String url = UriComponentsBuilder.fromUriString(catalogUrl)
                .path("/activities")
                .queryParam("domain", domain)
                .encode()
                .toUriString();

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    URI.create(url),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            List<Map<String, Object>> result = response.getBody() != null ? response.getBody() : List.of();
            log.info("Получено {} активностей из домена {}", result.size(), domain);
            return result;
        } catch (Exception e) {
            log.warn("Не удалось получить домен {}: {}", domain, e.getMessage());
            return List.of();
        }
    }

    /**
     * Получает полную карточку активности по её id.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getActivityCard(String activityId) {
        log.info("Запрос полной карточки активности: {}", activityId);

        // Исправление: используем buildAndExpand вместо build
        // buildAndExpand возвращает UriComponents, у которого есть toUriString()
        String url = UriComponentsBuilder.fromUriString(catalogUrl)
                .path("/activities/{id}")
                .buildAndExpand(activityId)
                .toUriString();

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    URI.create(url),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            log.warn("Не удалось получить карточку {}: {}", activityId, e.getMessage());
            return Map.of("error", "Активность не найдена: " + activityId);
        }
    }

    /**
     * Получает тонкий каталог для списка доменов (объединяет результаты).
     */
    public List<Map<String, Object>> getThinCatalogForDomains(List<String> domains) {
        List<Map<String, Object>> allActivities = new ArrayList<>();
        for (String domain : domains) {
            allActivities.addAll(getThinActivitiesByDomain(domain));
        }
        return allActivities;
    }
}