alter table return_items
    add column condition varchar(30);

update return_items
set condition = r.condition
    from returns r
where r.id = return_items.return_id;

alter table return_items
    alter column condition set not null;

alter table return_items
    add constraint ck_return_items_condition
        check (condition in ('SELLABLE', 'BLOCKED'));

alter table returns
drop column condition;

alter table returns
    add column idempotency_key uuid,
    add column request_fingerprint varchar(64);

update returns
set idempotency_key = id,
    request_fingerprint = repeat('0', 64)
where idempotency_key is null;

alter table returns
    alter column idempotency_key set not null,
alter column request_fingerprint set not null;

alter table returns
    add constraint uq_returns_idempotency_key
        unique (idempotency_key);

alter table returns
    add constraint ck_returns_request_fingerprint
        check (length(request_fingerprint) = 64);