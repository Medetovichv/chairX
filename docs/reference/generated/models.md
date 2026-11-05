<!-- Auto-generated; do not edit. See scripts/generate-codebase-reference.py -->

# Java records and enums

These are Java record components and enum literals, not guaranteed JSON response schemas. Read DTO validators and mappers for contract semantics.

Records: 215. Enums: 16.

### kg.chairx.common.web.ApiError

Source: [backend/src/main/java/kg/chairx/common/web/ApiError.java](../../../backend/src/main/java/kg/chairx/common/web/ApiError.java)

- String code
- String message
- Map<String
- ?> details

### kg.chairx.common.web.CsrfResponse

Source: [backend/src/main/java/kg/chairx/common/web/CsrfController.java](../../../backend/src/main/java/kg/chairx/common/web/CsrfController.java)

- String headerName
- String token

### kg.chairx.customer.api.CreateCustomerRequest

Source: [backend/src/main/java/kg/chairx/customer/api/CreateCustomerRequest.java](../../../backend/src/main/java/kg/chairx/customer/api/CreateCustomerRequest.java)

- @Size(max = 200, message = "Имя клиента не должно превышать 200 символов") String fullName
- @Size(max = 50) String phone
- @Size(max = 50) String secondaryPhone
- @Size(max = 50) String whatsappPhone
- @Size(max = 100) String instagramUsername
- @Size(max = 500) String address
- @Size(max = 200) String cityRegion
- @Size(max = 2000) String comment

### kg.chairx.customer.api.CustomerOverviewPage

Source: [backend/src/main/java/kg/chairx/customer/api/CustomerOverviewPage.java](../../../backend/src/main/java/kg/chairx/customer/api/CustomerOverviewPage.java)

- List<Line> items
- int page
- int size
- long total

### kg.chairx.customer.api.CustomerPageResponse

Source: [backend/src/main/java/kg/chairx/customer/api/CustomerPageResponse.java](../../../backend/src/main/java/kg/chairx/customer/api/CustomerPageResponse.java)

- List<CustomerResponse> items
- int page
- int size
- long totalElements

### kg.chairx.customer.api.CustomerResponse

Source: [backend/src/main/java/kg/chairx/customer/api/CustomerResponse.java](../../../backend/src/main/java/kg/chairx/customer/api/CustomerResponse.java)

- UUID id
- String fullName
- String phone
- String secondaryPhone
- String whatsappPhone
- String instagramUsername
- String address
- String cityRegion
- String comment
- boolean active
- Instant createdAt
- Instant updatedAt

### kg.chairx.customer.api.Line

Source: [backend/src/main/java/kg/chairx/customer/api/CustomerOverviewPage.java](../../../backend/src/main/java/kg/chairx/customer/api/CustomerOverviewPage.java)

- UUID id
- String fullName
- String phone
- String whatsappPhone
- String cityRegion
- long orderCount
- String lastSaleNumber
- Instant lastOrderAt

### kg.chairx.customer.api.ResolveCustomerPhoneRequest

Source: [backend/src/main/java/kg/chairx/customer/api/ResolveCustomerPhoneRequest.java](../../../backend/src/main/java/kg/chairx/customer/api/ResolveCustomerPhoneRequest.java)

- @NotBlank @Size(max=50) String phone

### kg.chairx.customer.api.UpdateCustomerRequest

Source: [backend/src/main/java/kg/chairx/customer/api/UpdateCustomerRequest.java](../../../backend/src/main/java/kg/chairx/customer/api/UpdateCustomerRequest.java)

- @Size(max = 200, message = "Имя клиента не должно превышать 200 символов") String fullName
- @Size(max = 50) String phone
- @Size(max = 50) String secondaryPhone
- @Size(max = 50) String whatsappPhone
- @Size(max = 100) String instagramUsername
- @Size(max = 500) String address
- @Size(max = 200) String cityRegion
- @Size(max = 2000) String comment

### kg.chairx.customer.application.Address

Source: [backend/src/main/java/kg/chairx/customer/application/CustomerAddressService.java](../../../backend/src/main/java/kg/chairx/customer/application/CustomerAddressService.java)

- UUID id
- UUID customerId
- String address
- String cityRegion
- Instant createdAt

### kg.chairx.customer.application.AddressInput

Source: [backend/src/main/java/kg/chairx/customer/application/CustomerAddressService.java](../../../backend/src/main/java/kg/chairx/customer/application/CustomerAddressService.java)

- String address
- String cityRegion

### kg.chairx.customer.application.Candidate

Source: [backend/src/main/java/kg/chairx/customer/application/CustomerPhoneResolver.java](../../../backend/src/main/java/kg/chairx/customer/application/CustomerPhoneResolver.java)

- UUID id
- boolean active

### kg.chairx.customer.domain.Customer

Source: [backend/src/main/java/kg/chairx/customer/domain/Customer.java](../../../backend/src/main/java/kg/chairx/customer/domain/Customer.java)

- UUID id
- String fullName
- String phone
- String secondaryPhone
- String whatsappPhone
- String instagramUsername
- String address
- String cityRegion
- String comment
- boolean active
- Instant createdAt
- Instant updatedAt

### kg.chairx.dashboard.api.DashboardOverview

Source: [backend/src/main/java/kg/chairx/dashboard/api/DashboardOverview.java](../../../backend/src/main/java/kg/chairx/dashboard/api/DashboardOverview.java)

- LocalDate businessDate
- Long orders30Days
- Long activeOrders
- Long pendingDeliveries
- Long deliveredToday
- Long delayedDeliveries
- Long availableUnits
- Long lowStockVariants
- List<SalesDay> ordersByDay
- List<SaleSummary> recentSales

### kg.chairx.dashboard.api.SalesDay

Source: [backend/src/main/java/kg/chairx/dashboard/api/DashboardOverview.java](../../../backend/src/main/java/kg/chairx/dashboard/api/DashboardOverview.java)

- LocalDate day
- long orders

### kg.chairx.defect.api.DefectPageResponse

Source: [backend/src/main/java/kg/chairx/defect/api/DefectPageResponse.java](../../../backend/src/main/java/kg/chairx/defect/api/DefectPageResponse.java)

- List<Defect> items
- int page
- int size
- long total

### kg.chairx.defect.api.DefectResolutionRequest

Source: [backend/src/main/java/kg/chairx/defect/api/DefectResolutionRequest.java](../../../backend/src/main/java/kg/chairx/defect/api/DefectResolutionRequest.java)

- @NotBlank @Size(max=4000) String resolutionNote

### kg.chairx.defect.api.OpenDefectRequest

Source: [backend/src/main/java/kg/chairx/defect/api/OpenDefectRequest.java](../../../backend/src/main/java/kg/chairx/defect/api/OpenDefectRequest.java)

- @NotNull UUID warehouseId
- @NotNull UUID productVariantId
- UUID supplierId
- UUID purchaseReceiptItemId
- @Min(1) long quantity
- @NotBlank @Size(max=4000) String description

### kg.chairx.defect.domain.Defect

Source: [backend/src/main/java/kg/chairx/defect/domain/Defect.java](../../../backend/src/main/java/kg/chairx/defect/domain/Defect.java)

- UUID id
- UUID warehouseId
- UUID productVariantId
- UUID supplierId
- UUID purchaseReceiptItemId
- long quantity
- DefectStatus status
- String description
- String resolutionNote
- String createdBy
- Instant createdAt
- String resolvedBy
- Instant resolvedAt

### kg.chairx.defect.domain.ReceiptItemOrigin

Source: [backend/src/main/java/kg/chairx/defect/domain/ReceiptItemOrigin.java](../../../backend/src/main/java/kg/chairx/defect/domain/ReceiptItemOrigin.java)

- UUID receiptItemId
- UUID warehouseId
- UUID productVariantId
- UUID supplierId

### kg.chairx.defect.web.Origin

Source: [backend/src/main/java/kg/chairx/defect/web/DefectReceiptOriginsController.java](../../../backend/src/main/java/kg/chairx/defect/web/DefectReceiptOriginsController.java)

- UUID id
- UUID purchaseId
- UUID supplierId
- Instant postedAt
- long quantity
- String model
- String variant
- String warehouse
- String supplier

### kg.chairx.defect.web.Page

Source: [backend/src/main/java/kg/chairx/defect/web/DefectReceiptOriginsController.java](../../../backend/src/main/java/kg/chairx/defect/web/DefectReceiptOriginsController.java)

- List<Origin> items
- int page
- int size
- long total

### kg.chairx.delivery.api.CreateDeliveryRequest

Source: [backend/src/main/java/kg/chairx/delivery/api/CreateDeliveryRequest.java](../../../backend/src/main/java/kg/chairx/delivery/api/CreateDeliveryRequest.java)

- @NotNull UUID saleId
- @Size(max = 200) String recipientName
- @NotBlank @Size(max = 50) String recipientPhone
- @NotBlank @Size(max = 500) String address
- @Size(max = 200) String cityRegion
- @NotNull @DecimalMin("0") @Digits(integer = 19, fraction = 0) BigDecimal deliveryCost
- @Size(max = 200) String carrierName
- @Size(max = 200) String trackingNumber
- @Size(max = 2000) String comment
- LocalDate plannedDeliveryDate

### kg.chairx.delivery.api.DeliveryPageResponse

Source: [backend/src/main/java/kg/chairx/delivery/api/DeliveryPageResponse.java](../../../backend/src/main/java/kg/chairx/delivery/api/DeliveryPageResponse.java)

- List<DeliverySummary> items
- int page
- int size
- long total

### kg.chairx.delivery.api.DeliveryResponse

Source: [backend/src/main/java/kg/chairx/delivery/api/DeliveryResponse.java](../../../backend/src/main/java/kg/chairx/delivery/api/DeliveryResponse.java)

- UUID id
- UUID saleId
- DeliveryStatus status
- String recipientName
- String recipientPhone
- String address
- String cityRegion
- BigDecimal deliveryCost
- String carrierName
- String trackingNumber
- String comment
- String createdBy
- Instant createdAt
- String dispatchedBy
- Instant dispatchedAt
- String deliveredBy
- Instant deliveredAt
- String failedBy
- Instant failedAt
- String failureReason
- String cancelledBy
- Instant cancelledAt
- String returnedToWarehouseBy
- Instant returnedToWarehouseAt
- UUID returnWarehouseId
- LocalDate plannedDeliveryDate

### kg.chairx.delivery.api.DeliverySummary

Source: [backend/src/main/java/kg/chairx/delivery/api/DeliverySummary.java](../../../backend/src/main/java/kg/chairx/delivery/api/DeliverySummary.java)

- UUID id
- UUID saleId
- DeliveryStatus status
- String recipientName
- String cityRegion
- Instant createdAt
- LocalDate plannedDeliveryDate
- String saleNumber
- String recipientPhone
- String address
- FulfillmentType fulfillmentType
- Instant deliveredAt

### kg.chairx.delivery.api.FailDeliveryRequest

Source: [backend/src/main/java/kg/chairx/delivery/api/FailDeliveryRequest.java](../../../backend/src/main/java/kg/chairx/delivery/api/FailDeliveryRequest.java)

- @NotBlank @Size(max = 1000) String reason

### kg.chairx.delivery.api.ReturnDeliveryToWarehouseRequest

Source: [backend/src/main/java/kg/chairx/delivery/api/ReturnDeliveryToWarehouseRequest.java](../../../backend/src/main/java/kg/chairx/delivery/api/ReturnDeliveryToWarehouseRequest.java)

- @NotNull UUID warehouseId

### kg.chairx.delivery.api.UpdatePlannedDeliveryDateRequest

Source: [backend/src/main/java/kg/chairx/delivery/api/UpdatePlannedDeliveryDateRequest.java](../../../backend/src/main/java/kg/chairx/delivery/api/UpdatePlannedDeliveryDateRequest.java)

- LocalDate plannedDeliveryDate

### kg.chairx.delivery.domain.Delivery

Source: [backend/src/main/java/kg/chairx/delivery/domain/Delivery.java](../../../backend/src/main/java/kg/chairx/delivery/domain/Delivery.java)

- UUID id
- UUID saleId
- DeliveryStatus status
- String recipientName
- String recipientPhone
- String address
- String cityRegion
- BigDecimal deliveryCost
- String carrierName
- String trackingNumber
- String comment
- String createdBy
- Instant createdAt
- String dispatchedBy
- Instant dispatchedAt
- String deliveredBy
- Instant deliveredAt
- String failedBy
- Instant failedAt
- String failureReason
- String cancelledBy
- Instant cancelledAt
- String returnedToWarehouseBy
- Instant returnedToWarehouseAt
- UUID returnWarehouseId
- LocalDate plannedDeliveryDate

### kg.chairx.expense.api.CreateExpenseRequest

Source: [backend/src/main/java/kg/chairx/expense/api/CreateExpenseRequest.java](../../../backend/src/main/java/kg/chairx/expense/api/CreateExpenseRequest.java)

- @NotNull UUID idempotencyKey
- @NotNull ExpenseCategory category
- @NotNull @Positive BigDecimal amount
- @NotNull ExpensePaymentMethod paymentMethod
- @NotNull LocalDate expenseDate
- @Size(max = 1000) String comment

### kg.chairx.expense.api.ExpenseResponse

Source: [backend/src/main/java/kg/chairx/expense/api/ExpenseResponse.java](../../../backend/src/main/java/kg/chairx/expense/api/ExpenseResponse.java)

- UUID id
- ExpenseCategory category
- BigDecimal amount
- ExpensePaymentMethod paymentMethod
- LocalDate expenseDate
- String comment
- String createdBy
- Instant createdAt

### kg.chairx.expense.application.Accrual

Source: [backend/src/main/java/kg/chairx/expense/application/ExpenseAccrualService.java](../../../backend/src/main/java/kg/chairx/expense/application/ExpenseAccrualService.java)

- UUID id
- UUID idempotencyKey
- ExpenseCategory category
- String description
- BigDecimal amount
- LocalDate periodStart
- LocalDate periodEnd
- String createdBy

### kg.chairx.expense.application.Create

Source: [backend/src/main/java/kg/chairx/expense/application/ExpenseAccrualService.java](../../../backend/src/main/java/kg/chairx/expense/application/ExpenseAccrualService.java)

- UUID idempotencyKey
- ExpenseCategory category
- String description
- BigDecimal amount
- LocalDate periodStart
- LocalDate periodEnd

### kg.chairx.expense.application.PeriodPosition

Source: [backend/src/main/java/kg/chairx/expense/application/ExpensePeriodAllocation.java](../../../backend/src/main/java/kg/chairx/expense/application/ExpensePeriodAllocation.java)

- BigDecimal recognized
- BigDecimal paid
- BigDecimal outstanding
- BigDecimal prepaid

### kg.chairx.expense.application.ReportLine

Source: [backend/src/main/java/kg/chairx/expense/application/ExpenseAccrualService.java](../../../backend/src/main/java/kg/chairx/expense/application/ExpenseAccrualService.java)

- Accrual accrual
- BigDecimal recognized
- BigDecimal paid
- BigDecimal outstanding
- BigDecimal prepaid

### kg.chairx.expense.domain.Expense

Source: [backend/src/main/java/kg/chairx/expense/domain/Expense.java](../../../backend/src/main/java/kg/chairx/expense/domain/Expense.java)

- UUID id
- ExpenseCategory category
- BigDecimal amount
- ExpensePaymentMethod paymentMethod
- LocalDate expenseDate
- String comment
- String createdBy
- Instant createdAt

### kg.chairx.expense.web.LinkPaymentRequest

Source: [backend/src/main/java/kg/chairx/expense/web/ExpenseAccrualController.java](../../../backend/src/main/java/kg/chairx/expense/web/ExpenseAccrualController.java)

- java.util.UUID expenseId

### kg.chairx.finance.api.Account

Source: [backend/src/main/java/kg/chairx/finance/api/DailyClosingResponse.java](../../../backend/src/main/java/kg/chairx/finance/api/DailyClosingResponse.java)

- BigDecimal expected
- BigDecimal actual
- BigDecimal difference
- String note
- BigDecimal originalExpected
- boolean recalculated
- boolean requiresReview

### kg.chairx.finance.api.AdminBalanceAdjustmentRequest

Source: [backend/src/main/java/kg/chairx/finance/api/AdminBalanceAdjustmentRequest.java](../../../backend/src/main/java/kg/chairx/finance/api/AdminBalanceAdjustmentRequest.java)

- @NotNull FinanceAccount account
- @NotNull @DecimalMin("0") @Digits(integer=17, fraction=0) BigDecimal targetBalance
- @NotNull UUID idempotencyKey
- @NotBlank @Size(min=5, max=500) String reason

### kg.chairx.finance.api.CashFlowSummaryResponse

Source: [backend/src/main/java/kg/chairx/finance/api/CashFlowSummaryResponse.java](../../../backend/src/main/java/kg/chairx/finance/api/CashFlowSummaryResponse.java)

- Instant from
- Instant to
- BigDecimal payments
- BigDecimal paymentCorrections
- BigDecimal refunds
- BigDecimal operatingExpenses
- BigDecimal purchasePayments
- BigDecimal totalIn
- BigDecimal totalOut
- BigDecimal netCashFlow

### kg.chairx.finance.api.DailyClosingRequest

Source: [backend/src/main/java/kg/chairx/finance/api/DailyClosingRequest.java](../../../backend/src/main/java/kg/chairx/finance/api/DailyClosingRequest.java)

- BigDecimal actualCash
- String cashNote
- BigDecimal actualBank
- String bankNote

### kg.chairx.finance.api.DailyClosingResponse

Source: [backend/src/main/java/kg/chairx/finance/api/DailyClosingResponse.java](../../../backend/src/main/java/kg/chairx/finance/api/DailyClosingResponse.java)

- UUID id
- LocalDate businessDate
- Instant createdAt
- String createdBy
- Account cash
- Account bank
- long version

### kg.chairx.finance.api.DailyClosingUpdateRequest

Source: [backend/src/main/java/kg/chairx/finance/api/DailyClosingUpdateRequest.java](../../../backend/src/main/java/kg/chairx/finance/api/DailyClosingUpdateRequest.java)

- Long expectedVersion
- BigDecimal actualCash
- String cashNote
- BigDecimal actualBank
- String bankNote
- String reason

### kg.chairx.finance.api.FinanceAccountResponse

Source: [backend/src/main/java/kg/chairx/finance/api/FinanceAccountResponse.java](../../../backend/src/main/java/kg/chairx/finance/api/FinanceAccountResponse.java)

- String code
- BigDecimal balance
- boolean initialized

### kg.chairx.finance.api.FinanceTransferRequest

Source: [backend/src/main/java/kg/chairx/finance/api/FinanceTransferRequest.java](../../../backend/src/main/java/kg/chairx/finance/api/FinanceTransferRequest.java)

- @NotNull UUID transferId
- @NotNull FinanceAccount from
- @NotNull FinanceAccount to
- @NotNull @DecimalMin(value = "1") @Digits(integer = 17, fraction = 0) BigDecimal amount

### kg.chairx.finance.api.OpeningBalanceRequest

Source: [backend/src/main/java/kg/chairx/finance/api/OpeningBalanceRequest.java](../../../backend/src/main/java/kg/chairx/finance/api/OpeningBalanceRequest.java)

- @NotNull FinanceAccount account
- @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 0) BigDecimal amount

### kg.chairx.finance.api.TransferResponse

Source: [backend/src/main/java/kg/chairx/finance/api/FinanceTransferController.java](../../../backend/src/main/java/kg/chairx/finance/api/FinanceTransferController.java)

- UUID transferId
- FinanceAccount from
- FinanceAccount to
- BigDecimal amount

### kg.chairx.finance.api.UnlockRequest

Source: [backend/src/main/java/kg/chairx/finance/api/DailyClosingController.java](../../../backend/src/main/java/kg/chairx/finance/api/DailyClosingController.java)

- String reason

### kg.chairx.finance.application.AccessState

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingAccessService.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingAccessService.java)

- LocalDate businessDate
- boolean reportExists
- String reportStatus
- String accessStatus
- boolean canEdit
- boolean canUnlock
- Instant unlockedUntil
- long remainingSeconds

### kg.chairx.finance.application.AdjustmentAudit

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceAdminAdjustmentService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceAdminAdjustmentService.java)

- Result amounts
- String reason

### kg.chairx.finance.application.Adjustments

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceSaleAdjustmentsService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceSaleAdjustmentsService.java)

- BigDecimal returnedRevenue
- BigDecimal restoredFifoCost
- BigDecimal refundOnlyDiscounts
- boolean completeReturnCost
- boolean unrecognizedAdjustments
- long returnItemCount

### kg.chairx.finance.application.AuditEntry

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingService.java)

- String action
- String actor
- Instant at
- String before
- String after

### kg.chairx.finance.application.ClosingRow

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingService.java)

- UUID id
- LocalDate date
- Instant createdAt
- String actor
- long version
- String account
- DailyClosingResponse.Account value

### kg.chairx.finance.application.Collections

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java)

- BigDecimal cash
- BigDecimal bank

### kg.chairx.finance.application.CompanyClose

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeClosingManagerReportService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeClosingManagerReportService.java)

- String closer
- BigDecimal expected
- BigDecimal actual

### kg.chairx.finance.application.CompletedSale

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingSalesSnapshot.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingSalesSnapshot.java)

- UUID saleId
- String saleNumber
- String customerName
- String phone
- String products
- long quantity
- String address
- String fulfillmentType
- Instant completedAt
- BigDecimal total
- String paymentStatus
- String deliveryStatus

### kg.chairx.finance.application.Employee

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java)

- UUID id
- String username

### kg.chairx.finance.application.EmployeeClose

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeClosingManagerReportService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeClosingManagerReportService.java)

- UUID employeeId
- String username
- String displayName
- boolean closed
- BigDecimal expectedCash
- BigDecimal expectedBank
- BigDecimal actualCash
- BigDecimal actualBank
- BigDecimal actualTotal
- String totalNote

### kg.chairx.finance.application.ExpenseLine

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java)

- UUID expenseId
- String category
- BigDecimal amount
- String paymentMethod
- String comment
- Instant createdAt
- String createdBy

### kg.chairx.finance.application.ExpenseLine

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeClosingManagerReportService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeClosingManagerReportService.java)

- UUID id
- String createdBy
- String category
- String paymentMethod
- BigDecimal amount
- String comment

### kg.chairx.finance.application.Grant

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingAccessService.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingAccessService.java)

- UUID id
- LocalDate businessDate
- String unlockedBy
- Instant unlockedAt
- Instant expiresAt
- String reason
- Instant revokedAt
- String revokedBy

### kg.chairx.finance.application.Issue

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceReconciliationService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceReconciliationService.java)

- String documentSource
- UUID documentId
- String account
- String movementType
- BigDecimal expectedAmount
- BigDecimal postedAmount
- String problem

### kg.chairx.finance.application.ManagerReport

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeClosingManagerReportService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeClosingManagerReportService.java)

- LocalDate businessDate
- List<EmployeeClose> employees
- List<ExpenseLine> expenses
- BigDecimal expenseTotal
- BigDecimal cashExpenses
- BigDecimal bankExpenses
- BigDecimal totalExpectedCash
- BigDecimal totalExpectedBank
- BigDecimal totalActualCash
- BigDecimal totalActualBank
- int closedEmployees
- int totalEmployees
- BigDecimal expectedTotal
- BigDecimal actualTotal
- boolean operationalClosed
- String operationalCloser

### kg.chairx.finance.application.Preview

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingService.java)

- LocalDate businessDate
- BigDecimal expectedCash
- BigDecimal expectedBank

### kg.chairx.finance.application.Profitability

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceProfitabilityService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceProfitabilityService.java)

- LocalDate from
- LocalDate to
- BigDecimal grossRevenue
- BigDecimal verifiedFifoCost
- BigDecimal recognizedAccruals
- BigDecimal otherOperatingExpenses
- BigDecimal returnedRevenue
- BigDecimal refundOnlyDiscounts
- BigDecimal restoredFifoCost
- BigDecimal netRevenue
- boolean completeCost
- boolean netProfitAvailable
- BigDecimal grossProfit
- BigDecimal operatingProfit
- List<SaleCost> sales
- String caveat
- BigDecimal goodsRevenue
- BigDecimal deliveryRevenue

### kg.chairx.finance.application.RefundLine

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceSaleAdjustmentsService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceSaleAdjustmentsService.java)

- BigDecimal amount
- String status
- String fulfillment
- String delivery
- java.time.Instant recognizedAt
- java.time.Instant eventAt
- boolean potentialUnlinkedReturn

### kg.chairx.finance.application.Report

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java)

- LocalDate businessDate
- boolean closed
- boolean canClose
- BigDecimal expectedCash
- BigDecimal expectedBank
- BigDecimal actualCash
- BigDecimal actualBank
- String cashNote
- String bankNote
- long orders
- long chairs
- BigDecimal salesValue
- List<SaleLine> sales
- List<ExpenseLine> expenses
- BigDecimal expenseCash
- BigDecimal expenseBank
- boolean collectionsChangedAfterClose
- BigDecimal expectedTotal
- BigDecimal actualTotal
- String totalNote

### kg.chairx.finance.application.Request

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java)

- BigDecimal expectedCash
- BigDecimal expectedBank
- BigDecimal actualCash
- BigDecimal actualBank
- String cashNote
- String bankNote
- BigDecimal actualTotal
- String totalNote

### kg.chairx.finance.application.Result

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceAdminAdjustmentService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceAdminAdjustmentService.java)

- FinanceAccount account
- BigDecimal previousBalance
- BigDecimal newBalance
- BigDecimal delta

### kg.chairx.finance.application.Result

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingSalesSnapshot.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingSalesSnapshot.java)

- LocalDate businessDate
- boolean closed
- boolean snapshotAvailable
- boolean preliminary
- List<CompletedSale> items
- Totals totals
- Long lateCompletionCount

### kg.chairx.finance.application.ReturnLine

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceSaleAdjustmentsService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceSaleAdjustmentsService.java)

- long quantity
- BigDecimal price
- long restoredQuantity
- BigDecimal restoredCost
- String status
- String fulfillment
- String delivery
- java.time.Instant recognizedAt
- java.time.Instant eventAt

### kg.chairx.finance.application.SaleCost

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceProfitabilityService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceProfitabilityService.java)

- UUID saleId
- String saleNumber
- BigDecimal recognizedRevenue
- BigDecimal fifoCost
- boolean completeCost
- BigDecimal goodsRevenue
- BigDecimal deliveryRevenue

### kg.chairx.finance.application.SaleLine

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java)

- UUID saleId
- String saleNumber
- long quantity
- BigDecimal total
- Instant completedAt
- String products
- String phone
- String address
- String fulfillment
- String deliveryStatus
- String paymentStatus
- BigDecimal goodsTotal
- BigDecimal deliveryTotal

### kg.chairx.finance.application.Stored

Source: [backend/src/main/java/kg/chairx/finance/application/FinanceAdminAdjustmentService.java](../../../backend/src/main/java/kg/chairx/finance/application/FinanceAdminAdjustmentService.java)

- String account
- BigDecimal previousBalance
- BigDecimal newBalance
- BigDecimal delta
- String reason

### kg.chairx.finance.application.Stored

Source: [backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java](../../../backend/src/main/java/kg/chairx/finance/application/EmployeeDailyClosingService.java)

- UUID id
- BigDecimal cash
- BigDecimal bank
- BigDecimal actualCash
- BigDecimal actualBank
- String cashNote
- String bankNote
- BigDecimal actualTotal
- String totalNote

### kg.chairx.finance.application.Totals

Source: [backend/src/main/java/kg/chairx/finance/application/DailyClosingSalesSnapshot.java](../../../backend/src/main/java/kg/chairx/finance/application/DailyClosingSalesSnapshot.java)

- Long orders
- Long chairs
- BigDecimal value

### kg.chairx.finance.persistence.AccountSnapshot

Source: [backend/src/main/java/kg/chairx/finance/persistence/FinanceAccountRepository.java](../../../backend/src/main/java/kg/chairx/finance/persistence/FinanceAccountRepository.java)

- FinanceAccount account
- BigDecimal balance
- boolean initialized

### kg.chairx.finance.persistence.TransferRecord

Source: [backend/src/main/java/kg/chairx/finance/persistence/FinanceTransferRepository.java](../../../backend/src/main/java/kg/chairx/finance/persistence/FinanceTransferRepository.java)

- UUID id
- FinanceAccount from
- FinanceAccount to
- BigDecimal amount
- String actor

### kg.chairx.finance.web.Categories

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceExpenseCategoryController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceExpenseCategoryController.java)

- LocalDate from
- LocalDate to
- List<CategoryAmount> categories
- BigDecimal grandTotal

### kg.chairx.finance.web.CategoryAmount

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceExpenseCategoryController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceExpenseCategoryController.java)

- String category
- BigDecimal accrued
- BigDecimal direct
- BigDecimal total

### kg.chairx.finance.web.Line

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceLedgerController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceLedgerController.java)

- UUID id
- Instant postedAt
- LocalDate businessDate
- String account
- BigDecimal amount
- String type
- String sourceType
- UUID sourceId
- String createdBy
- String documentKind
- UUID documentId
- String documentLabel

### kg.chairx.finance.web.Margin

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceModelMarginsController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceModelMarginsController.java)

- UUID modelId
- String model
- UUID variantId
- String variation
- long quantity
- BigDecimal revenue
- BigDecimal knownFifoCost
- boolean costComplete
- BigDecimal grossProfit

### kg.chairx.finance.web.ModelValue

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceInventoryValuationController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceInventoryValuationController.java)

- UUID productId
- String model
- long quantity
- BigDecimal cost

### kg.chairx.finance.web.Month

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceAnalyticsController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceAnalyticsController.java)

- LocalDate from
- BigDecimal recognizedRevenue
- BigDecimal netRevenue
- BigDecimal returnedRevenue
- BigDecimal refundDiscounts
- BigDecimal fifoCost
- BigDecimal restoredFifoCost
- BigDecimal grossProfit
- BigDecimal operatingProfit
- boolean profitVerified
- BigDecimal accrualExpenses
- BigDecimal otherExpenses
- BigDecimal cashIn
- BigDecimal cashOut
- BigDecimal netCash

### kg.chairx.finance.web.MonthlyReport

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceAnalyticsController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceAnalyticsController.java)

- LocalDate from
- LocalDate to
- List<Month> months

### kg.chairx.finance.web.Obligations

Source: [backend/src/main/java/kg/chairx/finance/web/FinancePurchaseObligationsController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinancePurchaseObligationsController.java)

- BigDecimal totalOutstanding
- BigDecimal totalPrepaid
- List<PurchaseDebt> purchases

### kg.chairx.finance.web.Page

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceLedgerController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceLedgerController.java)

- List<Line> items
- long total
- int page
- int size

### kg.chairx.finance.web.PurchaseDebt

Source: [backend/src/main/java/kg/chairx/finance/web/FinancePurchaseObligationsController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinancePurchaseObligationsController.java)

- UUID purchaseId
- String supplierName
- java.time.Instant createdAt
- String status
- BigDecimal supplierTotal
- BigDecimal cargoTotal
- BigDecimal supplierPaid
- BigDecimal cargoPaid
- BigDecimal outstanding
- BigDecimal prepaid

### kg.chairx.finance.web.SourceDocument

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceSourceDocumentController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceSourceDocumentController.java)

- String kind
- UUID id
- String title
- Map<String
- String> fields

### kg.chairx.finance.web.Turnover

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceInventoryTurnoverController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceInventoryTurnoverController.java)

- LocalDate from
- LocalDate to
- BigDecimal openingCost
- BigDecimal closingCost
- BigDecimal averageCost
- BigDecimal netConsumedCost
- BigDecimal turns
- boolean available

### kg.chairx.finance.web.Valuation

Source: [backend/src/main/java/kg/chairx/finance/web/FinanceInventoryValuationController.java](../../../backend/src/main/java/kg/chairx/finance/web/FinanceInventoryValuationController.java)

- BigDecimal totalCost
- long quantity
- List<ModelValue> models

### kg.chairx.inventory.api.ChangeBlockedStock

Source: [backend/src/main/java/kg/chairx/inventory/api/ChangeBlockedStock.java](../../../backend/src/main/java/kg/chairx/inventory/api/ChangeBlockedStock.java)

- @NotNull(message = "Укажите склад") UUID warehouseId
- @NotNull(message = "Укажите вариант товара") UUID productVariantId
- @Positive(message = "Количество должно быть положительным") long quantity
- @NotNull(message = "Укажите источник операции") UUID sourceId
- @Size(min = 1, max = 200, message = "Имя инициатора должно содержать от 1 до 200 символов") String actor

### kg.chairx.inventory.api.ChangeReservedStock

Source: [backend/src/main/java/kg/chairx/inventory/api/ChangeReservedStock.java](../../../backend/src/main/java/kg/chairx/inventory/api/ChangeReservedStock.java)

- @NotNull UUID warehouseId
- @NotNull UUID productVariantId
- @Positive long quantity

### kg.chairx.inventory.api.CreateInventoryTransferRequest

Source: [backend/src/main/java/kg/chairx/inventory/api/CreateInventoryTransferRequest.java](../../../backend/src/main/java/kg/chairx/inventory/api/CreateInventoryTransferRequest.java)

- @NotNull UUID transferId
- @NotNull UUID sourceWarehouseId
- @NotNull UUID destinationWarehouseId
- @NotNull UUID variantId
- @Min(1) long quantity

### kg.chairx.inventory.api.InventoryBalancePage

Source: [backend/src/main/java/kg/chairx/inventory/api/InventoryBalancePage.java](../../../backend/src/main/java/kg/chairx/inventory/api/InventoryBalancePage.java)

- List<InventoryBalanceResponse> items
- int page
- int size
- long total

### kg.chairx.inventory.api.InventoryBalanceResponse

Source: [backend/src/main/java/kg/chairx/inventory/api/InventoryBalanceResponse.java](../../../backend/src/main/java/kg/chairx/inventory/api/InventoryBalanceResponse.java)

- UUID warehouseId
- UUID productVariantId
- long onHand
- long reserved
- long blocked
- long available

### kg.chairx.inventory.api.InventoryOverviewPage

Source: [backend/src/main/java/kg/chairx/inventory/api/InventoryOverviewPage.java](../../../backend/src/main/java/kg/chairx/inventory/api/InventoryOverviewPage.java)

- List<Line> items
- int page
- int size
- long total

### kg.chairx.inventory.api.InventoryTransferDetailsResponse

Source: [backend/src/main/java/kg/chairx/inventory/api/InventoryTransferDetailsResponse.java](../../../backend/src/main/java/kg/chairx/inventory/api/InventoryTransferDetailsResponse.java)

- UUID id
- UUID sourceWarehouseId
- UUID destinationWarehouseId
- UUID variantId
- long quantity
- String actor
- OffsetDateTime createdAt
- UUID outMovementId
- UUID inMovementId
- String model
- String variation

### kg.chairx.inventory.api.InventoryTransferPageResponse

Source: [backend/src/main/java/kg/chairx/inventory/api/InventoryTransferPageResponse.java](../../../backend/src/main/java/kg/chairx/inventory/api/InventoryTransferPageResponse.java)

- List<InventoryTransferDetailsResponse> items
- int page
- int size
- long totalElements
- int totalPages

### kg.chairx.inventory.api.InventoryTransferResponse

Source: [backend/src/main/java/kg/chairx/inventory/api/InventoryTransferResponse.java](../../../backend/src/main/java/kg/chairx/inventory/api/InventoryTransferResponse.java)

- UUID transferId
- UUID outMovementId
- UUID inMovementId
- long quantity

### kg.chairx.inventory.api.Line

Source: [backend/src/main/java/kg/chairx/inventory/api/InventoryOverviewPage.java](../../../backend/src/main/java/kg/chairx/inventory/api/InventoryOverviewPage.java)

- UUID productVariantId
- String model
- String variation
- long onHand
- long reserved
- long blocked
- long available
- List<WarehouseStock> warehouses

### kg.chairx.inventory.api.RecordStockMovement

Source: [backend/src/main/java/kg/chairx/inventory/api/RecordStockMovement.java](../../../backend/src/main/java/kg/chairx/inventory/api/RecordStockMovement.java)

- @NotNull(message = "Укажите ключ операции") UUID operationId
- @NotNull(message = "Укажите склад") UUID warehouseId
- @NotNull(message = "Укажите вариант товара") UUID productVariantId
- @NotNull(message = "Укажите тип движения") StockMovementType type
- @Positive(message = "Количество должно быть положительным") long quantity
- @NotNull(message = "Укажите тип источника") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}", message = "Некорректный тип источника") String sourceType
- @NotNull(message = "Укажите источник операции") UUID sourceId
- @Size(min = 1, max = 200, message = "Имя инициатора должно содержать от 1 до 200 символов") String actor

### kg.chairx.inventory.api.StockMovementPage

Source: [backend/src/main/java/kg/chairx/inventory/api/StockMovementPage.java](../../../backend/src/main/java/kg/chairx/inventory/api/StockMovementPage.java)

- List<StockMovement> items
- int page
- int size
- long totalElements

### kg.chairx.inventory.api.WarehouseStock

Source: [backend/src/main/java/kg/chairx/inventory/api/InventoryOverviewPage.java](../../../backend/src/main/java/kg/chairx/inventory/api/InventoryOverviewPage.java)

- UUID warehouseId
- String warehouseCode
- long onHand
- long reserved
- long blocked
- long available

### kg.chairx.inventory.api.WarehouseStockSummary

Source: [backend/src/main/java/kg/chairx/inventory/api/WarehouseStockSummary.java](../../../backend/src/main/java/kg/chairx/inventory/api/WarehouseStockSummary.java)

- UUID warehouseId
- long onHand
- long reserved
- long blocked
- long available
- long stockedVariants
- long lowStockVariants

### kg.chairx.inventory.application.Row

Source: [backend/src/main/java/kg/chairx/inventory/application/InventoryOverviewService.java](../../../backend/src/main/java/kg/chairx/inventory/application/InventoryOverviewService.java)

- UUID variantId
- String model
- String variation
- UUID warehouseId
- String warehouseCode
- long onHand
- long reserved
- long blocked

### kg.chairx.inventory.application.SavedTransfer

Source: [backend/src/main/java/kg/chairx/inventory/application/InventoryTransferService.java](../../../backend/src/main/java/kg/chairx/inventory/application/InventoryTransferService.java)

- UUID source
- UUID destination
- UUID variant
- long quantity
- String actor
- UUID outMovement
- UUID inMovement

### kg.chairx.inventory.application.TransferResult

Source: [backend/src/main/java/kg/chairx/inventory/application/InventoryTransferService.java](../../../backend/src/main/java/kg/chairx/inventory/application/InventoryTransferService.java)

- UUID transferId
- UUID outMovementId
- UUID inMovementId
- long quantity
- BigDecimal totalCost

### kg.chairx.inventory.cost.CostRestorationAllocation

Source: [backend/src/main/java/kg/chairx/inventory/cost/CostRestorationAllocation.java](../../../backend/src/main/java/kg/chairx/inventory/cost/CostRestorationAllocation.java)

- UUID id
- long quantity
- BigDecimal amount
- long returnedQuantity
- BigDecimal returnedAmount

### kg.chairx.inventory.cost.InventoryCostLayer

Source: [backend/src/main/java/kg/chairx/inventory/cost/InventoryCostLayer.java](../../../backend/src/main/java/kg/chairx/inventory/cost/InventoryCostLayer.java)

- UUID id
- UUID warehouseId
- UUID productVariantId
- UUID sourceMovementId
- long quantityReceived
- long quantityRemaining
- BigDecimal totalCost
- BigDecimal remainingCost
- Instant receivedAt

### kg.chairx.inventory.domain.InventoryBalance

Source: [backend/src/main/java/kg/chairx/inventory/domain/InventoryBalance.java](../../../backend/src/main/java/kg/chairx/inventory/domain/InventoryBalance.java)

- UUID warehouseId
- UUID productVariantId
- long onHand
- long reserved
- long blocked

### kg.chairx.inventory.domain.StockMovement

Source: [backend/src/main/java/kg/chairx/inventory/domain/StockMovement.java](../../../backend/src/main/java/kg/chairx/inventory/domain/StockMovement.java)

- UUID id
- UUID operationId
- UUID warehouseId
- UUID productVariantId
- StockMovementType type
- long quantity
- String sourceType
- UUID sourceId
- String actor
- Instant occurredAt

### kg.chairx.payment.api.CancelPaymentRequest

Source: [backend/src/main/java/kg/chairx/payment/api/CancelPaymentRequest.java](../../../backend/src/main/java/kg/chairx/payment/api/CancelPaymentRequest.java)

- @NotBlank @Size(max = 1000) String reason

### kg.chairx.payment.api.CreatePaymentRequest

Source: [backend/src/main/java/kg/chairx/payment/api/CreatePaymentRequest.java](../../../backend/src/main/java/kg/chairx/payment/api/CreatePaymentRequest.java)

- @NotNull UUID saleId
- @NotNull PaymentMethod method
- @Size(max = 200) String reference
- @Size(max = 1000) String comment
- PaymentChannel channel

### kg.chairx.payment.api.PaymentResponse

Source: [backend/src/main/java/kg/chairx/payment/api/PaymentResponse.java](../../../backend/src/main/java/kg/chairx/payment/api/PaymentResponse.java)

- UUID id
- UUID saleId
- BigDecimal amount
- PaymentMethod method
- PaymentStatus status
- String reference
- String comment
- String paidBy
- Instant paidAt
- String cancelledBy
- Instant cancelledAt
- String cancellationReason
- PaymentChannel channel

### kg.chairx.payment.domain.Payment

Source: [backend/src/main/java/kg/chairx/payment/domain/Payment.java](../../../backend/src/main/java/kg/chairx/payment/domain/Payment.java)

- UUID id
- UUID saleId
- BigDecimal amount
- PaymentMethod method
- PaymentStatus status
- String reference
- String comment
- String paidBy
- Instant paidAt
- String cancelledBy
- Instant cancelledAt
- String cancellationReason
- PaymentChannel channel

### kg.chairx.product.api.CreateProductRequest

Source: [backend/src/main/java/kg/chairx/product/api/CreateProductRequest.java](../../../backend/src/main/java/kg/chairx/product/api/CreateProductRequest.java)

- @NotBlank(message = "Укажите название") @Size(max = 200, message = "Название не должно превышать 200 символов") String name
- @Size(max = 4000, message = "Описание не должно превышать 4000 символов") String description
- @Size(max = 120, message = "Категория не должна превышать 120 символов") String category

### kg.chairx.product.api.CreateProductVariantRequest

Source: [backend/src/main/java/kg/chairx/product/api/CreateProductVariantRequest.java](../../../backend/src/main/java/kg/chairx/product/api/CreateProductVariantRequest.java)

- @NotBlank(message = "Укажите название") @Size(max = 200, message = "Название не должно превышать 200 символов") String name
- @Size(max = 100, message = "SKU не должен превышать 100 символов") String sku
- @Size(max = 100, message = "Цвет не должен превышать 100 символов") String color
- @NotNull(message = "Укажите рекомендуемую цену") @DecimalMin(value = "0", message = "Цена не может быть отрицательной") @Digits(integer = 17, fraction = 2, message = "Цена должна содержать не более 17 цифр до запятой и 2 после") BigDecimal recommendedSalePrice

### kg.chairx.product.api.ProductPageResponse

Source: [backend/src/main/java/kg/chairx/product/api/ProductPageResponse.java](../../../backend/src/main/java/kg/chairx/product/api/ProductPageResponse.java)

- List<ProductResponse> items
- int page
- int size
- long totalElements
- int totalPages

### kg.chairx.product.api.ProductResponse

Source: [backend/src/main/java/kg/chairx/product/api/ProductResponse.java](../../../backend/src/main/java/kg/chairx/product/api/ProductResponse.java)

- UUID id
- String name
- String description
- String category
- boolean active
- Instant createdAt
- Instant updatedAt

### kg.chairx.product.api.ProductVariantPageResponse

Source: [backend/src/main/java/kg/chairx/product/api/ProductVariantPageResponse.java](../../../backend/src/main/java/kg/chairx/product/api/ProductVariantPageResponse.java)

- List<ProductVariantResponse> items
- int page
- int size
- long totalElements
- int totalPages

### kg.chairx.product.api.ProductVariantResponse

Source: [backend/src/main/java/kg/chairx/product/api/ProductVariantResponse.java](../../../backend/src/main/java/kg/chairx/product/api/ProductVariantResponse.java)

- UUID id
- UUID productId
- String name
- String sku
- String color
- BigDecimal recommendedSalePrice
- boolean active
- Instant createdAt
- Instant updatedAt

### kg.chairx.product.api.UpdateProductRequest

Source: [backend/src/main/java/kg/chairx/product/api/UpdateProductRequest.java](../../../backend/src/main/java/kg/chairx/product/api/UpdateProductRequest.java)

- @NotBlank(message = "Укажите название") @Size(max = 200, message = "Название не должно превышать 200 символов") String name
- @Size(max = 4000, message = "Описание не должно превышать 4000 символов") String description
- @Size(max = 120, message = "Категория не должна превышать 120 символов") String category

### kg.chairx.product.api.UpdateProductVariantRequest

Source: [backend/src/main/java/kg/chairx/product/api/UpdateProductVariantRequest.java](../../../backend/src/main/java/kg/chairx/product/api/UpdateProductVariantRequest.java)

- @NotBlank(message = "Укажите название") @Size(max = 200, message = "Название не должно превышать 200 символов") String name
- @Size(max = 100, message = "SKU не должен превышать 100 символов") String sku
- @Size(max = 100, message = "Цвет не должен превышать 100 символов") String color
- @NotNull(message = "Укажите рекомендуемую цену") @DecimalMin(value = "0", message = "Цена не может быть отрицательной") @Digits(integer = 17, fraction = 2, message = "Цена должна содержать не более 17 цифр до запятой и 2 после") BigDecimal recommendedSalePrice

### kg.chairx.purchase.api.CreatePurchasePaymentRequest

Source: [backend/src/main/java/kg/chairx/purchase/api/CreatePurchasePaymentRequest.java](../../../backend/src/main/java/kg/chairx/purchase/api/CreatePurchasePaymentRequest.java)

- @NotNull UUID idempotencyKey
- @NotNull PurchasePaymentKind paymentKind
- @NotNull FinanceAccount account
- @NotNull @DecimalMin("1") @Digits(integer=17, fraction=0) BigDecimal amount
- @Size(max=200) String reference
- @Size(max=1000) String comment

### kg.chairx.purchase.api.CreatePurchaseReceiptRequest

Source: [backend/src/main/java/kg/chairx/purchase/api/CreatePurchaseReceiptRequest.java](../../../backend/src/main/java/kg/chairx/purchase/api/CreatePurchaseReceiptRequest.java)

- @NotNull(message = "Укажите ключ поступления") UUID idempotencyKey
- @NotNull(message = "Укажите склад") UUID warehouseId
- @NotEmpty(message = "Добавьте позиции поступления") @Size(max = 1000, message = "Допустимо не более 1000 позиций") List<@NotNull(message = "Позиция не может быть пустой") @Valid ReceiptItemRequest> items
- @Size(max = 4000, message = "Комментарий не должен превышать 4000 символов") String comment

### kg.chairx.purchase.api.CreatePurchaseRequest

Source: [backend/src/main/java/kg/chairx/purchase/api/CreatePurchaseRequest.java](../../../backend/src/main/java/kg/chairx/purchase/api/CreatePurchaseRequest.java)

- @NotNull(message = "Укажите поставщика") UUID supplierId
- @NotEmpty(message = "Добавьте позиции закупки") @Size(max = 1000, message = "Допустимо не более 1000 позиций") List<@NotNull(message = "Позиция не может быть пустой") @Valid PurchaseItemRequest> items
- @DecimalMin(value = "0", message = "Карго не может быть отрицательным") @Digits(integer = 17, fraction = 2, message = "Карго: не более 17 целых и 2 дробных знаков") BigDecimal cargoCost
- @Size(max = 4000, message = "Комментарий не должен превышать 4000 символов") String comment

### kg.chairx.purchase.api.Line

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchaseOverviewPage.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchaseOverviewPage.java)

- UUID id
- UUID supplierId
- String supplierName
- PurchaseStatus status
- Instant createdAt
- long itemCount
- long orderedQuantity
- long receivedQuantity
- long remainingQuantity
- BigDecimal goodsCost
- BigDecimal cargoCost
- BigDecimal totalCost
- Instant lastReceiptAt

### kg.chairx.purchase.api.PurchaseItemRequest

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchaseItemRequest.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchaseItemRequest.java)

- @NotNull(message = "Укажите вариант товара") UUID productVariantId
- @Positive(message = "Заказанное количество должно быть положительным") long orderedQuantity
- @NotNull(message = "Укажите закупочную цену") @DecimalMin(value = "0", message = "Цена не может быть отрицательной") @Digits(integer = 17, fraction = 2, message = "Цена: не более 17 целых и 2 дробных знаков") BigDecimal purchaseUnitCost

### kg.chairx.purchase.api.PurchaseItemResponse

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchaseItemResponse.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchaseItemResponse.java)

- UUID id
- UUID productVariantId
- int lineNumber
- long orderedQuantity
- long receivedQuantity
- BigDecimal purchaseUnitCost
- BigDecimal allocatedCargoCost
- BigDecimal finalUnitCost
- UUID productId
- String modelName
- String variantName
- String sku

### kg.chairx.purchase.api.PurchaseOverviewPage

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchaseOverviewPage.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchaseOverviewPage.java)

- List<Line> items
- int page
- int size
- long total

### kg.chairx.purchase.api.PurchasePageResponse

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchasePageResponse.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchasePageResponse.java)

- List<PurchaseSummary> items
- int page
- int size
- long totalElements

### kg.chairx.purchase.api.PurchasePaymentResponse

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchasePaymentResponse.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchasePaymentResponse.java)

- UUID id
- UUID purchaseId
- PurchasePaymentKind paymentKind
- FinanceAccount account
- BigDecimal amount
- UUID idempotencyKey
- String reference
- String comment
- String createdBy
- Instant createdAt

### kg.chairx.purchase.api.PurchasePaymentSummaryResponse

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchasePaymentSummaryResponse.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchasePaymentSummaryResponse.java)

- UUID purchaseId
- BigDecimal supplierTotal
- BigDecimal cargoTotal
- BigDecimal paidSupplier
- BigDecimal paidCargo
- BigDecimal remainingSupplier
- BigDecimal remainingCargo
- List<PurchasePaymentResponse> payments

### kg.chairx.purchase.api.PurchaseReceiptItemResponse

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchaseReceiptItemResponse.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchaseReceiptItemResponse.java)

- UUID id
- UUID purchaseItemId
- UUID productVariantId
- long quantity
- BigDecimal allocatedCargoCost
- BigDecimal totalCost

### kg.chairx.purchase.api.PurchaseReceiptPageResponse

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchaseReceiptPageResponse.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchaseReceiptPageResponse.java)

- List<PurchaseReceiptSummary> items
- int page
- int size
- long totalElements

### kg.chairx.purchase.api.PurchaseReceiptResponse

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchaseReceiptResponse.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchaseReceiptResponse.java)

- UUID id
- UUID purchaseId
- UUID warehouseId
- UUID idempotencyKey
- Instant postedAt
- String createdBy
- String comment
- List<PurchaseReceiptItemResponse> items

### kg.chairx.purchase.api.PurchaseReceiptSummary

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchaseReceiptSummary.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchaseReceiptSummary.java)

- UUID id
- UUID warehouseId
- Instant postedAt
- String createdBy

### kg.chairx.purchase.api.PurchaseResponse

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchaseResponse.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchaseResponse.java)

- UUID id
- UUID supplierId
- PurchaseStatus status
- BigDecimal cargoCost
- String cargoAllocationMethod
- Instant costsLockedAt
- Instant confirmedAt
- String comment
- String createdBy
- Instant createdAt
- Instant updatedAt
- List<PurchaseItemResponse> items

### kg.chairx.purchase.api.PurchaseSummary

Source: [backend/src/main/java/kg/chairx/purchase/api/PurchaseSummary.java](../../../backend/src/main/java/kg/chairx/purchase/api/PurchaseSummary.java)

- UUID id
- UUID supplierId
- PurchaseStatus status
- BigDecimal cargoCost
- Instant createdAt

### kg.chairx.purchase.api.ReceiptItemRequest

Source: [backend/src/main/java/kg/chairx/purchase/api/ReceiptItemRequest.java](../../../backend/src/main/java/kg/chairx/purchase/api/ReceiptItemRequest.java)

- @NotNull(message = "Укажите позицию закупки") UUID purchaseItemId
- @Positive(message = "Количество поступления должно быть положительным") long quantity

### kg.chairx.purchase.api.ReceiptPosting

Source: [backend/src/main/java/kg/chairx/purchase/api/ReceiptPosting.java](../../../backend/src/main/java/kg/chairx/purchase/api/ReceiptPosting.java)

- PurchaseReceiptResponse receipt
- boolean replayed

### kg.chairx.purchase.api.SetCargoCostRequest

Source: [backend/src/main/java/kg/chairx/purchase/api/SetCargoCostRequest.java](../../../backend/src/main/java/kg/chairx/purchase/api/SetCargoCostRequest.java)

- @NotNull(message = "Укажите стоимость карго") @DecimalMin(value = "0", message = "Карго не может быть отрицательным") @Digits(integer = 17, fraction = 2, message = "Карго: не более 17 целых и 2 дробных знаков") BigDecimal cargoCost

### kg.chairx.purchase.api.UpdatePurchaseRequest

Source: [backend/src/main/java/kg/chairx/purchase/api/UpdatePurchaseRequest.java](../../../backend/src/main/java/kg/chairx/purchase/api/UpdatePurchaseRequest.java)

- @NotNull(message = "Укажите поставщика") UUID supplierId
- @NotEmpty(message = "Добавьте позиции закупки") @Size(max = 1000, message = "Допустимо не более 1000 позиций") List<@NotNull(message = "Позиция не может быть пустой") @Valid PurchaseItemRequest> items
- @DecimalMin(value = "0", message = "Карго не может быть отрицательным") @Digits(integer = 17, fraction = 2, message = "Карго: не более 17 целых и 2 дробных знаков") BigDecimal cargoCost
- @Size(max = 4000, message = "Комментарий не должен превышать 4000 символов") String comment

### kg.chairx.purchase.api.WarehouseReceiptItemResponse

Source: [backend/src/main/java/kg/chairx/purchase/api/WarehouseReceiptItemResponse.java](../../../backend/src/main/java/kg/chairx/purchase/api/WarehouseReceiptItemResponse.java)

- UUID id
- UUID purchaseItemId
- UUID productVariantId
- long quantity

### kg.chairx.purchase.api.WarehouseReceiptResponse

Source: [backend/src/main/java/kg/chairx/purchase/api/WarehouseReceiptResponse.java](../../../backend/src/main/java/kg/chairx/purchase/api/WarehouseReceiptResponse.java)

- UUID id
- UUID purchaseId
- UUID warehouseId
- UUID idempotencyKey
- Instant postedAt
- String createdBy
- String comment
- List<WarehouseReceiptItemResponse> items

### kg.chairx.purchase.application.ReceiptContent

Source: [backend/src/main/java/kg/chairx/purchase/application/PurchaseService.java](../../../backend/src/main/java/kg/chairx/purchase/application/PurchaseService.java)

- UUID warehouseId
- List<ReceiptItemRequest> items
- String comment

### kg.chairx.purchase.domain.Purchase

Source: [backend/src/main/java/kg/chairx/purchase/domain/Purchase.java](../../../backend/src/main/java/kg/chairx/purchase/domain/Purchase.java)

- UUID id
- UUID supplierId
- PurchaseStatus status
- BigDecimal cargoCost
- String cargoAllocationMethod
- Instant costsLockedAt
- Instant confirmedAt
- String comment
- String createdBy
- Instant createdAt
- Instant updatedAt

### kg.chairx.purchase.domain.PurchaseItem

Source: [backend/src/main/java/kg/chairx/purchase/domain/PurchaseItem.java](../../../backend/src/main/java/kg/chairx/purchase/domain/PurchaseItem.java)

- UUID id
- UUID purchaseId
- UUID productVariantId
- int lineNumber
- long orderedQuantity
- long receivedQuantity
- BigDecimal purchaseUnitCost
- BigDecimal allocatedCargoCost
- BigDecimal finalUnitCost

### kg.chairx.purchase.domain.PurchasePayment

Source: [backend/src/main/java/kg/chairx/purchase/domain/PurchasePayment.java](../../../backend/src/main/java/kg/chairx/purchase/domain/PurchasePayment.java)

- UUID id
- UUID purchaseId
- PurchasePaymentKind paymentKind
- FinanceAccount account
- BigDecimal amount
- UUID idempotencyKey
- String requestFingerprint
- String reference
- String comment
- String createdBy
- Instant createdAt

### kg.chairx.purchase.domain.PurchaseReceipt

Source: [backend/src/main/java/kg/chairx/purchase/domain/PurchaseReceipt.java](../../../backend/src/main/java/kg/chairx/purchase/domain/PurchaseReceipt.java)

- UUID id
- UUID purchaseId
- UUID warehouseId
- UUID idempotencyKey
- String requestFingerprint
- Instant postedAt
- String createdBy
- String comment
- List<PurchaseReceiptItem> items

### kg.chairx.purchase.domain.PurchaseReceiptItem

Source: [backend/src/main/java/kg/chairx/purchase/domain/PurchaseReceiptItem.java](../../../backend/src/main/java/kg/chairx/purchase/domain/PurchaseReceiptItem.java)

- UUID id
- UUID purchaseItemId
- UUID productVariantId
- long quantity
- BigDecimal allocatedCargoCost
- BigDecimal totalCost

### kg.chairx.purchase.persistence.CatalogLine

Source: [backend/src/main/java/kg/chairx/purchase/persistence/PurchaseRepository.java](../../../backend/src/main/java/kg/chairx/purchase/persistence/PurchaseRepository.java)

- PurchaseItem item
- UUID productId
- String modelName
- String variantName
- String sku

### kg.chairx.purchase.web.Line

Source: [backend/src/main/java/kg/chairx/purchase/web/PurchaseReceivingController.java](../../../backend/src/main/java/kg/chairx/purchase/web/PurchaseReceivingController.java)

- UUID purchaseId
- PurchaseStatus status
- Instant createdAt
- UUID purchaseItemId
- UUID productVariantId
- String model
- String variant
- long orderedQuantity
- long receivedQuantity
- long remainingQuantity

### kg.chairx.purchase.web.Summary

Source: [backend/src/main/java/kg/chairx/purchase/web/PurchaseReceivingController.java](../../../backend/src/main/java/kg/chairx/purchase/web/PurchaseReceivingController.java)

- UUID purchaseId
- PurchaseStatus status
- List<Line> items

### kg.chairx.purchase.web.Worklist

Source: [backend/src/main/java/kg/chairx/purchase/web/PurchaseReceivingController.java](../../../backend/src/main/java/kg/chairx/purchase/web/PurchaseReceivingController.java)

- List<Line> items
- int page
- int size
- long total

### kg.chairx.refund.api.CreateRefundRequest

Source: [backend/src/main/java/kg/chairx/refund/api/CreateRefundRequest.java](../../../backend/src/main/java/kg/chairx/refund/api/CreateRefundRequest.java)

- @NotNull(message = "Укажите продажу") UUID saleId
- UUID returnId
- @NotNull(message = "Укажите сумму возврата") @DecimalMin( value = "1", message = "Сумма возврата должна быть положительной" ) @Digits( integer = 17, fraction = 0, message = "Сумма возврата должна быть указана в целых сомах" ) BigDecimal amount
- @NotNull(message = "Укажите способ возврата") RefundMethod method
- @NotBlank(message = "Укажите причину возврата") @Size( max = 500, message = "Причина возврата не должна превышать 500 символов" ) String reason
- @Size( max = 200, message = "Ссылка на операцию не должна превышать 200 символов" ) String reference
- @Size( max = 1000, message = "Комментарий не должен превышать 1000 символов" ) String comment
- @NotNull(message = "Укажите ключ операции") UUID idempotencyKey

### kg.chairx.refund.api.RefundResponse

Source: [backend/src/main/java/kg/chairx/refund/api/RefundResponse.java](../../../backend/src/main/java/kg/chairx/refund/api/RefundResponse.java)

- UUID id
- UUID saleId
- UUID returnId
- BigDecimal amount
- RefundMethod method
- String reason
- String reference
- String comment
- String refundedBy
- Instant refundedAt

### kg.chairx.refund.domain.Refund

Source: [backend/src/main/java/kg/chairx/refund/domain/Refund.java](../../../backend/src/main/java/kg/chairx/refund/domain/Refund.java)

- UUID id
- UUID saleId
- UUID returnId
- BigDecimal amount
- RefundMethod method
- String reason
- String reference
- String comment
- String refundedBy
- Instant refundedAt

### kg.chairx.returning.api.CreateReturnItemRequest

Source: [backend/src/main/java/kg/chairx/returning/api/CreateReturnItemRequest.java](../../../backend/src/main/java/kg/chairx/returning/api/CreateReturnItemRequest.java)

- @NotNull(message = "Укажите позицию продажи") UUID saleItemId
- @Positive(message = "Количество возврата должно быть положительным") long quantity
- @NotNull(message = "Укажите состояние возвращённого товара") ReturnCondition condition

### kg.chairx.returning.api.CreateReturnRequest

Source: [backend/src/main/java/kg/chairx/returning/api/CreateReturnRequest.java](../../../backend/src/main/java/kg/chairx/returning/api/CreateReturnRequest.java)

- @NotNull(message = "Укажите продажу") UUID saleId
- @NotNull(message = "Укажите склад возврата") UUID warehouseId
- @NotNull(message = "Укажите ключ операции") UUID idempotencyKey
- @NotEmpty(message = "Добавьте хотя бы одну позицию возврата") List<@Valid CreateReturnItemRequest> items
- @NotNull(message = "Укажите причину возврата") @Size(min = 1, max = 500, message = "Причина возврата должна содержать от 1 до 500 символов") String reason
- @Size(max = 1000, message = "Комментарий не должен превышать 1000 символов") String comment

### kg.chairx.returning.api.ReturnItemResponse

Source: [backend/src/main/java/kg/chairx/returning/api/ReturnItemResponse.java](../../../backend/src/main/java/kg/chairx/returning/api/ReturnItemResponse.java)

- UUID id
- UUID saleItemId
- long quantity
- ReturnCondition condition

### kg.chairx.returning.api.ReturnPageResponse

Source: [backend/src/main/java/kg/chairx/returning/api/ReturnPageResponse.java](../../../backend/src/main/java/kg/chairx/returning/api/ReturnPageResponse.java)

- List<ReturnSummary> items
- int page
- int size
- long total

### kg.chairx.returning.api.ReturnResponse

Source: [backend/src/main/java/kg/chairx/returning/api/ReturnResponse.java](../../../backend/src/main/java/kg/chairx/returning/api/ReturnResponse.java)

- UUID id
- UUID saleId
- UUID warehouseId
- List<ReturnItemResponse> items
- String reason
- String comment
- String createdBy
- Instant createdAt

### kg.chairx.returning.api.ReturnSummary

Source: [backend/src/main/java/kg/chairx/returning/api/ReturnSummary.java](../../../backend/src/main/java/kg/chairx/returning/api/ReturnSummary.java)

- UUID id
- UUID saleId
- UUID warehouseId
- Instant createdAt
- String reason
- String createdBy
- String saleNumber

### kg.chairx.returning.domain.Return

Source: [backend/src/main/java/kg/chairx/returning/domain/Return.java](../../../backend/src/main/java/kg/chairx/returning/domain/Return.java)

- UUID id
- UUID saleId
- UUID warehouseId
- List<ReturnItem> items
- String reason
- String comment
- String createdBy
- Instant createdAt

### kg.chairx.returning.domain.ReturnItem

Source: [backend/src/main/java/kg/chairx/returning/domain/ReturnItem.java](../../../backend/src/main/java/kg/chairx/returning/domain/ReturnItem.java)

- UUID id
- UUID returnId
- UUID saleItemId
- long quantity
- ReturnCondition condition

### kg.chairx.sale.api.CancelSaleRequest

Source: [backend/src/main/java/kg/chairx/sale/api/CancelSaleRequest.java](../../../backend/src/main/java/kg/chairx/sale/api/CancelSaleRequest.java)

- @NotNull SaleCancellationReason reason
- @Size(max = 1000) String comment

### kg.chairx.sale.api.CreateDraftSaleRequest

Source: [backend/src/main/java/kg/chairx/sale/api/CreateDraftSaleRequest.java](../../../backend/src/main/java/kg/chairx/sale/api/CreateDraftSaleRequest.java)

- @NotNull UUID idempotencyKey
- UUID customerId
- FulfillmentType fulfillmentType
- List<@NotNull @Valid CreateSaleItemRequest> items
- @Size(max = 2000) String comment

### kg.chairx.sale.api.CreateSaleCommentRequest

Source: [backend/src/main/java/kg/chairx/sale/api/CreateSaleCommentRequest.java](../../../backend/src/main/java/kg/chairx/sale/api/CreateSaleCommentRequest.java)

- @NotNull UUID idempotencyKey
- @NotBlank @Size(max=2000) String text

### kg.chairx.sale.api.CreateSaleItemRequest

Source: [backend/src/main/java/kg/chairx/sale/api/CreateSaleItemRequest.java](../../../backend/src/main/java/kg/chairx/sale/api/CreateSaleItemRequest.java)

- @NotNull UUID productVariantId
- @NotNull UUID warehouseId
- @Positive long quantity
- @NotNull @DecimalMin(value = "0") @Digits(integer = 19, fraction = 0) BigDecimal unitSalePrice

### kg.chairx.sale.api.CreateSaleRequest

Source: [backend/src/main/java/kg/chairx/sale/api/CreateSaleRequest.java](../../../backend/src/main/java/kg/chairx/sale/api/CreateSaleRequest.java)

- @NotNull UUID idempotencyKey
- UUID customerId
- @NotNull FulfillmentType fulfillmentType
- @NotEmpty List<@Valid CreateSaleItemRequest> items

### kg.chairx.sale.api.CreateSaleWithDeliveryRequest

Source: [backend/src/main/java/kg/chairx/sale/api/CreateSaleWithDeliveryRequest.java](../../../backend/src/main/java/kg/chairx/sale/api/CreateSaleWithDeliveryRequest.java)

- @NotNull @Valid CreateDraftSaleRequest sale
- @NotNull @Valid DeliveryDetails delivery

### kg.chairx.sale.api.DeliveryDetails

Source: [backend/src/main/java/kg/chairx/sale/api/CreateSaleWithDeliveryRequest.java](../../../backend/src/main/java/kg/chairx/sale/api/CreateSaleWithDeliveryRequest.java)

- @Size(max=200) String recipientName
- @NotBlank @Size(max=50) String recipientPhone
- @NotBlank @Size(max=500) String address
- @Size(max=200) String cityRegion
- @NotNull @DecimalMin("0") @Digits(integer=19,fraction=0) BigDecimal deliveryCost
- @Size(max=200) String carrierName
- @Size(max=200) String trackingNumber
- @Size(max=2000) String comment
- LocalDate plannedDeliveryDate

### kg.chairx.sale.api.SaleCancellationDetails

Source: [backend/src/main/java/kg/chairx/sale/api/SaleCancellationDetails.java](../../../backend/src/main/java/kg/chairx/sale/api/SaleCancellationDetails.java)

- SaleCancellationReason reason
- String comment
- String cancelledBy
- Instant cancelledAt

### kg.chairx.sale.api.SaleCommentResponse

Source: [backend/src/main/java/kg/chairx/sale/api/SaleCommentResponse.java](../../../backend/src/main/java/kg/chairx/sale/api/SaleCommentResponse.java)

- UUID id
- UUID saleId
- String author
- String text
- Instant createdAt

### kg.chairx.sale.api.SaleCommentsPage

Source: [backend/src/main/java/kg/chairx/sale/api/SaleCommentsPage.java](../../../backend/src/main/java/kg/chairx/sale/api/SaleCommentsPage.java)

- List<SaleCommentResponse> items
- int page
- int size
- long total

### kg.chairx.sale.api.SaleHistoryEvent

Source: [backend/src/main/java/kg/chairx/sale/api/SaleHistoryEvent.java](../../../backend/src/main/java/kg/chairx/sale/api/SaleHistoryEvent.java)

- String action
- String actor
- Instant occurredAt

### kg.chairx.sale.api.SaleHistoryPage

Source: [backend/src/main/java/kg/chairx/sale/api/SaleHistoryPage.java](../../../backend/src/main/java/kg/chairx/sale/api/SaleHistoryPage.java)

- List<SaleHistoryEvent> items
- int page
- int size
- long total

### kg.chairx.sale.api.SaleItemResponse

Source: [backend/src/main/java/kg/chairx/sale/api/SaleItemResponse.java](../../../backend/src/main/java/kg/chairx/sale/api/SaleItemResponse.java)

- UUID id
- UUID productVariantId
- UUID warehouseId
- long quantity
- BigDecimal unitSalePrice
- BigDecimal total

### kg.chairx.sale.api.SalePageResponse

Source: [backend/src/main/java/kg/chairx/sale/api/SalePageResponse.java](../../../backend/src/main/java/kg/chairx/sale/api/SalePageResponse.java)

- List<SaleSummary> items
- int page
- int size
- long total

### kg.chairx.sale.api.SaleResponse

Source: [backend/src/main/java/kg/chairx/sale/api/SaleResponse.java](../../../backend/src/main/java/kg/chairx/sale/api/SaleResponse.java)

- UUID id
- String saleNumber
- UUID customerId
- FulfillmentType fulfillmentType
- SaleStatus status
- List<SaleItemResponse> items
- BigDecimal total
- String createdBy
- Instant createdAt
- String fulfilledBy
- Instant fulfilledAt
- String cancelledBy
- Instant cancelledAt
- String comment

### kg.chairx.sale.api.SaleStatistics

Source: [backend/src/main/java/kg/chairx/sale/api/SaleStatistics.java](../../../backend/src/main/java/kg/chairx/sale/api/SaleStatistics.java)

- long orders30Days
- long fulfilled30Days
- long inWork30Days
- BigDecimal averageCheck30Days

### kg.chairx.sale.api.SaleSummary

Source: [backend/src/main/java/kg/chairx/sale/api/SaleSummary.java](../../../backend/src/main/java/kg/chairx/sale/api/SaleSummary.java)

- UUID id
- String saleNumber
- Instant createdAt
- UUID customerId
- String customerName
- BigDecimal total
- SaleStatus status
- FulfillmentType fulfillmentType
- long quantity
- String products
- String paymentStatus
- DeliveryStatus deliveryStatus
- LocalDate plannedDeliveryDate
- String customerPhone
- String deliveryAddress
- long returnedQuantity

### kg.chairx.sale.api.UpdateDraftSaleRequest

Source: [backend/src/main/java/kg/chairx/sale/api/UpdateDraftSaleRequest.java](../../../backend/src/main/java/kg/chairx/sale/api/UpdateDraftSaleRequest.java)

- java.util.UUID customerId
- FulfillmentType fulfillmentType
- List<@NotNull @Valid CreateSaleItemRequest> items
- @Size(max = 2000) String comment

### kg.chairx.sale.application.Candidate

Source: [backend/src/main/java/kg/chairx/sale/application/SaleWithDeliveryService.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleWithDeliveryService.java)

- UUID id
- boolean active

### kg.chairx.sale.application.DraftContent

Source: [backend/src/main/java/kg/chairx/sale/application/SaleRequestFingerprint.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleRequestFingerprint.java)

- UUID customerId
- FulfillmentType fulfillmentType
- List<kg.chairx.sale.api.CreateSaleItemRequest> items
- String comment

### kg.chairx.sale.application.FingerprintSaleItem

Source: [backend/src/main/java/kg/chairx/sale/application/SaleRequestFingerprint.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleRequestFingerprint.java)

- UUID productVariantId
- UUID warehouseId
- long quantity
- BigDecimal unitSalePrice

### kg.chairx.sale.application.ItemKey

Source: [backend/src/main/java/kg/chairx/sale/application/SaleService.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleService.java)

- UUID productVariantId
- UUID warehouseId

### kg.chairx.sale.application.SaleContent

Source: [backend/src/main/java/kg/chairx/sale/application/SaleRequestFingerprint.java](../../../backend/src/main/java/kg/chairx/sale/application/SaleRequestFingerprint.java)

- UUID customerId
- FulfillmentType fulfillmentType
- List<FingerprintSaleItem> items

### kg.chairx.sale.domain.Sale

Source: [backend/src/main/java/kg/chairx/sale/domain/Sale.java](../../../backend/src/main/java/kg/chairx/sale/domain/Sale.java)

- UUID id
- String saleNumber
- UUID customerId
- FulfillmentType fulfillmentType
- SaleStatus status
- List<SaleItem> items
- String createdBy
- Instant createdAt
- String fulfilledBy
- Instant fulfilledAt
- String cancelledBy
- Instant cancelledAt
- String comment

### kg.chairx.sale.domain.SaleItem

Source: [backend/src/main/java/kg/chairx/sale/domain/SaleItem.java](../../../backend/src/main/java/kg/chairx/sale/domain/SaleItem.java)

- UUID id
- UUID saleId
- UUID productVariantId
- UUID warehouseId
- long quantity
- BigDecimal unitSalePrice

### kg.chairx.security.api.AssignRoleRequest

Source: [backend/src/main/java/kg/chairx/security/api/AdminUserController.java](../../../backend/src/main/java/kg/chairx/security/api/AdminUserController.java)

- UUID roleId

### kg.chairx.security.api.CreateUserRequest

Source: [backend/src/main/java/kg/chairx/security/api/AdminUserController.java](../../../backend/src/main/java/kg/chairx/security/api/AdminUserController.java)

- String username
- String password
- String displayName

### kg.chairx.security.api.CurrentUser

Source: [backend/src/main/java/kg/chairx/security/api/CurrentUserController.java](../../../backend/src/main/java/kg/chairx/security/api/CurrentUserController.java)

- UUID id
- String username
- String displayName
- List<String> roles
- List<String> permissions

### kg.chairx.security.api.ResetPasswordRequest

Source: [backend/src/main/java/kg/chairx/security/api/AdminUserController.java](../../../backend/src/main/java/kg/chairx/security/api/AdminUserController.java)

- String newPassword

### kg.chairx.security.api.RoleResponse

Source: [backend/src/main/java/kg/chairx/security/api/AdminUserController.java](../../../backend/src/main/java/kg/chairx/security/api/AdminUserController.java)

- UUID id
- String code
- String name

### kg.chairx.security.api.UserResponse

Source: [backend/src/main/java/kg/chairx/security/api/AdminUserController.java](../../../backend/src/main/java/kg/chairx/security/api/AdminUserController.java)

- UUID id
- String username
- String displayName
- boolean active
- List<String> roles

### kg.chairx.security.application.AuditLine

Source: [backend/src/main/java/kg/chairx/security/application/RoleManagementService.java](../../../backend/src/main/java/kg/chairx/security/application/RoleManagementService.java)

- UUID id
- UUID actorUserId
- String action
- String targetType
- UUID targetId
- String details
- java.time.Instant createdAt

### kg.chairx.security.application.AuditPage

Source: [backend/src/main/java/kg/chairx/security/application/RoleManagementService.java](../../../backend/src/main/java/kg/chairx/security/application/RoleManagementService.java)

- List<AuditLine> items
- int page
- int size
- long total

### kg.chairx.security.application.CreateRole

Source: [backend/src/main/java/kg/chairx/security/application/RoleManagementService.java](../../../backend/src/main/java/kg/chairx/security/application/RoleManagementService.java)

- String code
- String name
- List<String> permissions

### kg.chairx.security.application.CreateUserCommand

Source: [backend/src/main/java/kg/chairx/security/application/CreateUserCommand.java](../../../backend/src/main/java/kg/chairx/security/application/CreateUserCommand.java)

- String username
- String password
- String displayName

### kg.chairx.security.application.PermissionView

Source: [backend/src/main/java/kg/chairx/security/application/RoleManagementService.java](../../../backend/src/main/java/kg/chairx/security/application/RoleManagementService.java)

- String code
- String description
- String group
- boolean sensitive
- boolean adminOnly

### kg.chairx.security.application.RoleView

Source: [backend/src/main/java/kg/chairx/security/application/RoleManagementService.java](../../../backend/src/main/java/kg/chairx/security/application/RoleManagementService.java)

- UUID id
- String code
- String name
- boolean systemRole
- List<String> permissions
- long assignedUsersCount
- long version

### kg.chairx.security.application.UpdateRole

Source: [backend/src/main/java/kg/chairx/security/application/RoleManagementService.java](../../../backend/src/main/java/kg/chairx/security/application/RoleManagementService.java)

- String name
- List<String> permissions
- Long expectedVersion
- String reason

### kg.chairx.security.persistence.AppUserRecord

Source: [backend/src/main/java/kg/chairx/security/persistence/AppUserRepository.java](../../../backend/src/main/java/kg/chairx/security/persistence/AppUserRepository.java)

- UUID id
- String username
- String passwordHash
- String displayName
- boolean active

### kg.chairx.supplier.api.CreateSupplierRequest

Source: [backend/src/main/java/kg/chairx/supplier/api/CreateSupplierRequest.java](../../../backend/src/main/java/kg/chairx/supplier/api/CreateSupplierRequest.java)

- @NotBlank(message = "Укажите название") @Size(max = 200, message = "Название не должно превышать 200 символов") String name
- @Size(max = 1000, message = "Контактная информация не должна превышать 1000 символов") String contactInformation
- @Size(max = 4000, message = "Комментарий не должен превышать 4000 символов") String comment

### kg.chairx.supplier.api.SupplierPageResponse

Source: [backend/src/main/java/kg/chairx/supplier/api/SupplierPageResponse.java](../../../backend/src/main/java/kg/chairx/supplier/api/SupplierPageResponse.java)

- List<SupplierResponse> items
- int page
- int size
- long totalElements
- int totalPages

### kg.chairx.supplier.api.SupplierResponse

Source: [backend/src/main/java/kg/chairx/supplier/api/SupplierResponse.java](../../../backend/src/main/java/kg/chairx/supplier/api/SupplierResponse.java)

- UUID id
- String name
- String contactInformation
- String comment
- boolean active
- Instant createdAt
- Instant updatedAt

### kg.chairx.supplier.api.UpdateSupplierRequest

Source: [backend/src/main/java/kg/chairx/supplier/api/UpdateSupplierRequest.java](../../../backend/src/main/java/kg/chairx/supplier/api/UpdateSupplierRequest.java)

- @NotBlank(message = "Укажите название") @Size(max = 200, message = "Название не должно превышать 200 символов") String name
- @Size(max = 1000, message = "Контактная информация не должна превышать 1000 символов") String contactInformation
- @Size(max = 4000, message = "Комментарий не должен превышать 4000 символов") String comment

### kg.chairx.warehouse.api.CreateWarehouseRequest

Source: [backend/src/main/java/kg/chairx/warehouse/api/CreateWarehouseRequest.java](../../../backend/src/main/java/kg/chairx/warehouse/api/CreateWarehouseRequest.java)

- @NotBlank(message = "Укажите название склада") @Size(max = 200, message = "Название не должно превышать 200 символов") String name
- @NotBlank(message = "Укажите код склада") @Size(max = 50, message = "Код не должен превышать 50 символов") @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,49}", message = "Код должен содержать заглавные латинские буквы, цифры, дефис или подчёркивание и начинаться с буквы или цифры") String code
- @Size(max = 1000, message = "Адрес не должен превышать 1000 символов") String address

### kg.chairx.warehouse.api.UpdateWarehouseRequest

Source: [backend/src/main/java/kg/chairx/warehouse/api/UpdateWarehouseRequest.java](../../../backend/src/main/java/kg/chairx/warehouse/api/UpdateWarehouseRequest.java)

- @NotBlank(message = "Укажите название склада") @Size(max = 200, message = "Название не должно превышать 200 символов") String name
- @Size(max = 1000, message = "Адрес не должен превышать 1000 символов") String address

### kg.chairx.warehouse.api.WarehousePageResponse

Source: [backend/src/main/java/kg/chairx/warehouse/api/WarehousePageResponse.java](../../../backend/src/main/java/kg/chairx/warehouse/api/WarehousePageResponse.java)

- List<WarehouseResponse> items
- int page
- int size
- long totalElements
- int totalPages

### kg.chairx.warehouse.api.WarehouseResponse

Source: [backend/src/main/java/kg/chairx/warehouse/api/WarehouseResponse.java](../../../backend/src/main/java/kg/chairx/warehouse/api/WarehouseResponse.java)

- UUID id
- String name
- String code
- String address
- boolean active
- Instant createdAt
- Instant updatedAt

## Enums

| Enum | Values (heuristic) | Source |
|---|---|---|
| kg.chairx.defect.domain.DefectStatus | OPEN, WAITING_PARTS, RESOLVED, WRITTEN_OFF | [backend/src/main/java/kg/chairx/defect/domain/DefectStatus.java](../../../backend/src/main/java/kg/chairx/defect/domain/DefectStatus.java) |
| kg.chairx.delivery.domain.DeliveryStatus | READY, IN_TRANSIT, DELIVERED, FAILED, CANCELLED | [backend/src/main/java/kg/chairx/delivery/domain/DeliveryStatus.java](../../../backend/src/main/java/kg/chairx/delivery/domain/DeliveryStatus.java) |
| kg.chairx.expense.domain.ExpenseCategory | ADVERTISING, RENT, SALARY, DELIVERY, UTILITIES, OTHER | [backend/src/main/java/kg/chairx/expense/domain/ExpenseCategory.java](../../../backend/src/main/java/kg/chairx/expense/domain/ExpenseCategory.java) |
| kg.chairx.expense.domain.ExpensePaymentMethod | CASH, BANK | [backend/src/main/java/kg/chairx/expense/domain/ExpensePaymentMethod.java](../../../backend/src/main/java/kg/chairx/expense/domain/ExpensePaymentMethod.java) |
| kg.chairx.finance.domain.FinanceAccount | CASH, BANK | [backend/src/main/java/kg/chairx/finance/domain/FinanceAccount.java](../../../backend/src/main/java/kg/chairx/finance/domain/FinanceAccount.java) |
| kg.chairx.inventory.domain.StockMovementType | PURCHASE_IN, SALE_OUT, RETURN_IN, TRANSFER_OUT, TRANSFER_IN, WRITE_OFF, ADJUSTMENT_IN, ADJUSTMENT_OUT | [backend/src/main/java/kg/chairx/inventory/domain/StockMovementType.java](../../../backend/src/main/java/kg/chairx/inventory/domain/StockMovementType.java) |
| kg.chairx.payment.domain.PaymentChannel | CASH, BANK_TRANSFER, MBANK, BANK_INSTALLMENT | [backend/src/main/java/kg/chairx/payment/domain/PaymentChannel.java](../../../backend/src/main/java/kg/chairx/payment/domain/PaymentChannel.java) |
| kg.chairx.payment.domain.PaymentMethod | CASH, TRANSFER | [backend/src/main/java/kg/chairx/payment/domain/PaymentMethod.java](../../../backend/src/main/java/kg/chairx/payment/domain/PaymentMethod.java) |
| kg.chairx.payment.domain.PaymentStatus | PAID, CANCELLED | [backend/src/main/java/kg/chairx/payment/domain/PaymentStatus.java](../../../backend/src/main/java/kg/chairx/payment/domain/PaymentStatus.java) |
| kg.chairx.purchase.domain.PurchasePaymentKind | SUPPLIER, CARGO | [backend/src/main/java/kg/chairx/purchase/domain/PurchasePaymentKind.java](../../../backend/src/main/java/kg/chairx/purchase/domain/PurchasePaymentKind.java) |
| kg.chairx.purchase.domain.PurchaseStatus | DRAFT, CONFIRMED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED | [backend/src/main/java/kg/chairx/purchase/domain/PurchaseStatus.java](../../../backend/src/main/java/kg/chairx/purchase/domain/PurchaseStatus.java) |
| kg.chairx.refund.domain.RefundMethod | CASH, TRANSFER | [backend/src/main/java/kg/chairx/refund/domain/RefundMethod.java](../../../backend/src/main/java/kg/chairx/refund/domain/RefundMethod.java) |
| kg.chairx.returning.domain.ReturnCondition | SELLABLE, BLOCKED | [backend/src/main/java/kg/chairx/returning/domain/ReturnCondition.java](../../../backend/src/main/java/kg/chairx/returning/domain/ReturnCondition.java) |
| kg.chairx.sale.api.SaleCancellationReason | CUSTOMER_CHANGED_MIND, INSUFFICIENT_FUNDS, PRODUCT_MISMATCH, DUPLICATE_ORDER, OTHER | [backend/src/main/java/kg/chairx/sale/api/SaleCancellationReason.java](../../../backend/src/main/java/kg/chairx/sale/api/SaleCancellationReason.java) |
| kg.chairx.sale.domain.FulfillmentType | SELF_PICKUP, CITY_DELIVERY, REGION_DELIVERY | [backend/src/main/java/kg/chairx/sale/domain/FulfillmentType.java](../../../backend/src/main/java/kg/chairx/sale/domain/FulfillmentType.java) |
| kg.chairx.sale.domain.SaleStatus | DRAFT, CONFIRMED, FULFILLED, CANCELLED | [backend/src/main/java/kg/chairx/sale/domain/SaleStatus.java](../../../backend/src/main/java/kg/chairx/sale/domain/SaleStatus.java) |
