#!/usr/bin/env python3
"""F13: F12 staff/role lifecycle with real React, HTTP Basic, Spring and PostgreSQL.

Run ONLY on the existing disposable, isolated GitHub CI Docker runner.
Reuses the F11 Playwright/HTTP helper: no parallel E2E framework, no API mocks.
Roles/users are test-only and created through real admin API or browser UI.
NO source database changes are made through SQL; SQL is read-only assertions.
"""
import base64
import importlib.util
import json
import os
import urllib.error
import urllib.request
import uuid
from pathlib import Path

from playwright.sync_api import sync_playwright

spec = importlib.util.spec_from_file_location(
    "chairx_f11_e2e_harness", Path(__file__).with_name("f11-browser-e2e.py"))
harness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)
API, sql, BASE = harness.API, harness.sql, harness.BASE
ART = Path("artifacts/f13")
ART.mkdir(parents=True, exist_ok=True)


def count(query, expected):
    actual = sql(query)
    assert actual == str(expected), f"F13 PostgreSQL count mismatch: {query}: {actual} != {expected}"


def status_as(username, password, path="/api/auth/me"):
    # Deliberately skip harness.API() which demands valid CSRF (fails for
    # revoked credentials). The server must return an actual 401.
    value = base64.b64encode((username + ":" + password).encode()).decode()
    req = urllib.request.Request(BASE + path,
                                 headers={"Authorization": "Basic " + value,
                                          "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def rejected(api, method, path, body=None, status=None):
    actual, result = api.request(method, path, body)
    if status is not None:
        assert actual == status, f"Unexpected {method} {path}: {actual}, {result}"
    else:
        assert 400 <= actual < 500, f"Unexpectedly allowed {method} {path}: {actual}"
    return actual


def browser_login(page, username, password):
    page.get_by_label("Логин").fill(username)
    page.get_by_label("Пароль", exact=True).fill(password)
    page.get_by_role("button", name="Войти", exact=True).click()


def navigate_spa(page, path):
    # Auth session intentionally exists ONLY in React memory: don't reload.
    page.evaluate("""url => {
        history.pushState({}, '', url);
        window.dispatchEvent(new PopStateEvent('popstate'));
    }""", path)


def main():
    assert os.environ.get("CI") == "true", "Only ephemeral CI runners supported"
    assert os.environ.get("CHAIRX_F11_E2E_DISPOSABLE") == \
           "I_UNDERSTAND_DATABASE_IS_DISPOSABLE", "Missing disposable DB opt-in"
    assert sql("SELECT current_database()") == "chairx"
    # Run before the existing populated business scenario in the SAME ephemeral
    # CI job; no extra CI database, image builds or Playwright installation.
    for query in (
        "SELECT count(*) FROM finance_movements",
        "SELECT count(*) FROM finance_daily_closings",
        "SELECT count(*) FROM purchases",
        "SELECT count(*) FROM sales",
        "SELECT count(*) FROM payments",
    ):
        count(query, 0)
    count("SELECT count(*) FROM security_roles WHERE code LIKE 'F13_%'", 0)

    admin_pass = os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"]
    admin = API("admin", admin_pass)
    outsider = API("catalog", os.environ["CHAIRX_CATALOG_PASSWORD"])
    permissions = admin.must("GET", "/api/admin/permissions", None, 200)
    known = {p["code"]: p for p in permissions}

    warehouse_rights = ["INVENTORY_READ", "INVENTORY_TRANSFER", "INVENTORY_RECEIVE"]
    cashier_rights = ["PAYMENTS_READ", "PAYMENTS_CREATE"]
    for right in warehouse_rights + cashier_rights:
        assert right in known, f"Missing existing server permission: {right}"
        assert not known[right]["adminOnly"], "Only selected operational rights may be used"

    # Catalog/basic technical user must not gain admin just by calling APIs.
    outsider.must("GET", "/api/admin/users", None, 403)
    outsider.must("GET", "/api/admin/roles", None, 403)
    rejected(outsider, "POST", "/api/admin/roles", {
        "code": "F13_ESCALATED", "name": "Forbidden", "permissions": ["ADMIN_ACCESS"]
    }, 403)

    # The role is created with actual ADMIN permissions catalog, never hardcoded
    # into production migrations; admin-only privilege injection is rejected.
    rejected(admin, "POST", "/api/admin/roles", {
        "code": "F13_BAD", "name": "Unauthorized privilege",
        "permissions": ["INVENTORY_READ", "ADMIN_ACCESS"]
    })
    count("SELECT count(*) FROM security_roles WHERE code='F13_BAD'", 0)
    warehouse = admin.must("POST", "/api/admin/roles", {
        "code": "F13_WAREHOUSE", "name": "F13 тестовый кладовщик",
        "permissions": warehouse_rights
    }, 201)
    cashier = admin.must("POST", "/api/admin/roles", {
        "code": "F13_CASHIER", "name": "F13 тестовый кассир",
        "permissions": cashier_rights
    }, 201)
    assert not warehouse["systemRole"] and not cashier["systemRole"]
    assert warehouse["version"] == 0
    assert cashier["version"] == 0
    count("SELECT count(*) FROM security_roles WHERE code LIKE 'F13_%'", 2)

    employee_login = "f13_warehouse_" + uuid.uuid4().hex[:10]
    original_password = "F13-Initial-Long-Password-!" + uuid.uuid4().hex[:6]
    new_password = "F13-Replaced-Long-Password-!" + uuid.uuid4().hex[:6]
    employee_name = "F13 Warehouse Test Employee"

    # Create a REAL employee in the real desktop admin UI, not a fabricated
    # API/DB fixture. The server creates the user with NO inherited roles.
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={"width": 1440, "height": 900},
                                  locale="ru-RU")
        page = ctx.new_page()
        try:
            page.goto(BASE + "/admin/users", wait_until="domcontentloaded")
            browser_login(page, "admin", admin_pass)
            page.get_by_role("heading", name="Сотрудники", exact=True, level=2).wait_for(timeout=30000)
            page.get_by_role("button", name="+ Создать сотрудника").click()
            form = page.get_by_role("form", name="Создание сотрудника")
            form.get_by_label("Логин").fill(employee_login)
            form.get_by_label("Имя сотрудника").fill(employee_name)
            form.get_by_label("Начальный пароль").fill(original_password)
            form.get_by_label("Повтор пароля").fill(original_password)
            form.get_by_role("button", name="Создать", exact=True).click()
            page.get_by_role("heading", name=employee_name, exact=True).wait_for(timeout=30000)
            assert page.url.startswith(BASE + "/admin/users/")
            page.screenshot(path=str(ART / "01-user-created-without-role.png"),
                            full_page=True)
            users = admin.must("GET", "/api/admin/users", None, 200)
            user = next(u for u in users if u["username"] == employee_login)
            assert user["roles"] == [] and user["active"] is True
            uid = user["id"]
        finally:
            ctx.close()
            browser.close()

    # Role assignment is a separate transaction and is never implicit.
    admin.must("POST", f"/api/admin/users/{uid}/roles",
               {"roleId": warehouse["id"]}, 204)
    employee = API(employee_login, original_password)
    current = employee.must("GET", "/api/auth/me", None, 200)
    assert "F13_WAREHOUSE" in current["roles"]
    assert set(warehouse_rights) <= set(current["permissions"])
    assert "ADMIN" not in current["roles"] and "FINANCE_READ" not in current["permissions"]

    # Real API authorization, not merely hidden sidebar buttons.
    employee.must("GET", "/api/inventory/overview", None, 200)
    employee.must("GET", "/api/warehouses", None, 200)
    for denied in ("/api/finance/accounts", "/api/admin/users",
                   "/api/admin/roles", "/api/customers", "/api/purchases"):
        employee.must("GET", denied, None, 403)
    rejected(employee, "POST", "/api/finance/transfers",
             {"transferId": str(uuid.uuid4()), "from": "CASH",
              "to": "BANK", "amount": 1}, 403)
    rejected(employee, "POST", "/api/admin/users", {
        "username": "f13_not_created", "displayName": "blocked",
        "password": "NotAnEmployeePassword1"}, 403)
    count("SELECT count(*) FROM app_users WHERE username='f13_not_created'", 0)

    # Another custom business role, never equivalent to ADMIN, can be added
    # and removed independently; permissions are the union of assigned roles.
    admin.must("POST", f"/api/admin/users/{uid}/roles",
               {"roleId": cashier["id"]}, 204)
    multiple = employee.must("GET", "/api/auth/me", None, 200)
    assert "F13_CASHIER" in multiple["roles"]
    assert "PAYMENTS_CREATE" in multiple["permissions"]
    employee.must("GET", "/api/admin/users", None, 403)
    admin.must("DELETE", f"/api/admin/users/{uid}/roles/{cashier['id']}", None, 204)
    single = employee.must("GET", "/api/auth/me", None, 200)
    assert "F13_WAREHOUSE" in single["roles"] and "F13_CASHIER" not in single["roles"]
    assert "PAYMENTS_CREATE" not in single["permissions"]

    # Server enforces optimistic concurrency on role permission updates.
    updated = admin.must("PUT", f"/api/admin/roles/{warehouse['id']}", {
        "name": "F13 Test Warehouse (restricted)",
        "permissions": ["INVENTORY_READ", "INVENTORY_RECEIVE"],
        "expectedVersion": 0,
        "reason": "F13 automated permission revocation test"
    }, 200)
    assert updated["version"] == 1 and "INVENTORY_TRANSFER" not in updated["permissions"]
    rejected(admin, "PUT", f"/api/admin/roles/{warehouse['id']}", {
        "name": "Unexpected stale write",
        "permissions": warehouse_rights,
        "expectedVersion": 0,
        "reason": "F13 stale version should fail"
    }, 409)
    assert admin.must("GET", f"/api/admin/roles/{warehouse['id']}", None, 200)["version"] == 1
    after_update = employee.must("GET", "/api/auth/me", None, 200)
    assert "INVENTORY_TRANSFER" not in after_update["permissions"]
    rejected(employee, "POST", "/api/inventory/transfers", {
        "transferId": str(uuid.uuid4()), "fromWarehouseId": str(uuid.uuid4()),
        "toWarehouseId": str(uuid.uuid4()), "productVariantId": str(uuid.uuid4()),
        "quantity": 1
    }, 403)

    # Confirm warehouse interface is accessible to a restricted user on a
    # mobile viewport, without production data or frontend API mocks.
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={"width": 390, "height": 844},
                                  is_mobile=True, device_scale_factor=1,
                                  has_touch=True, locale="ru-RU")
        page = ctx.new_page()
        try:
            page.goto(BASE + "/inventory", wait_until="domcontentloaded")
            browser_login(page, employee_login, original_password)
            page.get_by_role("heading", name="Склады", exact=True, level=2).wait_for(timeout=30000)
            assert not page.get_by_role("heading", name="Доступ запрещён").count()
            page.screenshot(path=str(ART / "02-restricted-mobile-warehouse.png"),
                            full_page=True)
            navigate_spa(page, "/finance")
            page.get_by_text("Доступ запрещён", exact=False).first.wait_for(timeout=30000)
            page.screenshot(path=str(ART / "03-restricted-finance.png"),
                            full_page=True)
        finally:
            ctx.close()
            browser.close()

    # Password hash never exposed in the list or audit API. New password
    # invalidates old HTTP Basic, immediately and across separate requests.
    admin.must("POST", f"/api/admin/users/{uid}/reset-password",
               {"newPassword": new_password}, 204)
    assert status_as(employee_login, original_password) == 401
    assert status_as(employee_login, new_password) == 200
    reset_list = admin.must("GET", "/api/admin/users", None, 200)
    view = next(u for u in reset_list if u["id"] == uid)
    payload = json.dumps(view)
    assert "password" not in payload.lower()
    assert original_password not in payload and new_password not in payload

    admin.must("PATCH", f"/api/admin/users/{uid}/deactivate", None, 204)
    assert status_as(employee_login, new_password) == 401
    admin.must("PATCH", f"/api/admin/users/{uid}/activate", None, 204)
    assert status_as(employee_login, new_password) == 200
    admin.must("DELETE", f"/api/admin/users/{uid}/roles/{warehouse['id']}", None, 204)
    assert status_as(employee_login, new_password, "/api/inventory/overview") == 403
    final = admin.must("GET", f"/api/admin/users/{uid}", None, 200)
    assert final["roles"] == [] and final["active"] is True
    audit = admin.must("GET", f"/api/admin/audit?targetId={uid}&page=0&size=50", None, 200)
    actions = {item["action"] for item in audit["items"]}
    assert {"USER_PASSWORD_RESET", "USER_DEACTIVATED", "USER_ACTIVATED"} <= actions
    assert original_password not in json.dumps(audit)
    assert new_password not in json.dumps(audit)
    count("SELECT count(*) FROM finance_movements", 0)
    count("SELECT count(*) FROM purchases", 0)
    count("SELECT count(*) FROM sales", 0)
    print("F13: real admin React user creation, role lifecycle, passwords,"
          " isolated security APIs, revocation, optimistic version and mobile"
          " Chromium warehouse access all passed (synthetic users only).")


if __name__ == "__main__":
    main()
