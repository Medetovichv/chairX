#!/usr/bin/env python3
"""Create one customer through the sale drawer on the disposable F11 stack only."""
import importlib.util
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).parent
ART = Path("artifacts/studio-quickcreate")


def main():
    assert os.environ.get("CI") == "true"
    assert os.environ.get("CHAIRX_F11_E2E_DISPOSABLE") == "I_UNDERSTAND_DATABASE_IS_DISPOSABLE"
    spec = importlib.util.spec_from_file_location("quickcreate_harness", ROOT / "f11-browser-e2e.py")
    harness = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(harness)
    assert harness.sql("SELECT current_database()") == "chairx"
    assert int(harness.sql("SELECT count(*) FROM finance_daily_closings")) >= 1
    ART.mkdir(parents=True, exist_ok=True)
    password = os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"]
    api = harness.API("admin", password)
    name = "STUDIO_QUICKCREATE_ACCEPTANCE"
    before = harness.sql("SELECT count(*) FROM sales")
    observations = []
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()
        try:
            page.goto(harness.BASE + "/sales?quick=new")
            page.get_by_label("Логин", exact=True).fill("admin")
            page.get_by_label("Пароль", exact=True).fill(password)
            page.get_by_role("button", name="Войти", exact=True).click()
            form = page.get_by_role("form", name="Новый черновик продажи")
            form.wait_for(timeout=30000)
            opener = form.get_by_role("button", name="+ Новый клиент", exact=True)
            # Desktop and mobile child-dialog keyboard ownership, before any write.
            for width in (1440, 1280, 1024, 768, 375, 320):
                print(f"Quick-create acceptance at {width}px", flush=True)
                page.set_viewport_size({"width": width, "height": 900})
                opener.click()
                child = page.get_by_role("dialog", name="Новый клиент для продажи", exact=True)
                close = child.get_by_role("button", name="Закрыть", exact=True)
                expect(close).to_be_focused()
                page.keyboard.press("Shift+Tab")
                expect(child.get_by_role("button", name="Отмена", exact=True)).to_be_focused()
                page.keyboard.press("Tab")
                expect(close).to_be_focused()
                page.screenshot(path=str(ART / f"customer-{width}.png"))
                page.keyboard.press("Escape")
                expect(child).to_have_count(0)
                expect(opener).to_be_focused()
                expect(form).to_be_visible()
                metrics = page.evaluate("""() => {
                    const footer=document.querySelector('[data-quick-create-footer]');
                    const filters=document.querySelector('[aria-label="Фильтры продаж"]');
                    return {width:innerWidth, document:document.documentElement.scrollWidth,
                        footerBottom:footer.getBoundingClientRect().bottom,
                        filterOverflow:filters.scrollWidth-filters.clientWidth};
                }""")
                assert metrics["document"] <= width + 1, metrics
                assert metrics["footerBottom"] <= 901, metrics
                assert metrics["filterOverflow"] <= 2, metrics
                observations.append(metrics)
                page.screenshot(path=str(ART / f"sale-{width}.png"))
            # A real browser POST must create and select exactly one customer.
            opener.click()
            child = page.get_by_role("dialog", name="Новый клиент для продажи", exact=True)
            child.get_by_label("Имя", exact=True).fill(name)
            child.get_by_label("Основной телефон", exact=True).fill("+996700987321")
            with page.expect_response(lambda response:
                    response.url.endswith("/api/customers") and response.request.method == "POST") as created:
                child.get_by_role("button", name="Сохранить клиента", exact=True).click()
            response = created.value
            assert response.status == 201, response.status
            customer = response.json()
            expect(child).to_have_count(0)
            expect(form.get_by_text("Выбран: " + name, exact=False)).to_be_visible()
            assert api.must("GET", "/api/customers/" + customer["id"], None, 200)["fullName"] == name
            matches = api.must("GET", "/api/customers/search?query=STUDIO_QUICKCREATE_ACCEPTANCE", None, 200)
            assert len([item for item in matches if item["fullName"] == name]) == 1
            assert harness.sql("SELECT count(*) FROM sales") == before
            # Cancelling the discard prompt retains the draft; accepting closes it.
            form.get_by_label("Комментарий продажи", exact=True).fill("Сохранить введённые данные")
            page.once("dialog", lambda dialog: dialog.dismiss())
            page.keyboard.press("Escape")
            expect(form).to_be_visible()
            expect(form.get_by_label("Комментарий продажи", exact=True)).to_have_value("Сохранить введённые данные")
            page.once("dialog", lambda dialog: dialog.accept())
            page.keyboard.press("Escape")
            expect(form).to_have_count(0)
            assert harness.sql("SELECT count(*) FROM sales") == before
            (ART / "results.json").write_text(json.dumps({
                "realBackend": True, "customerCreatedAndSelected": True,
                "saleWrites": 0, "discardProtection": "passed", "layouts": observations,
                "physicalVirtualKeyboard": "not tested"
            }, ensure_ascii=False, indent=2), encoding="utf-8")
        finally:
            context.close()
            browser.close()


if __name__ == "__main__":
    main()
