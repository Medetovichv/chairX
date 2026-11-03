#!/usr/bin/env python3
"""Browser checks for network failure/retry + measured list readiness.

Runs only after the disposable F11 populated and stress acceptance steps.
Routes only the browser's GET requests; real database/finance is never modified.
"""
import json
import os
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE="http://127.0.0.1:5173"
ART=Path("artifacts/studio-resilience")
ART.mkdir(parents=True,exist_ok=True)
CASES=(
    ("/catalog","Каталог товаров","Поиск модели или категории",
     "**/api/products?**","По вашему запросу ничего не найдено"),
    ("/customers","Клиенты","Поиск клиентов",
     "**/api/customers/overview?**","Клиенты не найдены"),
)

def navigate(page,route):
    page.evaluate("""path => {
        history.pushState({}, '', path);
        dispatchEvent(new PopStateEvent('popstate'));
    }""",route)

def main():
    assert os.environ.get("CI")=="true"
    assert os.environ.get("CHAIRX_F11_E2E_DISPOSABLE")=="I_UNDERSTAND_DATABASE_IS_DISPOSABLE"
    results=[]
    with sync_playwright() as pw:
        browser=pw.chromium.launch(headless=True)
        context=browser.new_context(viewport={"width":375,"height":900},locale="ru-RU",
                                    reduced_motion="reduce")
        page=context.new_page()
        try:
            page.goto(BASE+"/catalog",wait_until="domcontentloaded")
            page.get_by_label("Логин").fill("admin")
            page.get_by_label("Пароль",exact=True).fill(
                os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"]
            )
            page.get_by_role("button",name="Войти",exact=True).click()
            page.get_by_role("heading",name="Каталог товаров",exact=True).wait_for(timeout=30000)
            for route,heading,search_label,pattern,empty_label in CASES:
                start=time.perf_counter()
                navigate(page,route)
                page.get_by_role("heading",name=heading,exact=True).first.wait_for(timeout=30000)
                # Real response-backed content exists only after the list query completes.
                region_label="Список моделей" if route=="/catalog" else "Список клиентов"
                page.get_by_role("region",name=region_label,exact=True).wait_for(timeout=30000)
                ready_ms=round((time.perf_counter()-start)*1000,1)

                # A fresh search key prevents cache from hiding the simulated outage.
                def fail_get(request):
                    request.fulfill(status=503,content_type="application/json",
                                    body='{"message":"Temporary CI-only outage"}')
                page.route(pattern,fail_get)
                field=page.get_by_role("searchbox",name=search_label)
                field.fill("STUDIO_RESILIENCE_NO_MATCH_783991")
                retry=page.get_by_role("button",name="Повторить")
                retry.wait_for(timeout=30000)
                alert=page.get_by_role("alert").filter(
                    has=page.get_by_role("heading",name="Не удалось загрузить данные")
                )
                alert.wait_for(timeout=10000)
                page.screenshot(path=str(ART/f"{route[1:]}-error-375.png"),full_page=True)
                page.unroute(pattern)
                retry.click()
                page.get_by_text(empty_label,exact=True).wait_for(timeout=30000)
                page.screenshot(path=str(ART/f"{route[1:]}-recovered-375.png"),full_page=True)
                results.append({"route":route,"list_ready_ms":ready_ms,
                                "network_error":"visible","retry":"recovers",
                                "empty_state":"visible"})
            (ART/"report.json").write_text(json.dumps({
                "cases":results,
                "note":"Timings are single CI samples, not reliable user-perceived web vitals."
            },indent=2,ensure_ascii=False),encoding="utf-8")
            print(f"Studio resilience accepted: {len(results)} error/retry/empty-state journeys")
        finally:
            context.close()
            browser.close()

if __name__=="__main__":
    main()
