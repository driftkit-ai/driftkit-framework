package ai.driftkit.workflows.examples.spring.controller;

import ai.driftkit.workflows.examples.spring.service.SimplifiedAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller demonstrating the simplified agent API.
 * Provides easy-to-use endpoints for various agent types.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/simplified-agents")
@RequiredArgsConstructor
public class SimplifiedAgentController {
    
    private final SimplifiedAgentService agentService;
    
    /**
     * Simple chat endpoint.
     */
    @PostMapping("/chat")
    public ResponseEntity<String> chat(@RequestBody ChatRequest request) {
        try {
            String response = agentService.chat(request.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in chat endpoint", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Travel planning with loop validation.
     */
    @PostMapping("/travel/plan")
    public ResponseEntity<String> planTravel(@RequestBody TravelRequest request) {
        try {
            String response = agentService.chat("Plan a travel to " + request.getDestination() + " for " + request.getDays() + " days");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in travel planning endpoint", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Sequential content creation.
     */
    @PostMapping("/content/create")
    public ResponseEntity<String> createContent(@RequestBody ContentRequest request) {
        try {
            String response = agentService.chat("Create content about: " + request.getTopic());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in content creation endpoint", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Business analysis with agent composition.
     */
    @PostMapping("/business/analyze")
    public ResponseEntity<String> analyzeBusinessIdea(@RequestBody BusinessRequest request) {
        try {
            String response = agentService.chat("Analyze this business idea: " + request.getBusinessIdea());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in business analysis endpoint", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Quality-controlled content generation.
     */
    @PostMapping("/content/quality")
    public ResponseEntity<String> generateQualityContent(@RequestBody ContentRequest request) {
        try {
            String response = agentService.chat("Generate high-quality content about: " + request.getTopic());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in quality content generation endpoint", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
    
    // Request DTOs
    public static class ChatRequest {
        private String message;
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
    
    public static class TravelRequest {
        private String destination;
        private int days;
        
        public String getDestination() { return destination; }
        public void setDestination(String destination) { this.destination = destination; }
        public int getDays() { return days; }
        public void setDays(int days) { this.days = days; }
    }
    
    public static class ContentRequest {
        private String topic;
        
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }
    
    public static class BusinessRequest {
        private String businessIdea;
        
        public String getBusinessIdea() { return businessIdea; }
        public void setBusinessIdea(String businessIdea) { this.businessIdea = businessIdea; }
    }
}