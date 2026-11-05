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
WIDTHS = (320, 375, 768, 1024, 1280, 1440)

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

def test_sale_quick_create(page):
    """Exercise the actual embedded form, not just the underlying sales list."""
    navigate_spa(page, "/sales")
    page.get_by_role("heading", name="Продажи", exact=True).wait_for(
        timeout=30000
    )
    page.get_by_role("link", name="Новая продажа").first.click()
    drawer = page.get_by_role(
        "dialog", name="Быстрый просмотр: Новая продажа"
    )
    drawer.wait_for(state="visible")
    form = drawer.get_by_role("form", name="Новый черновик продажи")
    footer = drawer.get_by_role("region", name="Итог и сохранение продажи")
    scroller = drawer.get_by_label("Прокручиваемое содержимое панели")
    save = footer.get_by_role("button", name="Сохранить черновик")
    assert save.get_attribute("form") == form.get_attribute("id"), (
        "Sticky save action is detached from the actual sale form"
    )
    assert not scroller.evaluate(
        "(scroller, footer) => scroller.contains(footer)",
        footer.element_handle()
    ), "Sticky checkout is incorrectly nested inside the scrolling form"
    close = drawer.get_by_role("button", name="Назад к списку")
    assert close.evaluate("(el) => el === document.activeElement"), (
        "Focus did not enter the sale Quick View"
    )
    for width in WIDTHS:
        page.set_viewport_size({"width": width, "height": 900})
        page.wait_for_timeout(150)
        assert_layout(page, "/sales?quick=new", width)
        bounds = drawer.bounding_box()
        checkout = footer.bounding_box()
        assert bounds and checkout, f"Quick Create not visible at {width}px"
        assert drawer.evaluate(
            "(el) => el.scrollWidth <= el.clientWidth + 1"
        ), f"Quick Create horizontal overflow at {width}px"
        assert checkout["y"] >= 0 and (
            checkout["y"] + checkout["height"] <= 901
        ), f"Quick Create checkout clipped at {width}px: {checkout}"
        if width <= 1024:
            assert bounds["x"] <= 1 and (
                bounds["x"] + bounds["width"] >= width - 1
            ), f"Quick Create must be full-screen at {width}px: {bounds}"
        else:
            assert bounds["x"] > 0, (
                f"Quick Create must preserve a list beside drawer at {width}px"
            )
        page.screenshot(
            path=str(ART / f"sale-quick-create-{width}.png"),
            full_page=True
        )
    # A nested customer dialog must own keyboard focus without closing its
    # parent sale drawer; no customer creation request is submitted here.
    page.set_viewport_size({"width": 375, "height": 900})
    opener = form.get_by_role("button", name="+ Новый клиент")
    opener.click()
    customer_dialog = page.get_by_role(
        "dialog", name="Новый клиент для продажи"
    )
    customer_dialog.wait_for(state="visible")
    customer_close = customer_dialog.get_by_role("button", name="Закрыть")
    assert customer_close.evaluate(
        "(el) => el === document.activeElement"
    ), "Focus did not enter nested customer dialog"
    page.keyboard.press("Shift+Tab")
    customer_cancel = customer_dialog.get_by_role("button", name="Отмена")
    assert customer_cancel.evaluate(
        "(el) => el === document.activeElement"
    ), "Shift+Tab did not wrap inside customer dialog"
    page.keyboard.press("Tab")
    assert customer_close.evaluate(
        "(el) => el === document.activeElement"
    ), "Tab did not return to first customer dialog control"
    page.screenshot(
        path=str(ART / "sale-quick-create-customer-dialog-375.png"),
        full_page=True
    )
    page.keyboard.press("Escape")
    customer_dialog.wait_for(state="detached")
    drawer.wait_for(state="visible")
    assert opener.evaluate(
        "(el) => el === document.activeElement"
    ), "Focus did not return to new-customer opener"
    assert form.is_visible(), "Escape closed the parent sale drawer"
    close.click()
    drawer.wait_for(state="detached")


def test_delivery_quick_create(page):
    """Check the real delivery drawer at every shipped responsive breakpoint."""
    page.set_viewport_size({"width": 1440, "height": 900})
    navigate_spa(page, "/deliveries")
    page.get_by_role("heading", name="Доставки", exact=True).first.wait_for(
        timeout=30000
    )
    page.get_by_role("link", name="Оформить доставку").first.click()
    drawer = page.get_by_role(
        "dialog", name="Быстрый просмотр: Новая доставка"
    )
    drawer.wait_for(state="visible")
    form = drawer.get_by_role("form", name="Оформить доставку")
    footer = drawer.get_by_role("region", name="Итог и сохранение продажи")
    scroller = drawer.get_by_label("Прокручиваемое содержимое панели")
    create = footer.get_by_role("button", name="Создать доставку")
    assert create.get_attribute("form") == form.get_attribute("id"), (
        "Delivery save button must submit its own embedded form"
    )
    assert not scroller.evaluate(
        "(node, footer) => node.contains(footer)", footer.element_handle()
    ), "Delivery footer must remain outside scrolling content"
    close = drawer.get_by_role("button", name="Назад к списку")
    assert close.evaluate("(el) => el === document.activeElement"), (
        "Delivery drawer must receive initial keyboard focus"
    )
    for width in WIDTHS:
        page.set_viewport_size({"width": width, "height": 900})
        page.wait_for_timeout(150)
        assert_layout(page, "/deliveries?quick=new", width)
        bounds, checkout = drawer.bounding_box(), footer.bounding_box()
        assert bounds and checkout, f"Delivery Quick Create hidden at {width}px"
        assert drawer.evaluate(
            "(node) => node.scrollWidth <= node.clientWidth + 1"
        ), f"Delivery Quick Create overflows horizontally at {width}px"
        assert checkout["y"] >= 0 and (
            checkout["y"] + checkout["height"] <= 901
        ), f"Delivery footer clipped at {width}px: {checkout}"
        if width <= 1024:
            assert bounds["x"] <= 1 and (
                bounds["x"] + bounds["width"] >= width - 1
            ), f"Delivery drawer must fill viewport at {width}px: {bounds}"
        else:
            assert bounds["x"] > 0, (
                f"Delivery drawer must preserve list at {width}px: {bounds}"
            )
        page.screenshot(
            path=str(ART / f"delivery-quick-create-{width}.png"),
            full_page=True
        )
    # Form is initially incomplete. No request to create a delivery is sent.
    assert create.is_disabled(), "Incomplete delivery must not be submittable"
    close.click()
    drawer.wait_for(state="detached")


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
    modal.evaluate("""dialog => {
        const nodes = [...dialog.querySelectorAll(
            'button:not([disabled]), a[href], input:not([disabled]), [tabindex]:not([tabindex="-1"])'
        )].filter(el => el.getClientRects().length);
        nodes[0].focus();
    }""")
    page.keyboard.press("Shift+Tab")
    assert modal.evaluate("""dialog => {
        const nodes = [...dialog.querySelectorAll(
            'button:not([disabled]), a[href], input:not([disabled]), [tabindex]:not([tabindex="-1"])'
        )].filter(el => el.getClientRects().length);
        return document.activeElement === nodes[nodes.length - 1];
    }"""), "Shift+Tab did not wrap to final mobile-menu control"
    page.keyboard.press("Tab")
    assert modal.evaluate("""dialog => {
        const nodes = [...dialog.querySelectorAll(
            'button:not([disabled]), a[href], input:not([disabled]), [tabindex]:not([tabindex="-1"])'
        )].filter(el => el.getClientRects().length);
        return document.activeElement === nodes[0];
    }"""), "Tab did not wrap back to first mobile-menu control"
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
                if route == "/":
                    hero=page.get_by_role("region", name="Обзор бизнеса")
                    hero.wait_for(state="visible",timeout=30000)
                    hero.get_by_role("heading", name="Контролируйте работу бизнеса в одном месте").wait_for()
                    page.get_by_role("region", name="Заказы за 7 дней").wait_for()
                    page.get_by_role("link", name="Новая продажа").first.wait_for()
                for width in WIDTHS:
                    page.set_viewport_size({"width": width, "height": 900})
                    page.wait_for_timeout(150)
                    metrics = assert_layout(page, route, width)
                    if route == "/purchases" and width == 375:
                        preset = page.get_by_role(
                            "button", name="Последние 7 дней + незавершённые"
                        )
                        bounds = preset.bounding_box()
                        assert bounds is not None and (
                            bounds["x"] >= 0 and bounds["x"] + bounds["width"] <= 376
                        ), f"Purchase date-preset button clipped at 375px: {bounds}"
                        assert preset.evaluate(
                            "(el) => el.scrollWidth <= el.clientWidth + 1"
                        ), "Purchase date-preset label is clipped"
                    observations.append({"route": route, "width": width, **metrics})
                    if width in (375, 1440):
                        slug = route.strip("/").replace("/", "-") or "home"
                        page.screenshot(path=str(ART / f"{slug}-{width}.png"),
                                        full_page=True)
            test_sale_quick_create(page)
            test_delivery_quick_create(page)
            (ART / "acceptance.json").write_text(
                json.dumps({"routes": len(ROUTES), "widths": WIDTHS,
                            "observations": observations,
                            "keyboard_mobile_menu": "pass",
                            "sale_quick_create_widths": WIDTHS,
                            "delivery_quick_create_widths": WIDTHS},
                           ensure_ascii=False, indent=2), encoding="utf-8"
            )
            print(f"Passed {len(observations)} responsive checks + mobile menu focus")
        finally:
            context.close()
            browser.close()

if __name__ == "__main__":
    main()
