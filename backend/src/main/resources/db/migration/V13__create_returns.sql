create table returns
(
    id              uuid         primary key,
    sale_id         uuid         not null,
    warehouse_id    uuid         not null,
    condition       varchar(30)  not null,
    reason          varchar(500) not null,
    comment         varchar(1000),
    created_by      varchar(200) not null,
    created_at      timestamptz  not null,

    constraint fk_returns_sale
        foreign key (sale_id)
            references sales (id),

    constraint fk_returns_warehouse
        foreign key (warehouse_id)
            references warehouses (id),

    constraint chk_returns_condition
        check (
            condition in ('SELLABLE', 'BLOCKED')
            ),

    constraint chk_returns_reason
        check (
            length(trim(reason)) > 0
            )
);

create table return_items
(
    id              uuid    primary key,
    return_id       uuid    not null,
    sale_item_id    uuid    not null,
    quantity        bigint  not null,

    constraint fk_return_items_return
        foreign key (return_id)
            references returns (id),

    constraint fk_return_items_sale_item
        foreign key (sale_item_id)
            references sale_items (id),

    constraint chk_return_items_quantity
        check (quantity > 0),

    constraint uq_return_items_return_sale_item
        unique (return_id, sale_item_id)
);

create index idx_returns_sale
    on returns (sale_id);

create index idx_returns_created_at
    on returns (created_at);

create index idx_return_items_sale_item
    on return_items (sale_item_id);