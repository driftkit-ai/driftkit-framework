# Improved Fluent API Design

## Key Improvements

### 1. Direct Method References Without StepDefinition.of

Instead of:
```java
.then(StepDefinition.of(chatSteps::analyzeIntent))
```

We can have:
```java
.then(chatSteps::analyzeIntent)
```

### 2. Enhanced WorkflowBuilder API

```java
public class WorkflowBuilder<T, R> {
    
    // Direct method reference support
    public <O> WorkflowBuilder<T, R> then(Function<?, StepResult<O>> step) {
        // Auto-detect if it's a method reference and extract ID
        return this;
    }
    
    // With context support
    public <I, O> WorkflowBuilder<T, R> then(BiFunction<I, WorkflowContext, StepResult<O>> step) {
        // Auto-detect and process
        return this;
    }
    
    // Lambda with explicit ID
    public <O> WorkflowBuilder<T, R> then(String id, Function<?, StepResult<O>> step) {
        // For lambdas where we can't extract method name
        return this;
    }
    
    // Parallel execution
    public WorkflowBuilder<T, R> parallel(Function<?, StepResult<?>>... steps) {
        return parallel(Arrays.asList(steps));
    }
    
    // Branch with inline sub-workflows
    public WorkflowBuilder<T, R> branch(
            Predicate<WorkflowContext> condition,
            Consumer<WorkflowBuilder<?, ?>> ifTrue,
            Consumer<WorkflowBuilder<?, ?>> ifFalse) {
        // Create sub-builders internally
        return this;
    }
    
    // Switch-style branching for multiple conditions
    public WorkflowBuilder<T, R> switchOn(Function<Object, ?> selector) {
        return new SwitchBuilder<>(this, selector);
    }
    
    // Loop support
    public WorkflowBuilder<T, R> loop(
            Predicate<WorkflowContext> whileCondition,
            Consumer<WorkflowBuilder<?, ?>> body) {
        // Add loop construct
        return this;
    }
    
    // Try-catch error handling
    public WorkflowBuilder<T, R> tryStep(Function<?, StepResult<?>> step) {
        return new TryBuilder<>(this, step);
    }
}

// Fluent try-catch
public class TryBuilder<T, R> {
    public TryBuilder<T, R> catchError(Class<? extends Throwable> errorType, 
                                       Function<Throwable, StepResult<?>> handler) {
        return this;
    }
    
    public WorkflowBuilder<T, R> finallyDo(Runnable cleanup) {
        // Execute cleanup
        return originalBuilder;
    }
}

// Fluent when branching
public class WhenBuilder<T, R> {
    public WhenBuilder<T, R> is(Object value, Consumer<WorkflowBuilder<?, ?>> then) {
        return this;
    }
    
    public WorkflowBuilder<T, R> otherwise(Consumer<WorkflowBuilder<?, ?>> then) {
        return originalBuilder;
    }
}
```

### 3. Enhanced WorkflowContext for Step Output Access

To support clean access to step outputs in predicates and lambdas, we need to enhance WorkflowContext:

```java
public class WorkflowContext {
    // Existing fields and methods...
    
    // New fluent step output access
    public StepOutputAccessor step(String stepId) {
        return new StepOutputAccessor(stepId);
    }
    
    // Direct access to last step output
    public <T> Optional<T> lastOutput(Class<T> type) {
        // Get the most recent step output (excluding special keys)
        return stepOutputs.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("__"))
            .reduce((first, second) -> second)
            .map(e -> convertToType(e.getValue(), type));
    }
    
    // Access all outputs of a specific type
    public <T> List<T> outputs(Class<T> type) {
        return stepOutputs.values().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .collect(Collectors.toList());
    }
    
    // Inner class for fluent step access
    public class StepOutputAccessor {
        private final String stepId;
        
        StepOutputAccessor(String stepId) {
            this.stepId = stepId;
        }
        
        public <T> Optional<T> output(Class<T> type) {
            Object value = stepOutputs.get(stepId);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(convertToType(value, type));
        }
        
        public <T> T outputOrThrow(Class<T> type) {
            return output(type)
                .orElseThrow(() -> new NoSuchElementException("No output found for step: " + stepId));
        }
        
        public boolean exists() {
            return stepOutputs.containsKey(stepId);
        }
        
        public boolean succeeded() {
            Object value = stepOutputs.get(stepId);
            return value != null && !(value instanceof Throwable);
        }
    }
}
```

This allows clean syntax like:
```java
// In branch predicates
ctx -> ctx.step("checkCustomerStatus").output(CustomerStatus.class).map(CustomerStatus::getTier)

// Check if step executed
ctx -> ctx.step("inventoryCheck").exists()

// Get or throw
CustomerStatus status = ctx.step("checkCustomerStatus").outputOrThrow(CustomerStatus.class);
```

### 4. Direct Method Reference Support

The key insight is that we don't need `StepDefinition.of()` wrapper. Instead, we can make `WorkflowBuilder` accept method references directly by using overloaded `then()` methods:

```java
public class WorkflowBuilder<T, R> {
    
    // For simple functions without context
    public <I, O> WorkflowBuilder<T, R> then(Function<I, StepResult<O>> step) {
        String stepId = extractMethodName(step); // Use reflection to get method name
        addStep(stepId, step);
        return this;
    }
    
    // For functions with context
    public <I, O> WorkflowBuilder<T, R> then(BiFunction<I, WorkflowContext, StepResult<O>> step) {
        String stepId = extractMethodName(step);
        addStep(stepId, step);
        return this;
    }
    
    // For lambdas where we need explicit ID
    public <I, O> WorkflowBuilder<T, R> then(String id, Function<I, StepResult<O>> step) {
        addStep(id, step);
        return this;
    }
    
    // Parallel with varargs
    @SafeVarargs
    public final WorkflowBuilder<T, R> parallel(Function<?, StepResult<?>>... steps) {
        // Process parallel steps
        return this;
    }
}
```

## Complete Example - Complex E-commerce Order Processing

```java
@Configuration
public class EcommerceWorkflowConfig {
    
    @Bean
    public WorkflowGraph<OrderRequest, OrderConfirmation> orderProcessingWorkflow(
            OrderSteps orderSteps,
            InventorySteps inventorySteps,
            PaymentSteps paymentSteps,
            ShippingSteps shippingSteps,
            NotificationSteps notificationSteps) {
        
        return Workflow
            .define("order-processing", OrderRequest.class, OrderConfirmation.class)
            .withDescription("Complete e-commerce order processing workflow")
            
            // Step 1: Validate order request
            .then(orderSteps::validateOrder)
            
            // Step 2: Check customer status
            .then(orderSteps::checkCustomerStatus)
            
            // Step 3: Apply discounts based on customer tier
            .when(ctx -> ctx.step("checkCustomerStatus").output(CustomerStatus.class).map(CustomerStatus::getTier))
                .is(CustomerTier.GOLD, workflow -> workflow
                    .then(orderSteps::applyGoldDiscount)
                    .then(notificationSteps::notifyGoldPerks))
                .is(CustomerTier.SILVER, workflow -> workflow
                    .then(orderSteps::applySilverDiscount))
                .otherwise(workflow -> workflow
                    .then(orderSteps::applyStandardPricing))
            
            // Step 4: Check inventory for all items
            .then(inventorySteps::checkInventory)
            
            // Step 5: Branch on inventory availability
            .branch(
                ctx -> ctx.step("checkInventory").output(InventoryResult.class)
                    .map(InventoryResult::isAllAvailable).orElse(false),
                
                // If all items available - proceed with payment
                workflow -> workflow
                    .then(inventorySteps::reserveItems)
                    .parallel(
                        paymentSteps::validatePaymentMethod,
                        shippingSteps::validateShippingAddress,
                        orderSteps::calculateTaxes
                    )
                    .then(paymentSteps::processPayment)
                    .tryStep(paymentSteps::chargeCard)
                        .catchError(PaymentException.class, error -> 
                            paymentSteps.handlePaymentFailure(error, ctx))
                        .catchError(Exception.class, error -> 
                            orderSteps.cancelOrder(error, ctx))
                    .then(shippingSteps::createShippingLabel)
                    .parallel(
                        inventorySteps::updateStock,
                        orderSteps::recordTransaction,
                        notificationSteps::sendOrderConfirmation,
                        shippingSteps::schedulePickup
                    )
                    .then(orderSteps::finalizeOrder),
                
                // If items unavailable - handle backorder
                workflow -> workflow
                    .then(inventorySteps::checkBackorderEligibility)
                    .branch(
                        ctx -> ctx.lastOutput(BackorderEligibility.class)
                            .map(BackorderEligibility::isEligible).orElse(false),
                        
                        // Can backorder
                        backorderFlow -> backorderFlow
                            .then("ask-backorder-consent", (InventoryResult result) -> 
                                StepResult.suspend(
                                    createBackorderPrompt(result), 
                                    BackorderConsent.class
                                ))
                            .then(orderSteps::processBackorderConsent)
                            .when(ctx -> ctx.lastOutput(BackorderDecision.class))
                                .is(BackorderDecision.ACCEPT, flow -> flow
                                    .then(inventorySteps::createBackorder)
                                    .then(notificationSteps::sendBackorderConfirmation))
                                .is(BackorderDecision.PARTIAL, flow -> flow
                                    .then(inventorySteps::splitOrder)
                                    .then(orderSteps::processPartialOrder))
                                .is(BackorderDecision.CANCEL, flow -> flow
                                    .then(orderSteps::cancelOrder)),
                        
                        // Cannot backorder
                        cancelFlow -> cancelFlow
                            .then(orderSteps::notifyOutOfStock)
                            .then(orderSteps::suggestAlternatives)
                            .then("wait-alternative-choice", ctx -> 
                                StepResult.suspend(
                                    ctx.lastOutput(AlternativeSuggestions.class).orElseThrow(),
                                    AlternativeChoice.class
                                ))
                            .then(orderSteps::processAlternativeChoice)
                    )
            )
            
            // Step 6: Post-order processing loop (check for add-ons)
            .loop(
                ctx -> ctx.step("finalizeOrder").exists() && 
                       ctx.lastOutput(OrderSummary.class)
                          .map(OrderSummary::hasRecommendedAddons).orElse(false),
                          
                loopBody -> loopBody
                    .then("offer-addon", ctx -> {
                        OrderSummary summary = ctx.lastOutput(OrderSummary.class).orElseThrow();
                        AddOn nextAddon = summary.getNextAddon();
                        return StepResult.suspend(
                            createAddonOffer(nextAddon),
                            AddonDecision.class
                        );
                    })
                    .then(orderSteps::processAddonDecision)
                    .branch(
                        ctx -> ctx.lastOutput(AddonDecision.class)
                            .map(AddonDecision::isAccepted).orElse(false),
                        
                        accepted -> accepted
                            .then(orderSteps::addAddonToOrder)
                            .then(paymentSteps::chargeAddon)
                            .then(notificationSteps::sendAddonConfirmation),
                            
                        declined -> declined
                            .then(orderSteps::recordAddonDecline)
                    )
            )
            
            // Step 7: Final confirmation
            .then(orderSteps::generateFinalConfirmation)
            
            .build();
    }
    
    @Bean
    public WorkflowGraph<ChatRequest, ChatResponse> customerServiceWorkflow(
            ChatSteps chatSteps,
            IntentSteps intentSteps,
            KnowledgeSteps knowledgeSteps,
            TicketSteps ticketSteps,
            SentimentSteps sentimentSteps) {
        
        return Workflow
            .define("customer-service-chat", ChatRequest.class, ChatResponse.class)
            
            // Initial processing chain
            .then(chatSteps::extractMessage)
            .then(intentSteps::analyzeIntent)
            .then(sentimentSteps::analyzeSentiment)
            
            // Route based on sentiment first
            .branch(
                ctx -> ctx.lastOutput(SentimentScore.class)
                    .map(score -> score.getScore() < -0.5).orElse(false),
                    
                // Angry customer - priority handling
                angryFlow -> angryFlow
                    .then(chatSteps::acknowledgeUpset)
                    .then(ticketSteps::escalateToSupport)
                    .parallel(
                        notificationSteps::alertSupervisor,
                        chatSteps::offerImmediateCallback,
                        ticketSteps::flagHighPriority
                    )
                    .then("wait-escalation-response", ctx ->
                        StepResult.suspend(
                            "I've escalated your concern to our senior support team. " +
                            "Would you like an immediate callback?",
                            CallbackChoice.class
                        ))
                    .then(chatSteps::processCallbackChoice),
                    
                // Normal sentiment - continue with intent routing
                normalFlow -> normalFlow
                    .when(ctx -> ctx.step("analyzeIntent").output(Intent.class))
                        .is(Intent.ORDER_STATUS, flow -> flow
                            .then(chatSteps::askOrderNumber)
                            .then(orderSteps::lookupOrder)
                            .then(chatSteps::formatOrderStatus))
                            
                        .is(Intent.TECHNICAL_SUPPORT, flow -> flow
                            .then(knowledgeSteps::searchKnowledgeBase)
                            .branch(
                                ctx -> ctx.lastOutput(KBSearchResult.class)
                                    .map(r -> r.getConfidence() > 0.8).orElse(false),
                                    
                                foundSolution -> foundSolution
                                    .then(chatSteps::presentSolution)
                                    .then("check-solved", ctx ->
                                        StepResult.suspend(
                                            "Did this solve your issue?",
                                            SolutionFeedback.class
                                        ))
                                    .then(chatSteps::processFeedback),
                                    
                                noSolution -> noSolution
                                    .then(ticketSteps::createSupportTicket)
                                    .then(chatSteps::confirmTicketCreation)
                            ))
                            
                        .is(Intent.BILLING, flow -> flow
                            .then(chatSteps::verifyAccountOwnership)
                            .then(billingSteps::retrieveBillingInfo)
                            .then(chatSteps::presentBillingOptions))
                            
                        .is(Intent.PRODUCT_QUESTION, flow -> flow
                            .then(productSteps::identifyProduct)
                            .parallel(
                                knowledgeSteps::searchProductDocs,
                                knowledgeSteps::searchUserForums,
                                productSteps::checkInventory
                            )
                            .then(chatSteps::compiledProductInfo))
                            
                        .otherwise(flow -> flow
                            .then(chatSteps::askForClarification)
                            .then("wait-clarification", ctx ->
                                StepResult.suspend(
                                    "I'm not sure I understand. Could you please provide more details?",
                                    UserClarification.class
                                ))
                            .then(intentSteps::reanalyzeWithContext)
                            .then(chatSteps::routeToAppropriateFlow))
            )
            
            // Always end with satisfaction check
            .then(chatSteps::askSatisfaction)
            .then("final-feedback", ctx ->
                StepResult.suspend(
                    "Is there anything else I can help you with today?",
                    ContinueChoice.class
                ))
            .branch(
                ctx -> ctx.lastOutput(ContinueChoice.class)
                    .map(ContinueChoice::wantsToContinue).orElse(false),
                    
                continueFlow -> continueFlow
                    .then(chatSteps::resetContext)
                    .then(chatSteps::startNewConversation),
                    
                endFlow -> endFlow
                    .then(chatSteps::summarizeConversation)
                    .then(ticketSteps::saveTranscript)
                    .then(chatSteps::sayGoodbye)
            )
            
            .build();
    }
}
```

## Step Components Implementation

```java
@Component
public class OrderSteps {
    
    @Autowired
    private OrderValidator validator;
    
    @Autowired
    private CustomerService customerService;
    
    @Autowired
    private PricingEngine pricingEngine;
    
    public StepResult<ValidatedOrder> validateOrder(OrderRequest request) {
        try {
            ValidatedOrder validated = validator.validate(request);
            return new StepResult.Continue<>(validated);
        } catch (ValidationException e) {
            return new StepResult.Fail<>(e);
        }
    }
    
    public StepResult<CustomerStatus> checkCustomerStatus(ValidatedOrder order, WorkflowContext context) {
        CustomerStatus status = customerService.getStatus(order.getCustomerId());
        context.setCustomData("customerTier", status.getTier());
        return new StepResult.Continue<>(status);
    }
    
    public StepResult<PricedOrder> applyGoldDiscount(ValidatedOrder order, WorkflowContext context) {
        PricedOrder priced = pricingEngine.applyDiscount(order, 0.20); // 20% for gold
        return new StepResult.Continue<>(priced);
    }
    
    public StepResult<PricedOrder> applySilverDiscount(ValidatedOrder order, WorkflowContext context) {
        PricedOrder priced = pricingEngine.applyDiscount(order, 0.10); // 10% for silver
        return new StepResult.Continue<>(priced);
    }
    
    public StepResult<PricedOrder> applyStandardPricing(ValidatedOrder order, WorkflowContext context) {
        PricedOrder priced = pricingEngine.calculateStandard(order);
        return new StepResult.Continue<>(priced);
    }
    
    public StepResult<TaxCalculation> calculateTaxes(PricedOrder order, WorkflowContext context) {
        // Access shipping address from parallel step if available
        Optional<ShippingAddress> address = context.step("validateShippingAddress")
            .output(ShippingAddress.class);
            
        TaxCalculation taxes = address
            .map(addr -> taxService.calculate(order, addr))
            .orElse(taxService.estimateTaxes(order));
            
        return new StepResult.Continue<>(taxes);
    }
    
    public StepResult<OrderCancellation> cancelOrder(Throwable error, WorkflowContext context) {
        String orderId = context.getInstanceId();
        OrderCancellation cancellation = orderService.cancel(orderId, error.getMessage());
        return new StepResult.Finish<>(cancellation);
    }
    
    // More methods...
}

@Component
public class PaymentSteps {
    
    @Autowired
    private PaymentGateway paymentGateway;
    
    @Autowired
    private FraudDetection fraudDetection;
    
    public StepResult<PaymentValidation> validatePaymentMethod(PricedOrder order, WorkflowContext context) {
        PaymentMethod method = order.getPaymentMethod();
        
        // Run fraud check
        FraudScore score = fraudDetection.check(method, order);
        if (score.isHighRisk()) {
            return StepResult.suspend(
                new FraudAlert(score, "High risk transaction detected. Manual review required."),
                FraudReviewDecision.class
            );
        }
        
        PaymentValidation validation = paymentGateway.validate(method);
        return new StepResult.Continue<>(validation);
    }
    
    public StepResult<PaymentAuth> processPayment(PricedOrder order, WorkflowContext context) {
        // Get tax calculation from parallel step
        TaxCalculation taxes = context.step("calculateTaxes")
            .outputOrThrow(TaxCalculation.class);
            
        BigDecimal total = order.getSubtotal()
            .add(taxes.getTotalTax())
            .add(order.getShipping());
            
        PaymentAuth auth = paymentGateway.authorize(order.getPaymentMethod(), total);
        return new StepResult.Continue<>(auth);
    }
    
    @Async
    public StepResult<ChargeResult> chargeCard(PaymentAuth auth, WorkflowContext context) {
        // This is an async operation
        CompletableFuture<ChargeResult> chargeFuture = paymentGateway.chargeAsync(auth);
        
        return new StepResult.Async<>(
            "charge-" + auth.getAuthCode(),
            30000L, // 30 second timeout
            Map.of("future", chargeFuture, "auth", auth),
            new ChargeInProgress(auth.getAuthCode(), "Processing payment...")
        );
    }
    
    @AsyncStep("charge-*")
    public StepResult<ChargeResult> completeCharge(Map<String, Object> taskArgs, 
                                                   WorkflowContext context, 
                                                   AsyncProgressReporter progress) {
        CompletableFuture<ChargeResult> future = (CompletableFuture<ChargeResult>) taskArgs.get("future");
        PaymentAuth auth = (PaymentAuth) taskArgs.get("auth");
        
        try {
            progress.updateProgress(50, "Contacting payment processor...");
            ChargeResult result = future.get(25, TimeUnit.SECONDS);
            progress.updateProgress(100, "Payment completed");
            
            return new StepResult.Continue<>(result);
        } catch (TimeoutException e) {
            return new StepResult.Fail<>(new PaymentException("Payment timeout", e));
        } catch (Exception e) {
            return new StepResult.Fail<>(new PaymentException("Payment failed", e));
        }
    }
    
    public StepResult<PaymentRecovery> handlePaymentFailure(Throwable error, WorkflowContext context) {
        if (error instanceof PaymentException) {
            PaymentException pe = (PaymentException) error;
            
            // Try alternative payment method
            return StepResult.suspend(
                new PaymentFailurePrompt(
                    pe.getReason(),
                    "Would you like to try a different payment method?"
                ),
                AlternativePaymentChoice.class
            );
        }
        
        return new StepResult.Fail<>(error);
    }
}
```

## Additional Complex Example - Multi-Stage Document Processing Pipeline

This example shows a sophisticated document processing workflow with multiple stages, parallel processing, and complex branching:

```java
@Configuration
public class DocumentProcessingConfig {
    
    @Bean
    public WorkflowGraph<DocumentRequest, ProcessingReport> documentPipeline(
            DocumentSteps docSteps,
            OcrSteps ocrSteps,
            NlpSteps nlpSteps,
            TranslationSteps translationSteps,
            QualitySteps qualitySteps,
            StorageSteps storageSteps,
            NotificationSteps notifySteps) {
        
        return Workflow
            .define("document-processing-pipeline", DocumentRequest.class, ProcessingReport.class)
            .withDescription("Multi-stage document processing with AI enrichment")
            
            // Stage 1: Initial validation and classification
            .then(docSteps::validateDocument)
            .then(docSteps::detectDocumentType)
            .then(docSteps::extractMetadata)
            
            // Stage 2: Format-specific preprocessing
            .when(ctx -> ctx.step("detectDocumentType").output(DocumentType.class))
                .is(DocumentType.PDF, flow -> flow
                    .then(docSteps::extractPdfText)
                    .then(docSteps::extractPdfImages)
                    .parallel(
                        docSteps::analyzePdfStructure,
                        docSteps::extractPdfTables,
                        docSteps::extractPdfForms
                    ))
                .is(DocumentType.IMAGE, flow -> flow
                    .then(ocrSteps::preprocessImage)
                    .then(ocrSteps::detectTextRegions)
                    .then(ocrSteps::performOcr)
                    .tryStep(ocrSteps::enhanceOcrQuality)
                        .catchError(OcrException.class, e -> ocrSteps.fallbackOcr(e)))
                .is(DocumentType.SCANNED_PDF, flow -> flow
                    .then(docSteps::splitPdfPages)
                    .then(ocrSteps::batchOcrPages)
                    .then(docSteps::reconstructTextLayout))
                .otherwise(flow -> flow
                    .then(docSteps::handleUnknownFormat))
            
            // Stage 3: Content enrichment and analysis
            .then(nlpSteps::detectLanguage)
            .branch(
                ctx -> ctx.step("detectLanguage").output(Language.class)
                    .map(lang -> !lang.equals(Language.ENGLISH)).orElse(false),
                    
                // Non-English: translate first
                translateFlow -> translateFlow
                    .then(translationSteps::translateToEnglish)
                    .then(qualitySteps::validateTranslation)
                    .branch(
                        ctx -> ctx.step("validateTranslation").output(ValidationResult.class)
                            .map(ValidationResult::isPoor).orElse(false),
                        poorQuality -> poorQuality
                            .then("request-human-review", ctx -> 
                                StepResult.suspend(
                                    "Translation quality is poor. Human review required.",
                                    HumanReviewDecision.class
                                ))
                            .then(translationSteps::applyHumanCorrections),
                        goodQuality -> goodQuality
                            .then(nlpSteps::preserveOriginalWithTranslation)
                    ),
                    
                // English: proceed directly
                englishFlow -> englishFlow
                    .then(nlpSteps::tokenizeText)
            )
            
            // Stage 4: Deep NLP analysis (parallel processing)
            .parallel(
                nlpSteps::extractEntities,
                nlpSteps::analyzeSentiment,
                nlpSteps::extractKeyPhrases,
                nlpSteps::classifyTopics,
                nlpSteps::detectPii,
                nlpSteps::summarizeContent
            )
            
            // Stage 5: Quality checks and compliance
            .then(qualitySteps::aggregateQualityMetrics)
            .then(qualitySteps::checkCompliance)
            .branch(
                ctx -> ctx.step("checkCompliance").output(ComplianceResult.class)
                    .map(ComplianceResult::hasViolations).orElse(false),
                    
                violationsFound -> violationsFound
                    .then(qualitySteps::categorizeViolations)
                    .when(ctx -> ctx.step("categorizeViolations").output(ViolationSeverity.class))
                        .is(ViolationSeverity.CRITICAL, flow -> flow
                            .then(docSteps::quarantineDocument)
                            .then(notifySteps::alertSecurityTeam)
                            .then(docSteps::generateComplianceReport)
                            .then("await-security-decision", ctx ->
                                StepResult.suspend(
                                    "Critical compliance violation. Awaiting security team decision.",
                                    SecurityDecision.class
                                )))
                        .is(ViolationSeverity.MAJOR, flow -> flow
                            .then(qualitySteps::attemptAutoRemediation)
                            .then(qualitySteps::rerunCompliance))
                        .is(ViolationSeverity.MINOR, flow -> flow
                            .then(qualitySteps::logViolation)
                            .then(qualitySteps::continueWithWarning)),
                            
                noViolations -> noViolations
                    .then(qualitySteps::certifyCompliant)
            )
            
            // Stage 6: Storage and indexing
            .then(storageSteps::determineStorageStrategy)
            .parallel(
                storageSteps::storeOriginalDocument,
                storageSteps::storeProcessedContent,
                storageSteps::updateSearchIndex,
                storageSteps::updateMetadataStore
            )
            
            // Stage 7: Post-processing tasks
            .then(docSteps::generateProcessingReport)
            .then(notifySteps::notifyStakeholders)
            
            // Stage 8: Optional enrichment loop
            .loop(
                ctx -> ctx.step("generateProcessingReport").output(ProcessingReport.class)
                    .map(report -> report.getSuggestedEnrichments().size() > 0).orElse(false),
                    
                enrichmentLoop -> enrichmentLoop
                    .then("select-enrichment", ctx -> {
                        ProcessingReport report = ctx.step("generateProcessingReport")
                            .outputOrThrow(ProcessingReport.class);
                        Enrichment next = report.getNextEnrichment();
                        return StepResult.suspend(
                            "Would you like to apply enrichment: " + next.getDescription() + "?",
                            EnrichmentDecision.class
                        );
                    })
                    .branch(
                        ctx -> ctx.lastOutput(EnrichmentDecision.class)
                            .map(EnrichmentDecision::isApproved).orElse(false),
                            
                        approved -> approved
                            .when(ctx -> ctx.lastOutput(EnrichmentDecision.class)
                                .map(EnrichmentDecision::getEnrichmentType))
                                .is(EnrichmentType.SEMANTIC_TAGGING, flow -> flow
                                    .then(nlpSteps::applySemanticTags)
                                    .then(storageSteps::updateTags))
                                .is(EnrichmentType.CROSS_REFERENCE, flow -> flow
                                    .then(docSteps::findRelatedDocuments)
                                    .then(docSteps::createCrossReferences))
                                .is(EnrichmentType.EXTERNAL_DATA, flow -> flow
                                    .then(docSteps::fetchExternalData)
                                    .then(docSteps::mergeExternalData)),
                                    
                        rejected -> rejected
                            .then(docSteps::markEnrichmentSkipped)
                    )
                    .then(docSteps::updateProcessingReport)
            )
            
            // Final stage: Cleanup and finalization
            .then(docSteps::cleanupTempFiles)
            .then(docSteps::finalizeReport)
            
            .build();
    }
}
```

## Long Chain Example - Customer Onboarding with KYC

```java
@Bean
public WorkflowGraph<OnboardingRequest, CustomerAccount> kycOnboardingWorkflow(
        KycSteps kycSteps,
        IdentitySteps identitySteps,
        RiskSteps riskSteps,
        AccountSteps accountSteps,
        CommunicationSteps commSteps) {
    
    return Workflow
        .define("kyc-onboarding", OnboardingRequest.class, CustomerAccount.class)
        
        // Step 1-5: Initial data collection
        .then(kycSteps::validateInitialData)
        .then(identitySteps::parseIdentityDocument)
        .then(identitySteps::extractBiometricData)
        .then(identitySteps::performLivenessCheck)
        .then(identitySteps::compareBiometrics)
        
        // Step 6-10: Identity verification
        .then(kycSteps::checkSanctionsList)
        .then(kycSteps::checkPepDatabase)
        .then(kycSteps::verifyAddress)
        .then(kycSteps::validatePhoneNumber)
        .then(kycSteps::sendOtpVerification)
        
        // Step 11-15: Risk assessment
        .then("await-otp", ctx -> 
            StepResult.suspend("Enter OTP sent to your phone", OtpInput.class))
        .then(kycSteps::verifyOtp)
        .then(riskSteps::calculateRiskScore)
        .then(riskSteps::checkCreditBureau)
        .then(riskSteps::analyzeFinancialHistory)
        
        // Step 16-20: Enhanced due diligence (if needed)
        .branch(
            ctx -> ctx.step("calculateRiskScore").output(RiskScore.class)
                .map(score -> score.getValue() > 0.7).orElse(false),
                
            highRisk -> highRisk
                .then(kycSteps::requestAdditionalDocuments)
                .then("await-documents", ctx ->
                    StepResult.suspend("Please upload additional documents", DocumentUpload.class))
                .then(kycSteps::verifyAdditionalDocuments)
                .then(riskSteps::performEnhancedDueDiligence)
                .then(riskSteps::manualRiskReview),
                
            normalRisk -> normalRisk
                .then(riskSteps::automatedApproval)
        )
        
        // Step 21-25: Account creation
        .then(accountSteps::generateAccountNumber)
        .then(accountSteps::setupAccountStructure)
        .then(accountSteps::configureAccountLimits)
        .then(accountSteps::createDigitalCard)
        .then(accountSteps::setupOnlineBanking)
        
        // Step 26-30: Final setup
        .then(commSteps::sendWelcomePackage)
        .then(accountSteps::scheduleDebitCard)
        .then(accountSteps::enrollInRewards)
        .then(commSteps::scheduleOnboardingCall)
        .then(accountSteps::activateAccount)
        
        .build();
}
```

## Key API Improvements Summary

1. **No StepDefinition.of wrapper** - Direct method references
2. **Richer branching** - `when/is/otherwise` pattern
3. **Loop support** - For iterative workflows  
4. **Try-catch blocks** - Built-in error handling
5. **Better context API** - Fluent step output access via `ctx.step("stepId")`
6. **Parallel varargs** - Simpler parallel syntax
7. **Inline sub-workflows** - Using Consumer<WorkflowBuilder>
8. **Type inference** - Let Java infer types where possible
9. **Long chains** - Support for 20+ sequential steps
10. **Complex nesting** - Multiple levels of branching and loops

This design makes the API much more intuitive and reduces boilerplate while maintaining type safety and expressiveness. The examples show how complex, real-world workflows can be expressed clearly and concisely.