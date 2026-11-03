#!/usr/bin/env python3
"""Stress the real ChairX UI with long labels and paginated datasets in disposable CI.

Creates test customers/models via the REAL API only after existing F11 finance acceptance
has finished. Never run on production or retained local databases.
"""
import importlib.util
import json
import os
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT=Path(__file__).parent
spec=importlib.util.spec_from_file_location("chairx_stress_harness",ROOT/"f11-browser-e2e.py")
harness=importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)
ART=Path("artifacts/studio-stress")
ART.mkdir(parents=True,exist_ok=True)
BASE=harness.BASE
WIDTHS=(320,375,768,1440)
N=42
PREFIX="STUDIO_STRESS_"

def nav(page,route):
    page.evaluate("""route => {
        history.pushState({}, '', route);
        dispatchEvent(new PopStateEvent('popstate'));
    }""",route)

def layout(page):
    return page.evaluate("""() => ({
        viewport:document.documentElement.clientWidth,
        pageWidth:document.documentElement.scrollWidth,
        mainWidth:document.querySelector('main')?.scrollWidth ?? 0
    })""")

def main():
    assert os.environ.get("CI")=="true"
    assert os.environ.get("CHAIRX_F11_E2E_DISPOSABLE")=="I_UNDERSTAND_DATABASE_IS_DISPOSABLE"
    assert harness.sql("SELECT current_database()")=="chairx"
    # F11 populated financial journey must already be complete.
    assert int(harness.sql("SELECT count(*) FROM finance_daily_closings"))>=1
    api=harness.API("admin",os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"])
    long_customer=PREFIX+"Клиент"+"А"*145
    long_product=PREFIX+"Кресло"+"Б"*150
    for i in range(N):
        customer_name=long_customer if i==0 else f"{PREFIX}Клиент_{i:03d}_"+"А"*110
        product_name=long_product if i==0 else f"{PREFIX}Кресло_{i:03d}_"+"Б"*110
        api.must("POST","/api/customers",{
            "fullName":customer_name,"phone":f"+996700{i:06d}",
            "secondaryPhone":None,"whatsappPhone":None,"instagramUsername":None,
            "address": "Тестовый адрес " + "У"*95,
            "cityRegion":"Бишкек_"+"Р"*90,"comment":"Disposable UI stress fixture"
        },201)
        api.must("POST","/api/products",{
            "name":product_name,"description":"Disposable Studio stress fixture",
            "category":"Офисные_"+"К"*88
        },201)
    customers=api.must("GET","/api/customers/overview?page=0&size=20",None,200)
    products=api.must("GET","/api/products?page=0&size=20",None,200)
    assert customers["total"]>=N and products["totalElements"]>=N, (
        f"Expected paginated fixtures: {customers['total']}, {products['totalElements']}"
    )
    timings=[]
    for endpoint in ("/api/customers/overview?page=0&size=20", "/api/products?page=0&size=20"):
        for _ in range(5):
            start=time.perf_counter()
            response=api.must("GET",endpoint,None,200)
            timings.append({"endpoint":endpoint,"elapsed_ms":round((time.perf_counter()-start)*1000,1),
                            "count":len(response["items"])})
    samples=[]
    with sync_playwright() as p:
        browser=p.chromium.launch(headless=True)
        context=browser.new_context(viewport={"width":1440,"height":900},locale="ru-RU",
                                    reduced_motion="reduce")
        page=context.new_page()
        try:
            page.goto(BASE+"/customers",wait_until="domcontentloaded")
            page.get_by_label("Логин").fill("admin")
            page.get_by_label("Пароль",exact=True).fill(os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"])
            page.get_by_role("button",name="Войти",exact=True).click()
            page.get_by_role("heading",name="Клиенты",exact=True).wait_for(timeout=30000)
            for route,heading,section in (
                ("/customers","Клиенты","Список клиентов"),
                ("/catalog","Каталог товаров","Список моделей")
            ):
                nav(page,route)
                page.get_by_role("heading",name=heading,exact=True).wait_for(timeout=30000)
                page.get_by_role("region",name=section,exact=True).wait_for(timeout=30000)
                # Ensure pagination under load, and the next page is selectable.
                next_label="Следующая" if route=="/customers" else "Вперёд"
                back_label="Предыдущая" if route=="/customers" else "Назад"
                pager=page.get_by_role("button",name=next_label)
                pager.wait_for(timeout=30000)
                assert pager.is_enabled(),f"{route}: 42 rows should produce multiple pages"
                pager.click()
                page.get_by_role("button",name=back_label).wait_for(timeout=30000)
                assert page.get_by_role("button",name=back_label).is_enabled()
                page.get_by_role("button",name=back_label).click()
                for width in WIDTHS:
                    page.set_viewport_size({"width":width,"height":900})
                    page.wait_for_timeout(130)
                    m=layout(page)
                    assert m["pageWidth"]<=m["viewport"]+1,(
                        f"{route} at {width}px: horizontal document overflow {m}"
                    )
                    samples.append({"route":route,"width":width,**m})
                    if width in (375,1440):
                        page.screenshot(path=str(ART/f"{route[1:]}-{width}.png"),full_page=True)
            # Narrow-width long text must wrap *inside* the viewport even after filtering.
            for route,input_name,term,section in (
                ("/customers","Поиск клиентов",long_customer,"Список клиентов"),
                ("/catalog","Поиск модели или категории",long_product,"Список моделей")
            ):
                nav(page,route)
                page.set_viewport_size({"width":375,"height":900})
                field=page.get_by_role("searchbox",name=input_name)
                field.fill(term)
                page.get_by_role("region",name=section,exact=True).get_by_text(term,exact=True).last.wait_for(timeout=30000)
                m=layout(page)
                assert m["pageWidth"]<=m["viewport"]+1,(
                    f"{route}: long unbroken label overflow: {m}"
                )
                page.screenshot(path=str(ART/f"{route[1:]}-long-375.png"),full_page=True)
                samples.append({"route":route,"long_unbroken_label":True,**m})
                field.fill("")
            # Validate forms without submitting any additional business transactions.
            nav(page,"/customers")
            page.get_by_role("heading",name="Клиенты",exact=True).wait_for()
            page.get_by_role("button",name="Добавить клиента").click()
            customer_form=page.get_by_role("form",name="Создание клиента")
            customer_form.get_by_role("button",name="Сохранить клиента").click()
            customer_form.get_by_role("alert").wait_for(timeout=10000)
            customer_form.get_by_role("button",name="Отмена").click()
            assert customer_form.count()==0,"Customer form did not close"
            samples.append({"form":"customers","required_contact_validation":"pass"})

            nav(page,"/catalog")
            page.get_by_role("heading",name="Каталог товаров",exact=True).wait_for()
            page.get_by_role("button",name="Создать товар").click()
            product_form=page.get_by_role("form",name="Создание модели")
            product_form.get_by_role("button",name="Создать модель").click()
            assert product_form.locator('input[required]').first.evaluate(
                "(el) => el.validity.valueMissing"
            ),"Empty product name did not trigger native required-field validation"
            product_form.get_by_role("button",name="Отмена").click()
            assert product_form.count()==0,"Product form did not close"
            samples.append({"form":"catalog","required_name_validation":"pass"})

            page.get_by_role("searchbox",name="Поиск модели или категории").fill(
                "STUDIO_STRESS_NONEXISTENT_QUERY_987654321"
            )
            page.get_by_text("По вашему запросу ничего не найдено").wait_for(timeout=30000)
            samples.append({"state":"catalog_empty_filter","result":"pass"})
            # Simple real-browser timings, diagnostic only: CI hardware is too variable
            # for meaningful fixed performance budgets.
            (ART/"metrics.json").write_text(json.dumps({
                "seeded_customers":N,"seeded_products":N,
                "responsive_checks":len(samples),"results":samples,"api_timings":timings,
                "note":"API GET timings from CI are diagnostics only; not Lighthouse or browser render measurements"
            },indent=2,ensure_ascii=False),encoding="utf-8")
            print(f"Studio stress acceptance passed: {N} customers + {N} products; {len(samples)} checks")
        finally:
            context.close()
            browser.close()

if __name__=="__main__":
    main()
