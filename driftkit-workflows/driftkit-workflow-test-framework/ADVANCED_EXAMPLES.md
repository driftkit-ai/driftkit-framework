# DriftKit Workflow Test Framework - Advanced Examples

This document provides advanced examples and patterns for testing complex workflow scenarios using the DriftKit Workflow Test Framework.

## Table of Contents

1. [Stateful Workflow Testing](#stateful-workflow-testing)
2. [Multi-Stage Pipeline Testing](#multi-stage-pipeline-testing)
3. [Event-Driven Workflow Testing](#event-driven-workflow-testing)
4. [Saga Pattern Testing](#saga-pattern-testing)
5. [Dynamic Workflow Testing](#dynamic-workflow-testing)
6. [Integration Test Patterns](#integration-test-patterns)

## Stateful Workflow Testing

### Testing Workflows with Complex State Management

```java
public class StatefulWorkflowTest extends WorkflowTestBase {
    
    @Test
    void testShoppingCartWorkflow() throws Exception {
        // Setup stateful workflow
        WorkflowBuilder<CartCommand, CartState> builder = WorkflowBuilder
            .define("cart-workflow", CartCommand.class, CartState.class)
            .then("process-command", (cmd, ctx) -> {
                CartState state = (CartState) ctx.get("cart-state", new CartState());
                
                switch (cmd.type()) {
                    case ADD_ITEM -> {
                        state.addItem(cmd.item());
                        ctx.set("cart-state", state);
                        return StepResult.continueWith(state);
                    }
                    case REMOVE_ITEM -> {
                        state.removeItem(cmd.itemId());
                        ctx.set("cart-state", state);
                        return StepResult.continueWith(state);
                    }
                    case CHECKOUT -> {
                        if (state.isEmpty()) {
                            return StepResult.fail("Cannot checkout empty cart");
                        }
                        return StepResult.continueWith(state);
                    }
                    default -> {
                        return StepResult.fail("Unknown command");
                    }
                }
            })
            .branch(
                ctx -> {
                    CartCommand cmd = (CartCommand) ctx.getTriggerData();
                    return cmd.type() == CommandType.CHECKOUT;
                },
                checkout -> checkout
                    .then("calculate-total", (state, ctx) -> {
                        CartState cart = (CartState) state;
                        double total = cart.calculateTotal();
                        return StepResult.continueWith(new CheckoutData(cart, total));
                    })
                    .then("process-payment", (data, ctx) -> {
                        // Payment processing
                        return StepResult.continueWith(new PaymentResult(true, "PAY-123"));
                    })
                    .then("complete-order", (payment, ctx) -> {
                        CartState cart = (CartState) ctx.get("cart-state");
                        return StepResult.finish(new OrderComplete(cart.getItemCount(), "ORD-123"));
                    }),
                continuation -> continuation
                    .then("update-inventory", (state, ctx) -> {
                        // Update inventory for add/remove
                        return StepResult.finish(state);
                    })
            );
        
        engine.register(builder);
        
        // Test sequence of operations
        // Add items
        CartState state1 = executeWorkflow("cart-workflow", 
            new CartCommand(CommandType.ADD_ITEM, new Item("PROD-1", 29.99)));
        assertEquals(1, state1.getItemCount());
        
        // Add more items
        CartState state2 = executeWorkflow("cart-workflow", 
            new CartCommand(CommandType.ADD_ITEM, new Item("PROD-2", 49.99)));
        assertEquals(2, state2.getItemCount());
        
        // Checkout
        OrderComplete order = executeWorkflow("cart-workflow", 
            new CartCommand(CommandType.CHECKOUT, null));
        assertNotNull(order);
        assertEquals(2, order.itemCount());
        
        // Verify execution paths
        assertions.assertStep("cart-workflow", "calculate-total").wasExecuted();
        assertions.assertStep("cart-workflow", "process-payment").wasExecuted();
        assertions.assertStep("cart-workflow", "complete-order").wasExecuted();
    }
    
    // Domain classes
    record CartCommand(CommandType type, Item item, String itemId) {
        CartCommand(CommandType type, Item item) {
            this(type, item, null);
        }
    }
    
    enum CommandType { ADD_ITEM, REMOVE_ITEM, CHECKOUT }
    
    static class CartState {
        private final List<Item> items = new ArrayList<>();
        
        void addItem(Item item) { items.add(item); }
        void removeItem(String itemId) { 
            items.removeIf(item -> item.id().equals(itemId)); 
        }
        int getItemCount() { return items.size(); }
        boolean isEmpty() { return items.isEmpty(); }
        double calculateTotal() {
            return items.stream().mapToDouble(Item::price).sum();
        }
    }
    
    record Item(String id, double price) {}
    record CheckoutData(CartState cart, double total) {}
    record PaymentResult(boolean success, String transactionId) {}
    record OrderComplete(int itemCount, String orderId) {}
}
```

## Multi-Stage Pipeline Testing

### Testing Complex Data Processing Pipelines

```java
public class DataPipelineWorkflowTest extends WorkflowTestBase {
    
    @Test
    void testETLPipeline() throws Exception {
        // Setup ETL pipeline workflow
        WorkflowBuilder<DataSource, ProcessedData> builder = WorkflowBuilder
            .define("etl-pipeline", DataSource.class, ProcessedData.class)
            
            // Extract stage
            .then("validate-source", (source, ctx) -> {
                if (!source.isValid()) {
                    return StepResult.fail("Invalid data source");
                }
                return StepResult.continueWith(source);
            })
            .then("extract-data", (source, ctx) -> {
                // Mock external data extraction
                return StepResult.continueWith(new RawData(source.id(), generateTestData()));
            })
            
            // Transform stage
            .then("clean-data", (raw, ctx) -> {
                List<Record> cleaned = raw.records().stream()
                    .filter(r -> r.isValid())
                    .collect(Collectors.toList());
                return StepResult.continueWith(new CleanedData(raw.sourceId(), cleaned));
            })
            .then("enrich-data", (cleaned, ctx) -> {
                List<EnrichedRecord> enriched = cleaned.records().stream()
                    .map(r -> enrichRecord(r))
                    .collect(Collectors.toList());
                return StepResult.continueWith(new EnrichedData(cleaned.sourceId(), enriched));
            })
            .then("aggregate-data", (enriched, ctx) -> {
                Map<String, AggregateMetrics> aggregates = enriched.records().stream()
                    .collect(Collectors.groupingBy(
                        EnrichedRecord::category,
                        Collectors.collectingAndThen(
                            Collectors.toList(),
                            records -> calculateMetrics(records)
                        )
                    ));
                return StepResult.continueWith(new AggregatedData(enriched.sourceId(), aggregates));
            })
            
            // Load stage
            .then("validate-output", (aggregated, ctx) -> {
                if (aggregated.metrics().isEmpty()) {
                    return StepResult.fail("No data to load");
                }
                return StepResult.continueWith(aggregated);
            })
            .then("load-to-warehouse", (aggregated, ctx) -> {
                // Mock data warehouse loading
                String loadId = "LOAD-" + System.currentTimeMillis();
                return StepResult.finish(new ProcessedData(
                    aggregated.sourceId(), 
                    loadId, 
                    aggregated.metrics().size()
                ));
            });
        
        engine.register(builder);
        
        // Mock external services
        orchestrator.mock()
            .workflow("etl-pipeline")
            .step("extract-data")
            .always()
            .thenReturn(DataSource.class, source -> {
                // Simulate different data volumes
                int recordCount = source.id().contains("LARGE") ? 10000 : 100;
                List<Record> records = IntStream.range(0, recordCount)
                    .mapToObj(i -> new Record("REC-" + i, "Category-" + (i % 5), i * 1.5))
                    .collect(Collectors.toList());
                return StepResult.continueWith(new RawData(source.id(), records));
            });
        
        // Test small dataset
        ProcessedData result = executeWorkflow("etl-pipeline", 
            new DataSource("SMALL-001", true));
        assertEquals(5, result.aggregateCount()); // 5 categories
        
        // Test large dataset with performance assertion
        long startTime = System.currentTimeMillis();
        ProcessedData largeResult = executeWorkflow("etl-pipeline", 
            new DataSource("LARGE-001", true));
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(duration < 5000, "Large dataset processing took too long: " + duration + "ms");
        assertEquals(5, largeResult.aggregateCount());
        
        // Verify all stages executed
        assertions.assertExecutionOrder()
            .step("validate-source")
            .step("extract-data")
            .step("clean-data")
            .step("enrich-data")
            .step("aggregate-data")
            .step("validate-output")
            .step("load-to-warehouse");
    }
    
    // Helper methods
    private List<Record> generateTestData() {
        return IntStream.range(0, 100)
            .mapToObj(i -> new Record("REC-" + i, "Cat-" + (i % 5), i * 1.5))
            .collect(Collectors.toList());
    }
    
    private EnrichedRecord enrichRecord(Record record) {
        return new EnrichedRecord(
            record.id(),
            record.category(),
            record.value(),
            record.value() * 1.1, // Add 10% enrichment
            System.currentTimeMillis()
        );
    }
    
    private AggregateMetrics calculateMetrics(List<EnrichedRecord> records) {
        double sum = records.stream().mapToDouble(EnrichedRecord::enrichedValue).sum();
        double avg = sum / records.size();
        return new AggregateMetrics(records.size(), sum, avg);
    }
    
    // Domain classes
    record DataSource(String id, boolean isValid) {}
    record Record(String id, String category, double value) {
        boolean isValid() { return value >= 0; }
    }
    record RawData(String sourceId, List<Record> records) {}
    record CleanedData(String sourceId, List<Record> records) {}
    record EnrichedRecord(String id, String category, double value, double enrichedValue, long timestamp) {}
    record EnrichedData(String sourceId, List<EnrichedRecord> records) {}
    record AggregateMetrics(int count, double sum, double average) {}
    record AggregatedData(String sourceId, Map<String, AggregateMetrics> metrics) {}
    record ProcessedData(String sourceId, String loadId, int aggregateCount) {}
}
```

## Event-Driven Workflow Testing

### Testing Workflows with Event Emissions and Reactions

```java
public class EventDrivenWorkflowTest extends WorkflowTestBase {
    
    private final List<WorkflowEvent> capturedEvents = new ArrayList<>();
    
    @BeforeEach
    void setUp() {
        // Setup event listener
        engine.addEventListener(event -> capturedEvents.add(event));
    }
    
    @Test
    void testOrderFulfillmentWithEvents() throws Exception {
        // Create event-driven order fulfillment workflow
        WorkflowBuilder<Order, FulfillmentResult> builder = WorkflowBuilder
            .define("fulfillment-workflow", Order.class, FulfillmentResult.class)
            .then("reserve-inventory", (order, ctx) -> {
                ctx.emit(new Event("INVENTORY_CHECK", order.orderId()));
                
                boolean available = checkInventory(order);
                if (!available) {
                    ctx.emit(new Event("INVENTORY_SHORTAGE", order.orderId()));
                    return StepResult.fail("Insufficient inventory");
                }
                
                ctx.emit(new Event("INVENTORY_RESERVED", order.orderId()));
                return StepResult.continueWith(new ReservedOrder(order, "RES-123"));
            })
            .then("allocate-shipping", (reserved, ctx) -> {
                ctx.emit(new Event("SHIPPING_ALLOCATION_START", reserved.order().orderId()));
                
                ShippingMethod method = selectShippingMethod(reserved.order());
                ctx.emit(new Event("SHIPPING_METHOD_SELECTED", method.name()));
                
                return StepResult.continueWith(new AllocatedOrder(reserved, method));
            })
            .then("generate-labels", (allocated, ctx) -> {
                ctx.emit(new Event("LABEL_GENERATION_START", allocated.reserved().order().orderId()));
                
                String trackingNumber = "TRACK-" + System.currentTimeMillis();
                ctx.emit(new Event("TRACKING_NUMBER_GENERATED", trackingNumber));
                
                return StepResult.continueWith(new LabeledOrder(allocated, trackingNumber));
            })
            .then("notify-warehouse", (labeled, ctx) -> {
                ctx.emit(new Event("WAREHOUSE_NOTIFICATION", labeled.trackingNumber()));
                
                return StepResult.finish(new FulfillmentResult(
                    labeled.allocated().reserved().order().orderId(),
                    labeled.trackingNumber(),
                    FulfillmentStatus.READY_TO_SHIP
                ));
            });
        
        engine.register(builder);
        
        // Test workflow execution
        Order order = new Order("ORD-789", Arrays.asList(
            new OrderItem("SKU-1", 2),
            new OrderItem("SKU-2", 1)
        ));
        
        FulfillmentResult result = executeWorkflow("fulfillment-workflow", order);
        
        // Verify result
        assertEquals("ORD-789", result.orderId());
        assertEquals(FulfillmentStatus.READY_TO_SHIP, result.status());
        assertNotNull(result.trackingNumber());
        
        // Verify events were emitted in correct order
        List<String> eventTypes = capturedEvents.stream()
            .map(WorkflowEvent::getType)
            .collect(Collectors.toList());
        
        assertEquals(Arrays.asList(
            "INVENTORY_CHECK",
            "INVENTORY_RESERVED",
            "SHIPPING_ALLOCATION_START",
            "SHIPPING_METHOD_SELECTED",
            "LABEL_GENERATION_START",
            "TRACKING_NUMBER_GENERATED",
            "WAREHOUSE_NOTIFICATION"
        ), eventTypes);
        
        // Test inventory shortage scenario
        orchestrator.mock()
            .workflow("fulfillment-workflow")
            .step("reserve-inventory")
            .always()
            .thenReturn(Order.class, ord -> {
                ctx.emit(new Event("INVENTORY_CHECK", ord.orderId()));
                ctx.emit(new Event("INVENTORY_SHORTAGE", ord.orderId()));
                return StepResult.fail("Insufficient inventory");
            });
        
        capturedEvents.clear();
        
        assertThrows(Exception.class, () -> 
            executeWorkflow("fulfillment-workflow", order)
        );
        
        // Verify shortage event was emitted
        assertTrue(capturedEvents.stream()
            .anyMatch(e -> e.getType().equals("INVENTORY_SHORTAGE")));
    }
    
    // Helper methods
    private boolean checkInventory(Order order) {
        return !order.orderId().contains("OOS"); // Out of stock
    }
    
    private ShippingMethod selectShippingMethod(Order order) {
        return order.items().size() > 5 ? ShippingMethod.FREIGHT : ShippingMethod.STANDARD;
    }
    
    // Domain classes
    record Order(String orderId, List<OrderItem> items) {}
    record OrderItem(String sku, int quantity) {}
    record ReservedOrder(Order order, String reservationId) {}
    enum ShippingMethod { STANDARD, EXPRESS, FREIGHT }
    record AllocatedOrder(ReservedOrder reserved, ShippingMethod method) {}
    record LabeledOrder(AllocatedOrder allocated, String trackingNumber) {}
    enum FulfillmentStatus { READY_TO_SHIP, SHIPPED, DELIVERED }
    record FulfillmentResult(String orderId, String trackingNumber, FulfillmentStatus status) {}
    record Event(String type, Object data) {}
}
```

## Saga Pattern Testing

### Testing Distributed Transaction Workflows

```java
public class SagaWorkflowTest extends WorkflowTestBase {
    
    @Test
    void testHotelBookingSaga() throws Exception {
        // Create saga workflow with compensation
        WorkflowBuilder<BookingRequest, BookingConfirmation> builder = WorkflowBuilder
            .define("booking-saga", BookingRequest.class, BookingConfirmation.class)
            
            // Step 1: Reserve hotel room
            .then("reserve-room", (request, ctx) -> {
                String reservationId = "ROOM-" + System.currentTimeMillis();
                ctx.set("room-reservation", reservationId);
                return StepResult.continueWith(new RoomReserved(reservationId, request));
            })
            
            // Step 2: Charge payment
            .then("charge-payment", (roomReserved, ctx) -> {
                try {
                    String paymentId = processPayment(roomReserved.request().creditCard());
                    ctx.set("payment-id", paymentId);
                    return StepResult.continueWith(new PaymentCharged(paymentId, roomReserved));
                } catch (PaymentException e) {
                    // Trigger compensation
                    ctx.set("compensation-needed", true);
                    return StepResult.fail("Payment failed: " + e.getMessage());
                }
            })
            
            // Step 3: Send confirmation
            .then("send-confirmation", (paymentCharged, ctx) -> {
                try {
                    String confirmationId = sendEmail(paymentCharged.roomReserved().request().email());
                    return StepResult.finish(new BookingConfirmation(
                        paymentCharged.roomReserved().reservationId(),
                        paymentCharged.paymentId(),
                        confirmationId
                    ));
                } catch (Exception e) {
                    ctx.set("compensation-needed", true);
                    return StepResult.fail("Confirmation failed");
                }
            })
            
            // Compensation flow
            .onError((error, ctx) -> {
                if (ctx.get("compensation-needed", false)) {
                    // Compensate in reverse order
                    String paymentId = (String) ctx.get("payment-id");
                    if (paymentId != null) {
                        refundPayment(paymentId);
                    }
                    
                    String roomReservation = (String) ctx.get("room-reservation");
                    if (roomReservation != null) {
                        cancelRoomReservation(roomReservation);
                    }
                }
                return StepResult.fail("Booking failed, compensations executed");
            });
        
        engine.register(builder);
        
        // Test successful saga
        BookingRequest request = new BookingRequest(
            "user@example.com",
            "2024-06-01",
            "2024-06-05",
            "VISA-1234"
        );
        
        BookingConfirmation confirmation = executeWorkflow("booking-saga", request);
        assertNotNull(confirmation.reservationId());
        assertNotNull(confirmation.paymentId());
        assertNotNull(confirmation.confirmationId());
        
        // Test saga with payment failure
        orchestrator.mock()
            .workflow("booking-saga")
            .step("charge-payment")
            .always()
            .thenFail(new PaymentException("Card declined"));
        
        // Track compensation calls
        AtomicBoolean roomCancelled = new AtomicBoolean(false);
        AtomicBoolean paymentRefunded = new AtomicBoolean(false);
        
        orchestrator.mock()
            .workflow("booking-saga")
            .step("onError")
            .always()
            .thenReturn(Object.class, err -> {
                roomCancelled.set(true);
                paymentRefunded.set(true);
                return StepResult.fail("Compensated");
            });
        
        // Execute and expect failure
        assertThrows(Exception.class, () -> 
            executeWorkflow("booking-saga", request)
        );
        
        // Verify compensations were executed
        assertTrue(roomCancelled.get(), "Room reservation should be cancelled");
        assertTrue(paymentRefunded.get(), "Payment should be refunded");
    }
    
    // Saga helper methods
    private String processPayment(String creditCard) {
        if (creditCard.contains("FAIL")) {
            throw new PaymentException("Payment processing failed");
        }
        return "PAY-" + System.currentTimeMillis();
    }
    
    private String sendEmail(String email) {
        return "CONF-" + System.currentTimeMillis();
    }
    
    private void refundPayment(String paymentId) {
        // Compensation logic
    }
    
    private void cancelRoomReservation(String reservationId) {
        // Compensation logic
    }
    
    // Domain classes
    record BookingRequest(String email, String checkIn, String checkOut, String creditCard) {}
    record RoomReserved(String reservationId, BookingRequest request) {}
    record PaymentCharged(String paymentId, RoomReserved roomReserved) {}
    record BookingConfirmation(String reservationId, String paymentId, String confirmationId) {}
    
    static class PaymentException extends RuntimeException {
        PaymentException(String message) { super(message); }
    }
}
```

## Dynamic Workflow Testing

### Testing Workflows with Dynamic Step Generation

```java
public class DynamicWorkflowTest extends WorkflowTestBase {
    
    @Test
    void testDynamicApprovalWorkflow() throws Exception {
        // Create workflow with dynamic approval steps
        WorkflowBuilder<ApprovalRequest, ApprovalResult> builder = WorkflowBuilder
            .define("dynamic-approval", ApprovalRequest.class, ApprovalResult.class)
            
            .then("determine-approvers", (request, ctx) -> {
                List<String> approvers = determineApprovers(request);
                ctx.set("approvers", approvers);
                ctx.set("approvals", new HashMap<String, Boolean>());
                return StepResult.continueWith(new ApprovalProcess(request, approvers));
            })
            
            .dynamicSteps("approval-loop", (process, ctx) -> {
                List<String> approvers = (List<String>) ctx.get("approvers");
                Map<String, Boolean> approvals = (Map<String, Boolean>) ctx.get("approvals");
                
                // Generate a step for each approver
                return approvers.stream()
                    .filter(approver -> !approvals.containsKey(approver))
                    .findFirst()
                    .map(approver -> {
                        return StepDefinition.create(
                            "approve-" + approver,
                            (ApprovalProcess proc, WorkflowContext c) -> {
                                // Simulate approval
                                boolean approved = !approver.contains("REJECT");
                                approvals.put(approver, approved);
                                c.set("approvals", approvals);
                                
                                if (!approved) {
                                    return StepResult.fail("Rejected by " + approver);
                                }
                                
                                // Check if all approvals are complete
                                if (approvals.size() == approvers.size()) {
                                    return StepResult.continueWith(new AllApproved(proc.request()));
                                }
                                
                                // Continue to next approver
                                return StepResult.continueWith(proc);
                            }
                        );
                    })
                    .orElse(null);
            })
            
            .then("finalize", (allApproved, ctx) -> {
                Map<String, Boolean> approvals = (Map<String, Boolean>) ctx.get("approvals");
                return StepResult.finish(new ApprovalResult(
                    allApproved.request().id(),
                    true,
                    approvals
                ));
            });
        
        engine.register(builder);
        
        // Test with multiple approvers
        ApprovalRequest request = new ApprovalRequest(
            "REQ-123",
            ApprovalType.BUDGET,
            50000.0
        );
        
        ApprovalResult result = executeWorkflow("dynamic-approval", request);
        assertTrue(result.approved());
        assertEquals(3, result.approverDecisions().size()); // Manager, Director, VP
        
        // Verify dynamic steps were executed
        assertions.assertStep("dynamic-approval", "approve-MANAGER").wasExecuted();
        assertions.assertStep("dynamic-approval", "approve-DIRECTOR").wasExecuted();
        assertions.assertStep("dynamic-approval", "approve-VP").wasExecuted();
        
        // Test rejection scenario
        ApprovalRequest rejectRequest = new ApprovalRequest(
            "REQ-456",
            ApprovalType.BUDGET,
            100000.0 // Will require REJECT-CFO
        );
        
        assertThrows(Exception.class, () -> 
            executeWorkflow("dynamic-approval", rejectRequest)
        );
    }
    
    // Helper method to determine approvers based on request
    private List<String> determineApprovers(ApprovalRequest request) {
        List<String> approvers = new ArrayList<>();
        
        if (request.amount() < 10000) {
            approvers.add("MANAGER");
        } else if (request.amount() < 50000) {
            approvers.add("MANAGER");
            approvers.add("DIRECTOR");
        } else if (request.amount() < 100000) {
            approvers.add("MANAGER");
            approvers.add("DIRECTOR");
            approvers.add("VP");
        } else {
            approvers.add("MANAGER");
            approvers.add("DIRECTOR");
            approvers.add("VP");
            approvers.add("REJECT-CFO"); // Will reject
        }
        
        return approvers;
    }
    
    // Domain classes
    enum ApprovalType { BUDGET, HIRE, PROJECT }
    record ApprovalRequest(String id, ApprovalType type, double amount) {}
    record ApprovalProcess(ApprovalRequest request, List<String> approvers) {}
    record AllApproved(ApprovalRequest request) {}
    record ApprovalResult(String requestId, boolean approved, Map<String, Boolean> approverDecisions) {}
}
```

## Integration Test Patterns

### Testing Workflows with External System Integration

```java
@SpringBootTest
@TestContainers
public class IntegrationWorkflowTest extends WorkflowTestBase {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("workflow_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }
    
    @Test
    void testDataProcessingWithRealDatabases() throws Exception {
        // Create workflow that uses real databases
        WorkflowBuilder<DataImportRequest, DataImportResult> builder = WorkflowBuilder
            .define("data-import", DataImportRequest.class, DataImportResult.class)
            
            .then("validate-schema", (request, ctx) -> {
                // Validate against real database schema
                try (Connection conn = dataSource.getConnection()) {
                    DatabaseMetaData meta = conn.getMetaData();
                    ResultSet tables = meta.getTables(null, null, request.tableName(), null);
                    if (!tables.next()) {
                        return StepResult.fail("Table does not exist: " + request.tableName());
                    }
                }
                return StepResult.continueWith(request);
            })
            
            .then("cache-metadata", (request, ctx) -> {
                // Store metadata in Redis
                String cacheKey = "import:" + request.importId();
                ImportMetadata metadata = new ImportMetadata(
                    request.importId(),
                    request.tableName(),
                    System.currentTimeMillis()
                );
                redisTemplate.opsForValue().set(cacheKey, metadata, Duration.ofHours(1));
                return StepResult.continueWith(new ValidatedImport(request, metadata));
            })
            
            .then("import-data", (validated, ctx) -> {
                // Import data to real database
                try (Connection conn = dataSource.getConnection()) {
                    // Start transaction
                    conn.setAutoCommit(false);
                    
                    String sql = "INSERT INTO " + validated.request().tableName() + 
                               " (id, data, created_at) VALUES (?, ?, ?)";
                    
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        for (Map<String, Object> record : validated.request().records()) {
                            stmt.setString(1, (String) record.get("id"));
                            stmt.setString(2, (String) record.get("data"));
                            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                            stmt.addBatch();
                        }
                        
                        int[] results = stmt.executeBatch();
                        conn.commit();
                        
                        return StepResult.continueWith(new ImportStats(
                            validated,
                            results.length,
                            0
                        ));
                    } catch (Exception e) {
                        conn.rollback();
                        throw e;
                    }
                }
            })
            
            .then("update-cache", (stats, ctx) -> {
                // Update Redis with import results
                String cacheKey = "import:" + stats.validated().request().importId() + ":stats";
                redisTemplate.opsForValue().set(cacheKey, stats, Duration.ofDays(7));
                
                return StepResult.finish(new DataImportResult(
                    stats.validated().request().importId(),
                    stats.successCount(),
                    stats.errorCount()
                ));
            });
        
        engine.register(builder);
        
        // Setup test data
        createTestTable();
        
        List<Map<String, Object>> testRecords = Arrays.asList(
            Map.of("id", "1", "data", "Test record 1"),
            Map.of("id", "2", "data", "Test record 2"),
            Map.of("id", "3", "data", "Test record 3")
        );
        
        DataImportRequest request = new DataImportRequest(
            "IMP-001",
            "test_imports",
            testRecords
        );
        
        // Execute workflow
        DataImportResult result = executeWorkflow("data-import", request);
        
        // Verify results
        assertEquals(3, result.successCount());
        assertEquals(0, result.errorCount());
        
        // Verify data in PostgreSQL
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_imports")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));
        }
        
        // Verify cache in Redis
        ImportMetadata cached = (ImportMetadata) redisTemplate.opsForValue()
            .get("import:IMP-001");
        assertNotNull(cached);
        assertEquals("test_imports", cached.tableName());
        
        ImportStats stats = (ImportStats) redisTemplate.opsForValue()
            .get("import:IMP-001:stats");
        assertNotNull(stats);
        assertEquals(3, stats.successCount());
    }
    
    private void createTestTable() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS test_imports (
                    id VARCHAR(50) PRIMARY KEY,
                    data TEXT,
                    created_at TIMESTAMP
                )
            """);
        }
    }
    
    // Domain classes
    record DataImportRequest(String importId, String tableName, List<Map<String, Object>> records) {}
    record ImportMetadata(String importId, String tableName, long timestamp) implements Serializable {}
    record ValidatedImport(DataImportRequest request, ImportMetadata metadata) {}
    record ImportStats(ValidatedImport validated, int successCount, int errorCount) implements Serializable {}
    record DataImportResult(String importId, int successCount, int errorCount) {}
}
```

## Summary

These advanced examples demonstrate:

1. **Stateful Workflows** - Managing complex state across workflow steps
2. **Multi-Stage Pipelines** - Testing ETL and data processing workflows
3. **Event-Driven Patterns** - Workflows that emit and react to events
4. **Saga Pattern** - Distributed transactions with compensation
5. **Dynamic Workflows** - Workflows that generate steps at runtime
6. **Integration Testing** - Testing with real external systems using TestContainers

Each example shows best practices for:
- Proper mock usage
- Comprehensive assertions
- Performance testing
- Error handling
- Integration with external systems

Use these patterns as templates for testing your own complex workflow scenarios.