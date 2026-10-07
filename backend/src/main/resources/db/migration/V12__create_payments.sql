create table payments
(
    id                  uuid           primary key,
    sale_id             uuid           not null,
    amount              numeric(19, 2) not null,
    method              varchar(30)    not null,
    status              varchar(30)    not null,

    reference           varchar(200),
    comment             varchar(1000),

    paid_by             varchar(200)   not null,
    paid_at             timestamptz    not null,

    cancelled_by        varchar(200),
    cancelled_at        timestamptz,
    cancellation_reason varchar(1000),

    constraint fk_payments_sale
        foreign key (sale_id)
            references sales (id),

    constraint chk_payments_amount
        check (
            amount >= 0
                and amount = trunc(amount)
            ),

    constraint chk_payments_method
        check (
            method in ('CASH', 'TRANSFER')
            ),

    constraint chk_payments_status
        check (
            status in ('PAID', 'CANCELLED')
            ),

    constraint chk_payments_state
        check (
            (
                status = 'PAID'
                    and cancelled_by is null
                    and cancelled_at is null
                    and cancellation_reason is null
                )
                or
            (
                status = 'CANCELLED'
                    and cancelled_by is not null
                    and cancelled_at is not null
                    and cancellation_reason is not null
                    and length(trim(cancellation_reason)) > 0
                )
            )
);

create unique index uq_payments_active_sale
    on payments (sale_id)
    where status = 'PAID';

create index idx_payments_sale
    on payments (sale_id);

create index idx_payments_status
    on payments (status);

create index idx_payments_paid_at
    on payments (paid_at);