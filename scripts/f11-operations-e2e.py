#!/usr/bin/env python3
"""Issue #32: one populated API journey observed in real Chromium and PostgreSQL.

THIS IS DESTRUCTIVE. Never run against a developer's retained Compose database
or production. CI creates an empty database and destroys the volume afterward.
All business transactions use authenticated HTTP through the browser's Vite
proxy; SQL is READ-ONLY for postcondition assertions.
"""
import datetime as dt
import importlib.util
import os
from pathlib import Path
import uuid

from playwright.sync_api import sync_playwright

spec=importlib.util.spec_from_file_location("f11_browser_harness",
    Path(__file__).with_name("f11-browser-e2e.py"))
harness=importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)
sql,API,BASE,DAY=harness.sql,harness.API,harness.BASE,harness.DAY
ART=Path("artifacts/issue32-operations")
ART.mkdir(parents=True,exist_ok=True)

def db_number(query,expected):
    actual=sql(query)
    assert actual==str(expected),f"DB invariant: {query}; expected {expected}, got {actual}"

def money(api,cash,bank,journal):
    records=api.must("GET","/api/finance/accounts",None,200)
    balances={r["code"]:int(r["balance"]) for r in records}
    assert balances=={"CASH":cash,"BANK":bank},balances
    db_number("SELECT balance::bigint FROM finance_accounts WHERE code='CASH'",cash)
    db_number("SELECT balance::bigint FROM finance_accounts WHERE code='BANK'",bank)
    db_number("SELECT count(*) FROM finance_movements",journal)

def stock(api,warehouse,variant,expected):
    record=api.must("GET",f"/api/inventory/balances/{warehouse}/{variant}",None,200)
    assert record["onHand"]==expected,record
    assert record["reserved"]==0,record
    assert record["blocked"]==0,record
    assert record["available"]==expected,record

def uuid4(): return str(uuid.uuid4())

def main():
    assert os.environ.get("CI")=="true"
    assert os.environ.get("CHAIRX_F11_E2E_DISPOSABLE")=="I_UNDERSTAND_DATABASE_IS_DISPOSABLE"
    assert sql("SELECT current_database()")=="chairx"
    # Completely empty test environment. Abort BEFORE first data write.
    for q in (
        "SELECT count(*) FROM finance_daily_closings",
        "SELECT count(*) FROM finance_movements",
        "SELECT count(*) FROM sales",
        "SELECT count(*) FROM purchases",
        "SELECT count(*) FROM returns",
        "SELECT count(*) FROM payments",
        "SELECT count(*) FROM refunds",
        "SELECT count(*) FROM finance_accounts WHERE balance<>0 OR opening_balance_initialized",
    ):db_number(q,0)

    adminpassword=os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"]
    admin=API("admin",adminpassword)
    outsider=API("catalog",os.environ["CHAIRX_CATALOG_PASSWORD"])
    admin.must("POST","/api/finance/opening-balances",{"account":"CASH","amount":20000},200)
    admin.must("POST","/api/finance/opening-balances",{"account":"BANK","amount":100000},200)
    money(admin,20000,100000,2)

    # Master records are also created over real API, no SQL fixture inserts.
    supplier=admin.must("POST","/api/suppliers",{
        "name":"Issue32 Test Supplier","contactInformation":"CI only","comment":None},201)
    product=admin.must("POST","/api/products",{
        "name":"Issue32 Ergonomic Chair","description":"Disposable integration test","category":"Office"},201)
    variant=admin.must("POST",f"/api/products/{product['id']}/variants",{
        "name":"Black","sku":"E2E-"+uuid4()[:8],"color":"Black",
        "recommendedSalePrice":8500},201)
    customer=admin.must("POST","/api/customers",{
        "fullName":"Issue32 Test Customer","phone":"+996555112233",
        "secondaryPhone":None,"whatsappPhone":None,"instagramUsername":None,
        "address":"Bishkek, Test street","cityRegion":"Bishkek","comment":None},201)
    variant_id=variant["id"]
    home=sql("SELECT id FROM warehouses WHERE code='HOME'")
    office=sql("SELECT id FROM warehouses WHERE code='OFFICE'")
    assert home and office and home!=office

    # Negative path: cannot sell nonexistent stock, including across two warehouses.
    before_money=sql("SELECT count(*) FROM finance_movements")
    bad,body=admin.request("POST","/api/sales",{
        "idempotencyKey":uuid4(),"customerId":customer["id"],"fulfillmentType":"CITY_DELIVERY",
        "items":[{"productVariantId":variant_id,"warehouseId":home,
                  "quantity":2,"unitSalePrice":8500}]})
    assert 400<=bad<500,("Unfunded sale unexpectedly passed",bad,body)
    db_number("SELECT count(*) FROM sales",0)
    db_number("SELECT count(*) FROM finance_movements",before_money)

    # Purchase, partial receiving into HOME, second receipt into OFFICE.
    purchase=admin.must("POST","/api/purchases",{
        "supplierId":supplier["id"],"items":[{"productVariantId":variant_id,
          "orderedQuantity":6,"purchaseUnitCost":5000}],"cargoCost":0,
        "comment":"Issue32 partial goods receipt"},201)
    pid=purchase["id"]
    confirmed=admin.must("POST",f"/api/purchases/{pid}/confirm",None,200)
    assert confirmed["status"]=="CONFIRMED"
    lineid=confirmed["items"][0]["id"]
    home_receipt={"idempotencyKey":uuid4(),"warehouseId":home,
        "items":[{"purchaseItemId":lineid,"quantity":4}],"comment":"HOME first"}
    first=admin.must("POST",f"/api/purchases/{pid}/receipts",home_receipt,201)
    assert first["items"][0]["quantity"]==4
    retry=admin.must("POST",f"/api/purchases/{pid}/receipts",home_receipt,201)
    assert retry["id"]==first["id"],"same receipt key must not duplicate stock"
    stock(admin,home,variant_id,4)
    status=admin.must("GET",f"/api/purchases/{pid}",None,200)
    assert status["status"]=="PARTIALLY_RECEIVED"
    office_receipt={"idempotencyKey":uuid4(),"warehouseId":office,
        "items":[{"purchaseItemId":lineid,"quantity":2}],"comment":"OFFICE second"}
    second=admin.must("POST",f"/api/purchases/{pid}/receipts",office_receipt,201)
    assert second["id"]!=first["id"]
    stock(admin,office,variant_id,2)
    status=admin.must("GET",f"/api/purchases/{pid}",None,200)
    assert status["status"]=="RECEIVED"
    over,_=admin.request("POST",f"/api/purchases/{pid}/receipts",{
        "idempotencyKey":uuid4(),"warehouseId":office,
        "items":[{"purchaseItemId":lineid,"quantity":1}],"comment":None})
    assert 400<=over<500,("Receiving > ordered allowed",over)
    stock(admin,home,variant_id,4)
    stock(admin,office,variant_id,2)
    money(admin,20000,100000,2) # Receipt is *not* a payment.

    # One sale with two warehouses, real BANK settlement, physical dispatch.
    sale=admin.must("POST","/api/sales",{
        "idempotencyKey":uuid4(),"customerId":customer["id"],
        "fulfillmentType":"CITY_DELIVERY",
        "items":[
          {"productVariantId":variant_id,"warehouseId":home,
           "quantity":2,"unitSalePrice":8500},
          {"productVariantId":variant_id,"warehouseId":office,
           "quantity":1,"unitSalePrice":8500},
        ]},201)
    sid=sale["id"]
    assert sale["status"]=="CONFIRMED"
    assert int(sale["total"])==25500
    delivery=admin.must("POST","/api/deliveries",{
        "saleId":sid,"recipientName":"Issue32 Test Customer",
        "recipientPhone":"+996555112233","address":"Bishkek Test Address",
        "cityRegion":"Bishkek","deliveryCost":0,
        "carrierName":None,"trackingNumber":None,"comment":None,
        "plannedDeliveryDate":None},201)
    did=delivery["id"]
    assert delivery["status"]=="READY"
    pay=admin.must("POST","/api/payments",{
        "saleId":sid,"method":"TRANSFER","channel":"MBANK",
        "reference":"ISSUE32-"+uuid4()[:8],"comment":"Test paid"},201)
    payment_id=pay["id"]
    assert int(pay["amount"])==25500 and pay["status"]=="PAID"
    money(admin,20000,125500,3)
    payment_repeat,_=admin.request("POST","/api/payments",{
        "saleId":sid,"method":"TRANSFER","channel":"MBANK",
        "reference":"unexpected second payment","comment":None})
    assert 400<=payment_repeat<500
    db_number("SELECT count(*) FROM payments WHERE sale_id='"+sid+"'",1)
    money(admin,20000,125500,3)

    admin.must("POST",f"/api/deliveries/{did}/dispatch",None,200)
    transit=admin.must("GET",f"/api/deliveries/{did}",None,200)
    assert transit["status"]=="IN_TRANSIT"
    admin.must("POST",f"/api/deliveries/{did}/deliver",None,200)
    complete=admin.must("GET",f"/api/deliveries/{did}",None,200)
    assert complete["status"]=="DELIVERED"
    fulfilled=admin.must("GET",f"/api/sales/{sid}",None,200)
    assert fulfilled["status"]=="FULFILLED"
    stock(admin,home,variant_id,2)
    stock(admin,office,variant_id,1)

    # Partial SELLABLE return: one of the two HOME units. Refund exactly the
    # associated item value, idempotently, without repeating warehouse credit.
    home_sale_item=next(item for item in fulfilled["items"] if item["warehouseId"]==home)
    return_request={"idempotencyKey":uuid4(),"saleId":sid,"warehouseId":home,
       "items":[{"saleItemId":home_sale_item["id"],"quantity":1,"condition":"SELLABLE"}],
       "reason":"Customer changed mind","comment":"partial return CI"}
    returned=admin.must("POST","/api/returns",return_request,201)
    re_returned=admin.must("POST","/api/returns",return_request,201)
    assert re_returned["id"]==returned["id"]
    stock(admin,home,variant_id,3)
    stock(admin,office,variant_id,1)
    refund_request={"idempotencyKey":uuid4(),"saleId":sid,"returnId":returned["id"],
        "amount":8500,"method":"TRANSFER","reason":"Partial return",
        "reference":"ISSUE32-refund","comment":None}
    refund=admin.must("POST","/api/refunds",refund_request,201)
    again=admin.must("POST","/api/refunds",refund_request,201)
    assert again["id"]==refund["id"]
    money(admin,20000,117000,4)
    db_number("SELECT count(*) FROM refunds WHERE sale_id='"+sid+"'",1)
    db_number("SELECT count(*) FROM returns WHERE sale_id='"+sid+"'",1)
    db_number("SELECT count(*) FROM finance_movements WHERE source_type='PAYMENT' AND source_id='"+payment_id+"'",1)
    db_number("SELECT count(*) FROM finance_movements WHERE source_type='REFUND' AND source_id='"+refund["id"]+"'",1)

    # Reader with ONLY CATALOG_READ cannot read finance or modify sale.
    outsider.must("GET","/api/finance/closings/"+DAY+"/preview",None,403)
    blocked,_=outsider.request("POST",f"/api/deliveries/{did}/dispatch",{})
    assert blocked==403,f"CATALOG could mutate delivery: {blocked}"
    money(admin,20000,117000,4)

    # Real Chromium must render the same completed sale and delivery.
    with sync_playwright() as pw:
        browser=pw.chromium.launch(headless=True)
        context=browser.new_context(viewport={"width":1440,"height":950},locale="ru-RU")
        page=context.new_page()
        try:
            page.goto(BASE+"/sales/"+sid,wait_until="domcontentloaded")
            page.get_by_label("Логин").fill("admin")
            page.get_by_label("Пароль",exact=True).fill(adminpassword)
            page.get_by_role("button",name="Войти",exact=True).click()
            # Protected React route, must show authoritative real sale number.
            page.get_by_text(sale["saleNumber"],exact=False).first.wait_for(timeout=30000)
            page.screenshot(path=str(ART/"01-sale-after-return.png"),full_page=True)
            # In-memory credentials intentionally vanish on a full page reload.
            # BrowserRouter navigation preserves the same authenticated SPA.
            page.evaluate("""path => {
                history.pushState({}, '', path);
                window.dispatchEvent(new PopStateEvent('popstate'));
            }""", "/deliveries/"+did)
            page.get_by_role("heading",name="Доставка",exact=False).last.wait_for(timeout=30000)
            page.get_by_text("Bishkek Test Address",exact=True).wait_for()
            page.screenshot(path=str(ART/"02-delivered-order.png"),full_page=True)
            page.evaluate("""path => {
                history.pushState({}, '', path);
                window.dispatchEvent(new PopStateEvent('popstate'));
            }""", "/daily-closing")
            page.get_by_role("heading",name="Предварительный расчёт backend").wait_for(timeout=30000)
            sales_day=admin.must("GET","/api/finance/closings/"+DAY+"/sales",None,200)
            assert sales_day["preliminary"] and not sales_day["closed"]
            assert sales_day["totals"]["orders"]==1
            assert sales_day["totals"]["chairs"]==3
            assert int(sales_day["totals"]["value"])==25500
            assert sales_day["items"][0]["saleId"]==sid
            # Browser signs exactly once on nonempty financial state.
            form=page.get_by_role("form",name="Закрытие финансового дня")
            form.get_by_role("textbox",name="Факт CASH").fill("20000")
            form.get_by_role("textbox",name="Факт BANK").fill("117000")
            form.get_by_role("button",name="Проверить и закрыть").click()
            page.get_by_role("alertdialog").get_by_role(
                "button",name="Зафиксировать отчёт").click()
            page.get_by_role("heading",name="День закрыт",exact=False).wait_for(timeout=30000)
            page.screenshot(path=str(ART/"03-closing-with-one-sale.png"),full_page=True)
            money(admin,20000,117000,4)
            db_number("SELECT count(*) FROM finance_daily_closings",1)
            db_number("SELECT count(*) FROM finance_daily_closing_sales_snapshots",1)
            db_number("SELECT count(*) FROM finance_daily_closing_sales",1)
            snapshot=admin.must("GET","/api/finance/closings/"+DAY+"/sales",None,200)
            assert snapshot["closed"] and snapshot["snapshotAvailable"]
            assert not snapshot["preliminary"]
            assert snapshot["totals"]["orders"]==1
            assert snapshot["totals"]["chairs"]==3
            assert int(snapshot["totals"]["value"])==25500
            assert snapshot["items"][0]["saleId"]==sid
            closing=admin.must("GET","/api/finance/closings/"+DAY,None,200)
            assert int(closing["cash"]["expected"])==20000
            assert int(closing["bank"]["expected"])==117000
            # Even a monetary edit after closing is rejected with ZERO side effects.
            denied,_=admin.request("POST","/api/finance/transfers",{
                "transferId":uuid4(),"from":"BANK","to":"CASH","amount":1})
            assert 400<=denied<500,f"Finance transfer after close unexpectedly allowed: {denied}"
            money(admin,20000,117000,4)
            print("Issue32 populated business chain verified: purchase, two receipts, "
                  "multi-warehouse sale, payment, delivery, return, refund, "
                  "browser closing and immutable sales snapshot.")
        finally:
            context.close()
            browser.close()

if __name__=="__main__":
    main()
