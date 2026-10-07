create table refunds
(
    id                  uuid           primary key,
    sale_id             uuid           not null,
    return_id           uuid,
    amount              numeric(19, 2) not null,
    method              varchar(30)    not null,

    reason              varchar(500)   not null,
    reference           varchar(200),
    comment             varchar(1000),

    idempotency_key     uuid           not null,
    request_fingerprint varchar(64)    not null,

    refunded_by         varchar(200)   not null,
    refunded_at         timestamptz    not null,

    constraint fk_refunds_sale
        foreign key (sale_id)
            references sales (id),

    constraint fk_refunds_return
        foreign key (return_id)
            references returns (id),

    constraint chk_refunds_amount
        check (
            amount > 0
                and amount = trunc(amount)
            ),

    constraint chk_refunds_method
        check (
            method in ('CASH', 'TRANSFER')
            ),

    constraint chk_refunds_reason
        check (
            length(trim(reason)) > 0
            ),

    constraint chk_refunds_fingerprint
        check (
            length(request_fingerprint) = 64
            )
);

create unique index uq_refunds_idempotency_key
    on refunds (idempotency_key);

create index idx_refunds_sale
    on refunds (sale_id);

create index idx_refunds_return
    on refunds (return_id);

create index idx_refunds_refunded_at
    on refunds (refunded_at);