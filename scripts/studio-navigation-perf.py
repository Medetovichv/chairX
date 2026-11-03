#!/usr/bin/env python3
"""Authenticated Chromium SPA navigation diagnostics on the disposable ChairX stack.

Collects measured transitions, API requests, dev-module fetches and long tasks.
No arbitrary speed budgets and no production/retained database writes.
These are Vite DEV-server measurements; not Lighthouse/CrUX/Web Vitals.
"""
import json
import os
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173"
ART = Path("artifacts/studio-navigation")
ART.mkdir(parents=True, exist_ok=True)

# Link text is from main navigation; a real click exercises React Router
# and chunk loading instead of manually dispatching popstate.
JOURNEY = [
    ("Финансы", "Финансы"),
    ("Продажи", "Продажи"),
    ("Финансы", "Финансы"),
    ("Администрирование", "Сотрудники"),
    ("Каталог", "Каталог товаров"),
]

def main():
    assert os.environ.get("CI") == "true"
    assert os.environ.get("CHAIRX_F11_E2E_DISPOSABLE") == (
        "I_UNDERSTAND_DATABASE_IS_DISPOSABLE"
    )
    samples = []
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        context = browser.new_context(
            viewport={"width": 1440, "height": 900}, locale="ru-RU",
            reduced_motion="reduce"
        )
        # Observe main-thread long tasks on the actual Vite app, without adding
        # dependencies or changing the application being measured.
        context.add_init_script("""(() => {
            window.__chairxLongTasks = [];
            try {
                new PerformanceObserver(list => {
                    for (const entry of list.getEntries()) {
                        window.__chairxLongTasks.push({
                            start: Math.round(entry.startTime),
                            duration: Math.round(entry.duration)
                        });
                    }
                }).observe({type:'longtask', buffered:true});
            } catch (_) {}
        })()""")
        page = context.new_page()
        requests = []
        responses = []
        page.on("request", lambda req: requests.append({
            "url": req.url, "type": req.resource_type,
        }) if req.url.startswith(BASE) else None)
        page.on("response", lambda resp: responses.append({
            "url": resp.url, "status": resp.status,
        }) if resp.url.startswith(BASE + "/api/") else None)
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
            navigation = page.get_by_role("navigation", name="Разделы ChairX")
            # First and second visits to Finance should show cold/warm
            # module-fetch differences within a single authenticated session.
            for step, (link_name, heading) in enumerate(JOURNEY, start=1):
                start_requests = len(requests)
                start_responses = len(responses)
                start = time.perf_counter()
                navigation.get_by_role("link", name=link_name, exact=True).click()
                page.get_by_role("heading", name=heading, exact=True).first.wait_for(
                    timeout=30000
                )
                heading_ms = round((time.perf_counter() - start) * 1000, 1)
                # Small consistent observation window for network diagnostics,
                # NOT a claim that all background requests have completed.
                page.wait_for_timeout(300)
                recent_requests = requests[start_requests:]
                recent_responses = responses[start_responses:]
                api_requests = [r["url"].split("?", 1)[0].replace(BASE, "")
                                for r in recent_requests
                                if "/api/" in r["url"]]
                module_requests = [r["url"].split("?", 1)[0].replace(BASE, "")
                                   for r in recent_requests if r["url"].endswith(".js")
                                   or ".js?" in r["url"]]
                bad_responses = [r for r in recent_responses if r["status"] >= 400]
                assert not bad_responses, (
                    f"{link_name} caused HTTP errors: {bad_responses}"
                )
                samples.append({
                    "step": step, "section": link_name,
                    "heading": heading, "heading_visible_ms": heading_ms,
                    "api_request_count": len(api_requests),
                    "api_paths": api_requests,
                    "script_request_count": len(module_requests),
                    "script_paths": module_requests[:20],
                    "api_http_errors": bad_responses,
                })
            assert len(samples) == len(JOURNEY)
            # This observes browser resource fetches only on the Vite dev
            # server. Do not confuse it with production CDN cache behavior.
            diagnostic = page.evaluate("""() => ({
                document_fcp_ms:
                    performance.getEntriesByType('paint')
                        .find(e => e.name === 'first-contentful-paint')?.startTime ?? null,
                document_dom_content_loaded_ms:
                    performance.getEntriesByType('navigation')[0]?.domContentLoadedEventEnd ?? null,
                long_task_count: (window.__chairxLongTasks || []).length,
                long_task_duration_ms:
                    (window.__chairxLongTasks || [])
                        .reduce((sum, e) => sum + e.duration, 0),
            })""")
            output = {
                "environment": "Chromium / authenticated Vite dev / disposable PostgreSQL",
                "disclaimer": "Transition and long-task diagnostics; document FCP includes login. Not production Core Web Vitals or Lighthouse.",
                "transitions": samples,
                "document_diagnostics": diagnostic,
            }
            (ART / "navigation.json").write_text(
                json.dumps(output, indent=2, ensure_ascii=False),
                encoding="utf-8"
            )
            print("Studio navigation diagnostics:", json.dumps(
                [{"section": x["section"], "ms": x["heading_visible_ms"],
                  "api_requests": x["api_request_count"],
                  "script_requests": x["script_request_count"]}
                 for x in samples], ensure_ascii=False
            ))
        finally:
            context.close()
            browser.close()

if __name__ == "__main__":
    main()
