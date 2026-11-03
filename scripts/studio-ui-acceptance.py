#!/usr/bin/env python3
"""Real Chromium responsive acceptance on the disposable F11 CI stack; read-only."""
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173"
ART = Path("artifacts/studio-ui")
ART.mkdir(parents=True, exist_ok=True)
ROUTES = [
    ("/", "Добро пожаловать"), ("/sales", "Продажи"),
    ("/deliveries", "Доставки"), ("/inventory", "Склады"),
    ("/customers", "Клиенты"), ("/catalog", "Каталог товаров"),
    ("/purchases", "Закупки"), ("/purchases/receipts", "Поступления"),
    ("/purchases/suppliers", "Поставщики"), ("/purchases/payments", "Оплаты"),
    ("/returns", "Возвраты"), ("/returns/defects", "Брак"),
    ("/daily-closing", "Закрытие дня"), ("/finance", "Финансы"),
    ("/finance/analytics", "Аналитика"), ("/admin/users", "Сотрудники"),
    ("/admin/roles", "Роли и разрешения"),
]
WIDTHS = (320, 375, 768, 1024, 1440)

def navigate_spa(page, route):
    # Auth is stored in React memory: full reload would log out.
    page.evaluate("""route => {
        history.pushState({}, '', route);
        dispatchEvent(new PopStateEvent('popstate'));
    }""", route)

def assert_layout(page, route, width):
    layout = page.evaluate("""() => ({
        viewport: document.documentElement.clientWidth,
        document: document.documentElement.scrollWidth,
        offenders: [...document.querySelectorAll('body *')]
            .filter(el => el.getBoundingClientRect().right >
                document.documentElement.clientWidth + 2)
            .slice(0, 8)
            .map(el => ({
                tag: el.tagName,
                className: typeof el.className === 'string'
                    ? el.className.slice(0, 100) : '',
                right: Math.round(el.getBoundingClientRect().right)
            }))
    })""")
    assert layout["document"] <= layout["viewport"] + 1, (
        f"{route}: document overflows at {width}px: {layout}"
    )
    return layout

def test_mobile_menu(page):
    page.set_viewport_size({"width": 375, "height": 812})
    opener = page.get_by_role("button", name="Открыть меню")
    opener.click()
    modal = page.get_by_role("dialog", name="Мобильная навигация")
    modal.wait_for(state="visible")
    close = modal.get_by_role("button", name="Закрыть боковое меню")
    assert close.evaluate("(el) => el === document.activeElement"), (
        "Initial focus did not enter mobile menu"
    )
    page.keyboard.press("Shift+Tab")
    assert modal.evaluate("(el) => el.contains(document.activeElement)"), (
        "Shift+Tab escaped the mobile dialog"
    )
    page.keyboard.press("Tab")
    assert modal.evaluate("(el) => el.contains(document.activeElement)"), (
        "Tab escaped the mobile dialog"
    )
    page.screenshot(path=str(ART / "mobile-menu-375.png"), full_page=True)
    page.keyboard.press("Escape")
    modal.wait_for(state="detached")
    assert opener.evaluate("(el) => el === document.activeElement"), (
        "Focus did not return to mobile-menu opener"
    )

def main():
    assert os.environ.get("CI") == "true", "Only disposable CI environments"
    assert os.environ.get("CHAIRX_F11_E2E_DISPOSABLE") == (
        "I_UNDERSTAND_DATABASE_IS_DISPOSABLE"
    )
    observations = []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(
            viewport={"width": 1440, "height": 900},
            locale="ru-RU", reduced_motion="reduce"
        )
        page = context.new_page()
        try:
            page.goto(BASE + "/sales", wait_until="domcontentloaded")
            page.get_by_label("Логин").fill("admin")
            page.get_by_label("Пароль", exact=True).fill(
                os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"]
            )
            page.get_by_role("button", name="Войти", exact=True).click()
            page.get_by_role("heading", name="Продажи", exact=True).wait_for(
                timeout=30000
            )
            test_mobile_menu(page)
            for route, heading in ROUTES:
                navigate_spa(page, route)
                page.get_by_role(
                    "heading", name=heading,
                    exact=route != "/"
                ).first.wait_for(timeout=30000)
                for width in WIDTHS:
                    page.set_viewport_size({"width": width, "height": 900})
                    page.wait_for_timeout(150)
                    metrics = assert_layout(page, route, width)
                    observations.append({"route": route, "width": width, **metrics})
                    if width in (375, 1440):
                        slug = route.strip("/").replace("/", "-") or "home"
                        page.screenshot(path=str(ART / f"{slug}-{width}.png"),
                                        full_page=True)
            (ART / "acceptance.json").write_text(
                json.dumps({"routes": len(ROUTES), "widths": WIDTHS,
                            "observations": observations,
                            "keyboard_mobile_menu": "pass"},
                           ensure_ascii=False, indent=2), encoding="utf-8"
            )
            print(f"Passed {len(observations)} responsive checks + mobile menu focus")
        finally:
            context.close()
            browser.close()

if __name__ == "__main__":
    main()
