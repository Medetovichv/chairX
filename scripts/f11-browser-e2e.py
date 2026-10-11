#!/usr/bin/env python3
"""Destructive Chromium + HTTP + PostgreSQL acceptance: ONLY empty ephemeral CI."""
import base64
import datetime
import http.cookiejar
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.request
import uuid
from playwright.sync_api import sync_playwright

BASE="http://127.0.0.1:5173"
DAY=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=6))).date().isoformat()
ART=Path("artifacts/f11")
ART.mkdir(parents=True,exist_ok=True)

def sql(query):
    result=subprocess.run(["docker","compose","exec","-T","postgres","psql",
      "-v","ON_ERROR_STOP=1","-U","chairx","-d","chairx","-tA","-c",query],
      capture_output=True,text=True,check=True,timeout=30)
    return result.stdout.strip()

def db(closes,movements,cash,bank,snapshots):
    expected={
      "SELECT count(*) FROM finance_daily_closings":closes,
      "SELECT count(*) FROM finance_movements":movements,
      "SELECT balance::bigint FROM finance_accounts WHERE code='CASH'":cash,
      "SELECT balance::bigint FROM finance_accounts WHERE code='BANK'":bank,
      "SELECT count(*) FROM finance_daily_closing_sales_snapshots":snapshots,
    }
    for query,value in expected.items():
        actual=sql(query)
        assert actual==str(value),f"PostgreSQL invariant {query}: {actual} != {value}"
    if closes:
        assert sql("SELECT count(*) FROM finance_daily_closing_accounts")=="2"
        assert sql("SELECT count(*) FROM finance_daily_closing_sales")=="0"

class API:
    """Real Basic Auth, shared cookies and CSRF, via frontend proxy."""
    def __init__(self,username,password):
        self.auth="Basic "+base64.b64encode((username+":"+password).encode()).decode()
        self.opener=urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        status,csrf=self.request("GET","/api/csrf")
        assert status==200
        self.csrf_header,self.csrf_token=csrf["headerName"],csrf["token"]
    def request(self,method,path,body=None):
        headers={"Authorization":self.auth,"Accept":"application/json"}
        data=None
        if body is not None:
            data=json.dumps(body).encode()
            headers["Content-Type"]="application/json"
        # Spring's CSRF also protects zero-body POSTs (confirm, dispatch, etc.).
        if method.upper() in ("POST","PUT","PATCH","DELETE") and hasattr(self,"csrf_token"):
            headers[self.csrf_header]=self.csrf_token
        req=urllib.request.Request(BASE+path,data=data,headers=headers,method=method)
        try: response=self.opener.open(req,timeout=15)
        except urllib.error.HTTPError as error: response=error
        with response:
            body=response.read().decode()
            return response.status,json.loads(body) if body else {}
    def must(self,method,path,body,status):
        actual,result=self.request(method,path,body)
        assert actual==status,f"{method} {path}: {actual} != {status}; {result}"
        return result

def main():
    assert os.environ.get("CI")=="true"
    assert os.environ.get("CHAIRX_F11_E2E_DISPOSABLE")=="I_UNDERSTAND_DATABASE_IS_DISPOSABLE"
    assert sql("SELECT current_database()")=="chairx"
    # Reject a local/old database before first write.
    for query in [
        "SELECT count(*) FROM finance_daily_closings",
        "SELECT count(*) FROM finance_movements",
        "SELECT count(*) FROM expenses",
        "SELECT count(*) FROM finance_daily_closing_sales_snapshots",
        "SELECT count(*) FROM finance_accounts WHERE balance<>0 OR opening_balance_initialized",
    ]: assert sql(query)=="0",f"Database is NOT disposable/empty: {query}"
    adminpass=os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"]
    admin=API("admin",adminpass)
    catalog=API("catalog",os.environ["CHAIRX_CATALOG_PASSWORD"])
    url="/api/finance/closings/"+DAY
    catalog.must("GET","/api/finance/closings",None,403)
    catalog.must("GET",url+"/sales",None,403)
    admin.must("POST","/api/finance/opening-balances",
               {"account":"CASH","amount":10000},200)
    admin.must("POST","/api/finance/opening-balances",
               {"account":"BANK","amount":5000},200)
    db(0,2,10000,5000,0)
    preview=admin.must("GET",url+"/preview",None,200)
    assert int(preview["expectedCash"])==10000
    assert int(preview["expectedBank"])==5000

    with sync_playwright() as pw:
        browser=pw.chromium.launch(headless=True)
        context=browser.new_context(viewport={"width":1440,"height":900},locale="ru-RU")
        page=context.new_page()
        try:
            page.goto(BASE+"/daily-closing",wait_until="domcontentloaded")
            page.get_by_label("Логин").fill("admin")
            page.get_by_label("Пароль",exact=True).fill(adminpass)
            page.get_by_role("button",name="Войти",exact=True).click()
            page.get_by_role("heading",name="Закрытие дня",level=2).wait_for(timeout=30000)
            page.get_by_role("heading",name="Предварительный расчёт backend").wait_for()
            form=page.get_by_role("form",name="Закрытие финансового дня")
            cash=form.get_by_role("textbox",name="Факт CASH")
            bank=form.get_by_role("textbox",name="Факт BANK")
            cash.fill("10000");bank.fill("5000")
            page.screenshot(path=str(ART/"01-before-close.png"),full_page=True)
            form.get_by_role("button",name="Проверить и закрыть").click()
            page.get_by_role("alertdialog").wait_for()
            # Real concurrent financial transfer changes expected balances
            # after the employee viewed the preview, before their confirmation.
            admin.must("POST","/api/finance/transfers",{
                "transferId":str(uuid.uuid4()),"from":"BANK",
                "to":"CASH","amount":100},200)
            db(0,4,10100,4900,0)
            page.get_by_role("alertdialog").get_by_role(
                "button",name="Зафиксировать отчёт").click()
            page.get_by_text("Проверка перед закрытием не пройдена:",
                             exact=False).wait_for(timeout=25000)
            db(0,4,10100,4900,0)
            page.screenshot(path=str(ART/"02-concurrency-blocked.png"),full_page=True)
            cash.fill("10100");bank.fill("4900")
            form.get_by_role("button",name="Проверить и закрыть").click()
            page.get_by_role("alertdialog").get_by_role(
                "button",name="Зафиксировать отчёт").click()
            page.get_by_role("heading",name="День закрыт",exact=False).wait_for(timeout=30000)
            page.screenshot(path=str(ART/"03-closed-day.png"),full_page=True)
            db(1,4,10100,4900,1)
            report=admin.must("GET",url,None,200)
            assert int(report["cash"]["expected"])==10100
            assert int(report["bank"]["expected"])==4900
            assert int(report["cash"]["actual"])==10100
            assert int(report["bank"]["actual"])==4900
            snapshot=admin.must("GET",url+"/sales",None,200)
            assert snapshot["closed"] and snapshot["snapshotAvailable"]
            assert not snapshot["preliminary"]
            retry,_=admin.request("POST",url,{
                "actualCash":10100,"actualBank":4900,
                "cashNote":None,"bankNote":None})
            assert retry==409,f"Duplicate close: unexpected HTTP {retry}"
            db(1,4,10100,4900,1)
            blocked,_=admin.request("POST",url+"/expenses",{
                "idempotencyKey":str(uuid.uuid4()),"category":"OTHER",
                "amount":1,"paymentMethod":"CASH",
                "expenseDate":DAY,"comment":"Prohibited after today's closing"})
            assert blocked in (400,403,409),f"Closed-day expense unexpectedly allowed: {blocked}"
            assert sql("SELECT count(*) FROM expenses")=="0"
            db(1,4,10100,4900,1)
            print("F11 real Chromium → proxy → Spring → PostgreSQL verified.")
        finally:
            context.close()
            browser.close()

if __name__=="__main__":
    main()
