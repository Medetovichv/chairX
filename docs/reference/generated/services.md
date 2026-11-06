<!-- Auto-generated; do not edit. See scripts/generate-codebase-reference.py -->

# Application-service public functions

Static method inventory, not a call graph or transactional safety proof. Inspect code and tests for state transitions, locks, side effects and permissions.

## customer / CustomerAddressService

Source: [backend/src/main/java/kg/chairx/customer/application/CustomerAddressService.java](../../../backend/src/main/java/kg/chairx/customer/application/CustomerAddressService.java)

- list(UUID customerId)
- add(UUID customerId, AddressInput input)

## customer / CustomerMapper

Source: [backend/src/main/java/kg/chairx/customer/application/CustomerMapper.java](../../../backend/src/main/java/kg/chairx/customer/application/CustomerMapper.java)

- toResponse(Customer customer)

## customer / CustomerOverviewService

Source: [backend/src/main/java/kg/chairx/customer/application/CustomerOverviewService.java](../../../backend/src/main/java/kg/chairx/customer/application/CustomerOverviewService.java)

- list(String query, int page, int size)

## customer / CustomerPhoneResolver

Source: [backend/src/main/java/kg/chairx/customer/application/CustomerPhoneResolver.java](../../../backend/src/main/java/kg/chairx/customer/application/CustomerPhoneResolver.java)

- resolve(String phone)

## customer / CustomerService

Source: [backend/src/main/java/kg/chairx/customer/application/CustomerService.java](../../../backend/src/main/java/kg/chairx/customer/application/CustomerService.java)

- create(CreateCustomerRequest request)
- get(UUID customerId)
- list( int page, int size )
- search(String query)
- update( UUID customerId, UpdateCustomerRequest request )
- activate(UUID customerId)
- deactivate(UUID customerId)

## customer / KyrgyzPhoneNormalizer

Source: [backend/src/main/java/kg/chairx/customer/application/KyrgyzPhoneNormalizer.java](../../../backend/src/main/java/kg/chairx/customer/application/KyrgyzPhoneNormalizer.java)

- local(String raw)

## dashboard / DashboardService

Source: [backend/src/main/java/kg/chairx/dashboard/application/DashboardService.java](../../../backend/src/main/java/kg/chairx/dashboard/application/DashboardService.java)

- overview()

## defect / DefectService

Source: [backend/src/main/java/kg/chairx/defect/application/DefectService.java](../../../backend/src/main/java/kg/chairx/defect/application/DefectService.java)

- get(UUID id)
- list(DefectStatus status,int page,int size)
- open( UUID warehouseId, UUID productVariantId, UUID supplierId, UUID purchaseReceiptItemId, long quantity, String description, String actor )
- waitForParts(UUID defectId)
- resolve( UUID defectId, String resolutionNote, String actor )
- writeOff( UUID defectId, String resolutionNote, String actor )

## delivery / DeliveryMapper

Source: [backend/src/main/java/kg/chairx/delivery/application/DeliveryMapper.java](../../../backend/src/main/java/kg/chairx/delivery/application/DeliveryMapper.java)

- toResponse( Delivery delivery )

## delivery / DeliveryRuleViolationException

Source: [backend/src/main/java/kg/chairx/delivery/application/DeliveryRuleViolationException.java](../../../backend/src/main/java/kg/chairx/delivery/application/DeliveryRuleViolationException.java)

- code()

## delivery / DeliveryService

Source: [backend/src/main/java/kg/chairx/delivery/application/DeliveryService.java](../../../backend/src/main/java/kg/chairx/delivery/application/DeliveryService.java)

- create( @Valid CreateDeliveryRequest request )
- list( DeliveryStatus status, LocalDate from, LocalDate to, int page, int size)
- list( DeliveryStatus status, LocalDate from, LocalDate to, LocalDate plannedFrom, LocalDate plannedTo, int page, int size)
- list( DeliveryStatus status, LocalDate from, LocalDate to, LocalDate plannedFrom, LocalDate plannedTo, String cityRegion, int page, int size)
- list( DeliveryStatus status, LocalDate from, LocalDate to, LocalDate plannedFrom, LocalDate plannedTo, String cityRegion, int page, int size, boolean operational)
- list(DeliveryStatus status,LocalDate from,LocalDate to, LocalDate plannedFrom,LocalDate plannedTo,String cityRegion,int page,int size, boolean operational,boolean pendingOnly,LocalDate )
- get( UUID deliveryId )
- getBySaleId( UUID saleId )
- changePlannedDate(UUID deliveryId, LocalDate plannedDate)
- dispatch( UUID deliveryId )
- markDelivered( UUID deliveryId )
- markFailed( UUID deliveryId, @Valid FailDeliveryRequest request )
- cancel(UUID deliveryId)
- cancelWithReason(UUID deliveryId, kg.chairx.sale.api.CancelSaleRequest request)
- returnToWarehouse( UUID deliveryId, @Valid ReturnDeliveryToWarehouseRequest request )

## expense / ExpenseAccrualService

Source: [backend/src/main/java/kg/chairx/expense/application/ExpenseAccrualService.java](../../../backend/src/main/java/kg/chairx/expense/application/ExpenseAccrualService.java)

- create(Create input, String actor)
- linkExistingPayment(UUID accrualId, UUID expenseId, String actor)
- report(LocalDate from, LocalDate to)

## expense / ExpenseConflictException

Source: [backend/src/main/java/kg/chairx/expense/application/ExpenseConflictException.java](../../../backend/src/main/java/kg/chairx/expense/application/ExpenseConflictException.java)

- getCode()

## expense / ExpensePeriodAllocation

Source: [backend/src/main/java/kg/chairx/expense/application/ExpensePeriodAllocation.java](../../../backend/src/main/java/kg/chairx/expense/application/ExpensePeriodAllocation.java)

- accruedWithin( BigDecimal amount, LocalDate periodStart, LocalDate periodEnd, LocalDate fromInclusive, LocalDate toExclusive)
- outstanding(BigDecimal accrued, BigDecimal paid)
- prepaid(BigDecimal accrued, BigDecimal paid)
- byMonth( BigDecimal amount, LocalDate fromInclusive, LocalDate toExclusive)
- position( BigDecimal amount, LocalDate periodStart, LocalDate periodEnd, LocalDate reportFrom, LocalDate reportTo, BigDecimal actuallyPaid)
- allocate( BigDecimal amount, LocalDate fromInclusive, LocalDate toExclusive)

## expense / ExpenseService

Source: [backend/src/main/java/kg/chairx/expense/application/ExpenseService.java](../../../backend/src/main/java/kg/chairx/expense/application/ExpenseService.java)

- create(CreateExpenseRequest request)
- createForClosing(LocalDate closingDate, CreateExpenseRequest request)
- get(UUID id)
- list( LocalDate from, LocalDate to )
- total( LocalDate from, LocalDate to )

## finance / CashFlowService

Source: [backend/src/main/java/kg/chairx/finance/application/CashFlowService.java](../../../backend/src/main/java/kg/chairx/finance/application/CashFlowService.java)

- summary(Instant from, Instant to)

## finance / DailyClosingAccessPolicy

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingAccessPolicy.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingAccessPolicy.java)

- now()
- today()
- today(Instant at)
- normalWriteWindow(LocalDate businessDate, Instant at)
- managerMayUnlock(LocalDate businessDate, Instant at)
- adminMayUnlock(LocalDate businessDate, Instant at)
- expiry(LocalDate businessDate, Instant grantedAt, boolean byAdmin)
- grantActive(Instant at, Instant expiresAt, Instant revokedAt)

## finance / DailyClosingAccessService

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingAccessService.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingAccessService.java)

- lockDate(LocalDate date)
- get(LocalDate date)
- assertCanEdit(LocalDate date)
- unlock(LocalDate date, String reason)
- revoke(LocalDate date)

## finance / DailyClosingSalesSnapshot

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingSalesSnapshot.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingSalesSnapshot.java)

- acquireCompletionGate(JdbcClient jdbc)
- capture(JdbcClient jdbc, UUID closingId, LocalDate day)
- read(JdbcClient jdbc, LocalDate date)
- live(JdbcClient jdbc, LocalDate day)

## finance / DailyClosingService

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingService.java)

- close(LocalDate date, DailyClosingRequest request, String actor)
- closeAuthorized(LocalDate date, DailyClosingRequest request, String actor)
- update(LocalDate date, DailyClosingUpdateRequest request, String actor)
- refreshExpectedAfterCorrection(LocalDate date, String actor, UUID sourceExpenseId)
- preview(LocalDate date)
- findByDate(LocalDate date)
- closedDates()
- history(LocalDate date)

## finance / EmployeeClosingManagerReportService

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeClosingManagerReportService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeClosingManagerReportService.java)

- report(LocalDate date)

## finance / EmployeeDailyClosingService

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java)

- companyDates(String username)
- companyReport(String username,LocalDate date)
- dates(String username)
- get(String username, LocalDate date)
- close(String username, LocalDate date, Request input)
- closeCompany(String username,LocalDate date,Request input)

## finance / FinanceAdminAdjustmentService

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceAdminAdjustmentService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceAdminAdjustmentService.java)

- adjust(AdminBalanceAdjustmentRequest request, String actor)

## finance / FinanceConflictException

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceConflictException.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceConflictException.java)

- getCode()

## finance / FinanceOpeningBalanceService

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceOpeningBalanceService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceOpeningBalanceService.java)

- initialize( FinanceAccount account, BigDecimal amount, String actor )

## finance / FinancePostingService

Source: [backend/src/main/java/kg/chairx/finance/application/FinancePostingService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinancePostingService.java)

- account(String method)
- post(String method, BigDecimal amount, String type, String source, UUID sourceId, String actor)
- acquireOperationalExpenseGate()
- postExpense(String method, BigDecimal amount, UUID expenseId, String actor, java.time.LocalDate expenseDate)
- assertCorrectionPermitted(java.time.LocalDate date, String actor)
- postHistoricalExpense(String method, BigDecimal amount, UUID expenseId, String actor, java.time.LocalDate date)
- reversePayment(String method, BigDecimal amount, UUID paymentId, String actor)

## finance / FinanceProfitabilityService

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceProfitabilityService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceProfitabilityService.java)

- calculate(LocalDate from,LocalDate to)

## finance / FinanceQueryService

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceQueryService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceQueryService.java)

- getAccounts()

## finance / FinanceReconciliationService

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceReconciliationService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceReconciliationService.java)

- listIssues()

## finance / FinanceSaleAdjustmentsService

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceSaleAdjustmentsService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceSaleAdjustmentsService.java)

- forPeriod(LocalDate from,LocalDate to)

## finance / FinanceTransferService

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceTransferService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceTransferService.java)

- transfer( UUID transferId, FinanceAccount from, FinanceAccount to, BigDecimal amount, String actor )

## finance / HistoricalFinanceBalanceService

Source: [backend/src/main/java/kg/chairx/finance/application/HistoricalFinanceBalanceService.java](../../../backend/src/main/java/kg/chairx/finance/application/HistoricalFinanceBalanceService.java)

- expectedAtEndOf(LocalDate date, FinanceAccount account, BigDecimal lockedCurrent)

## inventory / InventoryOverviewService

Source: [backend/src/main/java/kg/chairx/inventory/application/InventoryOverviewService.java](../../../backend/src/main/java/kg/chairx/inventory/application/InventoryOverviewService.java)

- list(UUID warehouseId, String model, String variation, boolean includeZero, boolean onlyAvailable, int page, int size)
- list(UUID warehouseId, String model, String variation, String search, boolean includeZero, boolean onlyAvailable, int page, int size)
- list(UUID warehouseId,String model,String variation,String search, boolean includeZero,boolean onlyAvailable,int page,int size,boolean lowStock)

## inventory / InventoryService

Source: [backend/src/main/java/kg/chairx/inventory/application/InventoryService.java](../../../backend/src/main/java/kg/chairx/inventory/application/InventoryService.java)

- recordMovement( @NotNull @Valid RecordStockMovement command )
- reserve( @NotNull @Valid ChangeReservedStock command )
- releaseReservation( @NotNull @Valid ChangeReservedStock command )
- block( ChangeBlockedStock command )
- unblock( ChangeBlockedStock command )
- getBalance( @NotNull UUID warehouseId, @NotNull UUID variantId )
- listBalances( @NotNull UUID warehouseId, @Min(0) int page, @Min(1) @Max(100) int size )
- listMovements( @NotNull UUID warehouseId, @NotNull UUID variantId, @Min( value = 0, message = "Номер страницы не может быть отрицательным" ) int page, @Min( value = 1, message = "Размер страницы)

## inventory / InventoryTransferQueryService

Source: [backend/src/main/java/kg/chairx/inventory/application/InventoryTransferQueryService.java](../../../backend/src/main/java/kg/chairx/inventory/application/InventoryTransferQueryService.java)

- get(UUID id)
- list( UUID warehouseId, UUID variantId, OffsetDateTime from, OffsetDateTime to, int page, int size )

## inventory / InventoryTransferService

Source: [backend/src/main/java/kg/chairx/inventory/application/InventoryTransferService.java](../../../backend/src/main/java/kg/chairx/inventory/application/InventoryTransferService.java)

- transfer( UUID transferId, UUID sourceWarehouse, UUID destinationWarehouse, UUID variantId, long quantity, String actor )

## inventory / WarehouseStockSummaryService

Source: [backend/src/main/java/kg/chairx/inventory/application/WarehouseStockSummaryService.java](../../../backend/src/main/java/kg/chairx/inventory/application/WarehouseStockSummaryService.java)

- overview()

## payment / PaymentBalanceService

Source: [backend/src/main/java/kg/chairx/payment/application/PaymentBalanceService.java](../../../backend/src/main/java/kg/chairx/payment/application/PaymentBalanceService.java)

- availableBalance(UUID saleId)

## payment / PaymentMapper

Source: [backend/src/main/java/kg/chairx/payment/application/PaymentMapper.java](../../../backend/src/main/java/kg/chairx/payment/application/PaymentMapper.java)

- toResponse( Payment payment )

## payment / PaymentRuleViolationException

Source: [backend/src/main/java/kg/chairx/payment/application/PaymentRuleViolationException.java](../../../backend/src/main/java/kg/chairx/payment/application/PaymentRuleViolationException.java)

- getCode()

## payment / PaymentService

Source: [backend/src/main/java/kg/chairx/payment/application/PaymentService.java](../../../backend/src/main/java/kg/chairx/payment/application/PaymentService.java)

- create( @Valid CreatePaymentRequest request )
- get(UUID paymentId)
- getBySale(UUID saleId)
- getActiveBySale(UUID saleId)
- cancel( UUID paymentId, @Valid CancelPaymentRequest request )

## payment / PickupCheckoutService

Source: [backend/src/main/java/kg/chairx/payment/application/PickupCheckoutService.java](../../../backend/src/main/java/kg/chairx/payment/application/PickupCheckoutService.java)

- checkout(@Valid CreatePaymentRequest request)

## product / ProductService

Source: [backend/src/main/java/kg/chairx/product/application/ProductService.java](../../../backend/src/main/java/kg/chairx/product/application/ProductService.java)

- create(@Valid CreateProductRequest request)
- get(UUID id)
- list(int page, int size)
- list(int page, int size, String search, String category, Boolean active)
- update(UUID id, @Valid UpdateProductRequest request)
- activate(UUID id)
- deactivate(UUID id)

## product / ProductVariantService

Source: [backend/src/main/java/kg/chairx/product/application/ProductVariantService.java](../../../backend/src/main/java/kg/chairx/product/application/ProductVariantService.java)

- create(UUID productId, @Valid CreateProductVariantRequest request)
- get(UUID id)
- list(UUID productId, int page, int size)
- update(UUID id, @Valid UpdateProductVariantRequest request)
- activate(UUID id)
- deactivate(UUID id)

## purchase / PurchaseOverviewService

Source: [backend/src/main/java/kg/chairx/purchase/application/PurchaseOverviewService.java](../../../backend/src/main/java/kg/chairx/purchase/application/PurchaseOverviewService.java)

- list(PurchaseStatus status, UUID supplierId, LocalDate from, LocalDate to, String number, int page, int size)
- list(PurchaseStatus status, UUID supplierId, LocalDate from, LocalDate to, String number, int page, int size, boolean includeOpenOlder)

## purchase / PurchasePaymentService

Source: [backend/src/main/java/kg/chairx/purchase/application/PurchasePaymentService.java](../../../backend/src/main/java/kg/chairx/purchase/application/PurchasePaymentService.java)

- create(UUID purchaseId, CreatePurchasePaymentRequest request)
- list(UUID purchaseId)

## purchase / PurchaseRuleViolationException

Source: [backend/src/main/java/kg/chairx/purchase/application/PurchaseRuleViolationException.java](../../../backend/src/main/java/kg/chairx/purchase/application/PurchaseRuleViolationException.java)

- getCode()

## purchase / PurchaseService

Source: [backend/src/main/java/kg/chairx/purchase/application/PurchaseService.java](../../../backend/src/main/java/kg/chairx/purchase/application/PurchaseService.java)

- create(@Valid CreatePurchaseRequest request)
- update(UUID id, @Valid UpdatePurchaseRequest request)
- confirm(UUID id)
- cancel(UUID id)
- setCargo(UUID id, @Valid SetCargoCostRequest request)
- get(UUID id)
- list(@Min(0) int page,@Min(1) @Max(100) int size)
- getReceipt(UUID purchaseId,UUID receiptId)
- receipts(UUID purchaseId,@Min(0) int page,@Min(1) @Max(100) int size)
- prepareReceipt(UUID purchaseId,@Valid CreatePurchaseReceiptRequest request)

## refund / RefundRuleViolationException

Source: [backend/src/main/java/kg/chairx/refund/application/RefundRuleViolationException.java](../../../backend/src/main/java/kg/chairx/refund/application/RefundRuleViolationException.java)

- getCode()

## refund / RefundService

Source: [backend/src/main/java/kg/chairx/refund/application/RefundService.java](../../../backend/src/main/java/kg/chairx/refund/application/RefundService.java)

- create( @NotNull @Valid CreateRefundRequest request )
- get(UUID refundId)
- getBySale(UUID saleId)

## returning / ReturnRuleViolationException

Source: [backend/src/main/java/kg/chairx/returning/application/ReturnRuleViolationException.java](../../../backend/src/main/java/kg/chairx/returning/application/ReturnRuleViolationException.java)

- getCode()

## returning / ReturnService

Source: [backend/src/main/java/kg/chairx/returning/application/ReturnService.java](../../../backend/src/main/java/kg/chairx/returning/application/ReturnService.java)

- create( @NotNull @Valid CreateReturnRequest request )
- list(UUID saleId,String number,int page,int size)
- get(@NotNull UUID returnId)

## sale / PaidPickupService

Source: [backend/src/main/java/kg/chairx/sale/application/PaidPickupService.java](../../../backend/src/main/java/kg/chairx/sale/application/PaidPickupService.java)

- payAndHandOver(UUID saleId, @Valid CreatePaymentRequest request)

## sale / SaleCommentService

Source: [backend/src/main/java/kg/chairx/sale/application/SaleCommentService.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleCommentService.java)

- list(UUID saleId, int page, int size)
- create(UUID saleId, CreateSaleCommentRequest request)

## sale / SaleHistoryService

Source: [backend/src/main/java/kg/chairx/sale/application/SaleHistoryService.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleHistoryService.java)

- list(UUID saleId, int page, int size)

## sale / SaleRequestFingerprint

Source: [backend/src/main/java/kg/chairx/sale/application/SaleRequestFingerprint.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleRequestFingerprint.java)

- fingerprint( CreateSaleRequest request )
- fingerprintDraft(kg.chairx.sale.api.CreateDraftSaleRequest request)

## sale / SaleRuleViolationException

Source: [backend/src/main/java/kg/chairx/sale/application/SaleRuleViolationException.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleRuleViolationException.java)

- getCode()

## sale / SaleService

Source: [backend/src/main/java/kg/chairx/sale/application/SaleService.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleService.java)

- list(int page,int size,SaleStatus status, LocalDate from,LocalDate to,String saleNumber)
- list(int page,int size,SaleStatus status, LocalDate from,LocalDate to,String saleNumber, boolean operational)
- list(int page,int size,SaleStatus status,LocalDate from,LocalDate to, String saleNumber,boolean operational,boolean activeOnly)
- listForCustomer(UUID customerId, int page, int size)
- createDraft(@Valid kg.chairx.sale.api.CreateDraftSaleRequest request)
- updateDraft(UUID id, @Valid kg.chairx.sale.api.UpdateDraftSaleRequest request)
- confirmDraft(UUID id)
- create( @Valid CreateSaleRequest request )
- get(UUID saleId)
- fulfill(UUID saleId)
- fulfillForDelivery( UUID saleId )
- cancelWithReason(UUID saleId, kg.chairx.sale.api.CancelSaleRequest request)
- cancellationDetails(UUID saleId)
- cancel(UUID saleId)
- cancelForDelivery( UUID saleId )
- cancelForDeliveryWithReason( UUID saleId, kg.chairx.sale.api.CancelSaleRequest request)
- lockForInventoryReturn(UUID saleId)

## sale / SaleStatisticsService

Source: [backend/src/main/java/kg/chairx/sale/application/SaleStatisticsService.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleStatisticsService.java)

- last30Days()

## sale / SaleWithDeliveryService

Source: [backend/src/main/java/kg/chairx/sale/application/SaleWithDeliveryService.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleWithDeliveryService.java)

- create(@Valid CreateSaleWithDeliveryRequest request)

## security / AdminBootstrapRunner

Source: [backend/src/main/java/kg/chairx/security/application/AdminBootstrapRunner.java](../../../backend/src/main/java/kg/chairx/security/application/AdminBootstrapRunner.java)

- run(ApplicationArguments args)

## security / AdminBootstrapService

Source: [backend/src/main/java/kg/chairx/security/application/AdminBootstrapService.java](../../../backend/src/main/java/kg/chairx/security/application/AdminBootstrapService.java)

- initialize( String username, String password, String displayName )

## security / ChairxUserDetailsService

Source: [backend/src/main/java/kg/chairx/security/application/ChairxUserDetailsService.java](../../../backend/src/main/java/kg/chairx/security/application/ChairxUserDetailsService.java)

- loadUserByUsername(String username)

## security / CompositeUserDetailsService

Source: [backend/src/main/java/kg/chairx/security/application/CompositeUserDetailsService.java](../../../backend/src/main/java/kg/chairx/security/application/CompositeUserDetailsService.java)

- loadUserByUsername(String username)

## security / LocalDevUsersRunner

Source: [backend/src/main/java/kg/chairx/security/application/LocalDevUsersRunner.java](../../../backend/src/main/java/kg/chairx/security/application/LocalDevUsersRunner.java)

- run(ApplicationArguments args)

## security / LocalDevUsersService

Source: [backend/src/main/java/kg/chairx/security/application/LocalDevUsersService.java](../../../backend/src/main/java/kg/chairx/security/application/LocalDevUsersService.java)

- ensureUsers(String adminUsername, String managerPassword, String employeePassword)

## security / RoleAssignmentService

Source: [backend/src/main/java/kg/chairx/security/application/RoleAssignmentService.java](../../../backend/src/main/java/kg/chairx/security/application/RoleAssignmentService.java)

- removeRole( UUID actorUserId, UUID targetUserId, UUID roleId )
- assignRole( UUID actorUserId, UUID targetUserId, UUID roleId )

## security / RoleManagementService

Source: [backend/src/main/java/kg/chairx/security/application/RoleManagementService.java](../../../backend/src/main/java/kg/chairx/security/application/RoleManagementService.java)

- adminOnly(String code)
- list(Authentication auth)
- get(Authentication auth, UUID roleId)
- permissions(Authentication auth)
- audit(Authentication auth, UUID targetId, int page, int size)
- create(Authentication auth, CreateRole command)
- update(Authentication auth, UUID roleId, UpdateRole command)

## security / UserManagementService

Source: [backend/src/main/java/kg/chairx/security/application/UserManagementService.java](../../../backend/src/main/java/kg/chairx/security/application/UserManagementService.java)

- deactivateUser( UUID actorUserId, UUID targetUserId )
- activateUser(UUID actorUserId, UUID targetUserId)
- resetPassword(UUID actorUserId, UUID targetUserId, String newPassword)
- createUser(UUID actorUserId, CreateUserCommand command)

## supplier / SupplierService

Source: [backend/src/main/java/kg/chairx/supplier/application/SupplierService.java](../../../backend/src/main/java/kg/chairx/supplier/application/SupplierService.java)

- create(@Valid CreateSupplierRequest request)
- get(UUID id)
- list(int page, int size)
- update(UUID id, @Valid UpdateSupplierRequest request)
- activate(UUID id)
- deactivate(UUID id)

## warehouse / WarehouseService

Source: [backend/src/main/java/kg/chairx/warehouse/application/WarehouseService.java](../../../backend/src/main/java/kg/chairx/warehouse/application/WarehouseService.java)

- create(@Valid CreateWarehouseRequest request)
- get(UUID id)
- list(int page, int size)
- update(UUID id, @Valid UpdateWarehouseRequest request)
- activate(UUID id)
- deactivate(UUID id)

