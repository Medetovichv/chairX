package kg.chairx.customer.application;

import kg.chairx.customer.api.CreateCustomerRequest;
import kg.chairx.customer.api.CustomerPageResponse;
import kg.chairx.customer.api.CustomerResponse;
import kg.chairx.customer.api.UpdateCustomerRequest;
import kg.chairx.customer.domain.Customer;
import kg.chairx.customer.persistence.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int SEARCH_LIMIT = 20;

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        if (request == null) {
            throw new CustomerRuleViolationException(
                    "Данные клиента обязательны"
            );
        }

        Instant now = Instant.now();

        Customer customer = new Customer(
                UUID.randomUUID(),
                requiredName(request.fullName()),
                optional(request.phone(), 50, "Телефон"),
                optional(request.secondaryPhone(), 50, "Дополнительный телефон"),
                optional(request.whatsappPhone(), 50, "WhatsApp"),
                optional(request.instagramUsername(), 100, "Instagram"),
                optional(request.address(), 500, "Адрес"),
                optional(request.cityRegion(), 200, "Город или регион"),
                optional(request.comment(), 2000, "Комментарий"),
                true,
                now,
                now
        );

        repository.insert(customer);

        return CustomerMapper.toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID customerId) {
        requireCustomerId(customerId);

        Customer customer = repository.find(customerId)
                .orElseThrow(
                        () -> new CustomerNotFoundException(customerId)
                );

        return CustomerMapper.toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerPageResponse list(
            int page,
            int size
    ) {
        validatePage(page, size);

        List<CustomerResponse> items = repository.list(page, size)
                .stream()
                .map(CustomerMapper::toResponse)
                .toList();

        return new CustomerPageResponse(
                items,
                page,
                size,
                repository.count()
        );
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalized = query.trim();

        if (normalized.length() > 200) {
            throw new CustomerRuleViolationException(
                    "Поисковый запрос слишком длинный"
            );
        }

        return repository.search(normalized, SEARCH_LIMIT)
                .stream()
                .map(CustomerMapper::toResponse)
                .toList();
    }

    @Transactional
    public CustomerResponse update(
            UUID customerId,
            UpdateCustomerRequest request
    ) {
        requireCustomerId(customerId);

        if (request == null) {
            throw new CustomerRuleViolationException(
                    "Данные клиента обязательны"
            );
        }

        Customer current = repository.lock(customerId)
                .orElseThrow(
                        () -> new CustomerNotFoundException(customerId)
                );

        Customer changed = new Customer(
                current.id(),
                requiredName(request.fullName()),
                optional(request.phone(), 50, "Телефон"),
                optional(request.secondaryPhone(), 50, "Дополнительный телефон"),
                optional(request.whatsappPhone(), 50, "WhatsApp"),
                optional(request.instagramUsername(), 100, "Instagram"),
                optional(request.address(), 500, "Адрес"),
                optional(request.cityRegion(), 200, "Город или регион"),
                optional(request.comment(), 2000, "Комментарий"),
                current.active(),
                current.createdAt(),
                Instant.now()
        );

        repository.update(changed);

        return CustomerMapper.toResponse(changed);
    }

    @Transactional
    public CustomerResponse activate(UUID customerId) {
        return changeActive(customerId, true);
    }

    @Transactional
    public CustomerResponse deactivate(UUID customerId) {
        return changeActive(customerId, false);
    }

    private CustomerResponse changeActive(
            UUID customerId,
            boolean active
    ) {
        requireCustomerId(customerId);

        Customer current = repository.lock(customerId)
                .orElseThrow(
                        () -> new CustomerNotFoundException(customerId)
                );

        if (current.active() == active) {
            return CustomerMapper.toResponse(current);
        }

        Customer changed = new Customer(
                current.id(),
                current.fullName(),
                current.phone(),
                current.secondaryPhone(),
                current.whatsappPhone(),
                current.instagramUsername(),
                current.address(),
                current.cityRegion(),
                current.comment(),
                active,
                current.createdAt(),
                Instant.now()
        );

        repository.update(changed);

        return CustomerMapper.toResponse(changed);
    }

    private void requireCustomerId(UUID customerId) {
        if (customerId == null) {
            throw new CustomerRuleViolationException(
                    "Укажите клиента"
            );
        }
    }

    private String requiredName(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomerRuleViolationException(
                    "Имя клиента обязательно"
            );
        }

        String normalized = value.trim();

        if (normalized.length() > 200) {
            throw new CustomerRuleViolationException(
                    "Имя клиента не должно превышать 200 символов"
            );
        }

        return normalized;
    }

    private String optional(
            String value,
            int maxLength,
            String fieldName
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > maxLength) {
            throw new CustomerRuleViolationException(
                    fieldName + " превышает допустимую длину"
            );
        }

        return normalized;
    }

    private void validatePage(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new CustomerRuleViolationException(
                    "Номер страницы не может быть отрицательным"
            );
        }

        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new CustomerRuleViolationException(
                    "Размер страницы должен быть от 1 до 100"
            );
        }
    }
}