package kg.chairx.customer.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kg.chairx.customer.api.CreateCustomerRequest;
import kg.chairx.customer.api.CustomerPageResponse;
import kg.chairx.customer.api.CustomerResponse;
import kg.chairx.customer.api.UpdateCustomerRequest;
import kg.chairx.customer.application.CustomerService;
import kg.chairx.customer.application.CustomerAddressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;
    private final CustomerAddressService addresses;

    public CustomerController(CustomerService service, CustomerAddressService addresses) {
        this.service = service;
        this.addresses = addresses;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        CustomerResponse response = service.create(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/customers/" + response.id()
                        )
                )
                .body(response);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(
            @PathVariable UUID id
    ) {
        return service.get(id);
    }

    @GetMapping
    public CustomerPageResponse list(
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size
    ) {
        return service.list(page, size);
    }

    @GetMapping("/search")
    public List<CustomerResponse> search(
            @RequestParam String query
    ) {
        return service.search(query);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return service.update(id, request);
    }

    @GetMapping("/{id}/addresses")
    public List<CustomerAddressService.Address> addresses(@PathVariable UUID id) {
        return addresses.list(id);
    }

    @PostMapping("/{id}/addresses")
    public CustomerAddressService.Address addAddress(@PathVariable UUID id,
            @Valid @RequestBody CustomerAddressService.AddressInput input) {
        return addresses.add(id,input);
    }

    @PostMapping("/{id}/activate")
    public CustomerResponse activate(
            @PathVariable UUID id
    ) {
        return service.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    public CustomerResponse deactivate(
            @PathVariable UUID id
    ) {
        return service.deactivate(id);
    }
}