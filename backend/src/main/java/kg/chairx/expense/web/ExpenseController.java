package kg.chairx.expense.web;

import jakarta.validation.Valid;

import kg.chairx.expense.api.CreateExpenseRequest;
import kg.chairx.expense.api.ExpenseResponse;
import kg.chairx.expense.application.ExpenseService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(
            @Valid @RequestBody CreateExpenseRequest request
    ) {
        ExpenseResponse result = service.create(request);

        return ResponseEntity
                .created(URI.create("/api/expenses/" + result.id()))
                .body(result);
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    public List<ExpenseResponse> list(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        return service.list(from, to);
    }

    @GetMapping("/total")
    public BigDecimal total(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        return service.total(from, to);
    }
}