#!/usr/bin/env python3
"""Read-only visual and keyboard acceptance on the POPULATED disposable F11 stack.

Run after f11-operations-e2e.py. Only creates screenshots and JSON reports.
Never run against retained developer or production data.
"""
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE="http://127.0.0.1:5173"
ART=Path("artifacts/studio-populated")
ART.mkdir(parents=True,exist_ok=True)
WIDTHS=(375,768,1440)
# The F11 operation journey has already created sales, deliveries, stock,
# a completed purchase, customer, return/refund and a closed-day report.
ROUTES=[
    ("/sales?operational=false","Продажи"),
    ("/deliveries?operational=false","Доставки"),
    ("/inventory","Склады"),
    ("/purchases","Закупки"),
    ("/customers","Клиенты"),
    ("/catalog","Каталог товаров"),
    ("/returns","Возвраты"),
    ("/finance","Финансы"),
]

def goto(page,route):
    # Auth lives in React memory; no full reload after login.
    page.evaluate("""route => {
        history.pushState({}, '', route);
        dispatchEvent(new PopStateEvent('popstate'));
    }""",route)

def measure(page):
    return page.evaluate("""() => {
        const viewport=document.documentElement.clientWidth;
        const candidates=[...document.querySelectorAll('main button, main a, main input, main select')];
        const clipped=candidates.filter(el=>{
            const r=el.getBoundingClientRect();
            const st=getComputedStyle(el);
            if(!r.width || !r.height || st.visibility==='hidden') return false;
            if(r.right>viewport+2 || r.left< -2) return true;
            return !['hidden','clip'].includes(st.overflowX)
                && el.scrollWidth>el.clientWidth+2
                && st.whiteSpace==='nowrap';
        });
        return {
            viewport,
            documentWidth:document.documentElement.scrollWidth,
            clippedControls:clipped.slice(0,10).map(el=>({
                text:(el.textContent||el.getAttribute('aria-label')||'').trim().slice(0,90),
                tag:el.tagName,
                rect:Math.round(el.getBoundingClientRect().right),
                content:el.scrollWidth,
                client:el.clientWidth,
            }))
        };
    }""")

def main():
    assert os.environ.get("CI")=="true", "Only CI disposable data"
    assert os.environ.get("CHAIRX_F11_E2E_DISPOSABLE")=="I_UNDERSTAND_DATABASE_IS_DISPOSABLE"
    results=[]
    with sync_playwright() as pw:
        browser=pw.chromium.launch(headless=True)
        context=browser.new_context(viewport={"width":1440,"height":900},locale="ru-RU",reduced_motion="reduce")
        page=context.new_page()
        try:
            page.goto(BASE+"/sales",wait_until="domcontentloaded")
            page.get_by_label("Логин").fill("admin")
            page.get_by_label("Пароль",exact=True).fill(os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"])
            page.get_by_role("button",name="Войти",exact=True).click()
            page.get_by_role("heading",name="Продажи",exact=True).wait_for(timeout=30000)
            for route,heading in ROUTES:
                goto(page,route)
                page.get_by_role("heading",name=heading,exact=True).first.wait_for(timeout=30000)
                # Allow real asynchronous list query to settle; never assert a loading skeleton.
                page.locator('main [aria-label^="Загружаем"]').first.wait_for(state="hidden",timeout=30000)
                for width in WIDTHS:
                    page.set_viewport_size({"width":width,"height":900})
                    page.wait_for_timeout(180)
                    result=measure(page)
                    assert result["documentWidth"]<=result["viewport"]+1,(
                        f"Populated {route} page overflow at {width}: {result}")
                    assert not result["clippedControls"],(
                        f"Populated {route} clipped controls at {width}: {result}")
                    results.append({"route":route,"width":width,**result})
                    slug=route.split("?")[0].strip("/") or "home"
                    page.screenshot(path=str(ART/f"{slug}-{width}.png"),full_page=True)
            # Open actual right-side sale/delivery panels, not fabricated mock content.
            from urllib.parse import quote
            import importlib.util
            harness_path=Path(__file__).with_name("f11-browser-e2e.py")
            spec=importlib.util.spec_from_file_location("chairx_harness_populated",harness_path)
            harness=importlib.util.module_from_spec(spec)
            spec.loader.exec_module(harness)
            api=harness.API("admin",os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"])
            sale=api.must("GET","/api/sales?operational=false&page=0&size=20",None,200)
            delivery=api.must("GET","/api/deliveries?operational=false&page=0&size=20",None,200)
            for name,route,record in (
                ("sale","/sales?operational=false",sale),
                ("delivery","/deliveries?operational=false",delivery),
            ):
                records=record.get("items",[])
                assert records,f"No populated {name} records available for drawer acceptance"
                selection=records[0]["id"]
                target=route+"&quick="+quote(selection)
                goto(page,target)
                dialog=page.get_by_role("dialog",name=("Быстрый просмотр:" if name=="sale" else "Быстрый просмотр:") )
                # Name includes the localized entity, so use a prefix rather than exact match.
                dialog=page.get_by_role("dialog",name="Быстрый просмотр:",exact=False)
                dialog.wait_for(timeout=30000)
                for width in (375,1440):
                    page.set_viewport_size({"width":width,"height":900})
                    page.wait_for_timeout(120)
                    assert dialog.is_visible(),f"{name} drawer hidden at {width}"
                    if width==375:
                        box=dialog.bounding_box()
                        assert box and box["x"]>=-1 and box["x"]+box["width"]<=376,(
                            f"{name} drawer beyond mobile viewport: {box}")
                    page.screenshot(path=str(ART/f"{name}-drawer-{width}.png"),full_page=True)
                page.keyboard.press("Escape")
                dialog.wait_for(state="detached",timeout=10000)
            (ART/"results.json").write_text(json.dumps({
                "checks":len(results),"screens":len(ROUTES),
                "widths":WIDTHS,"populated":True,"results":results,
                "drawers":"sale+delivery verified at 375 and 1440"
            },ensure_ascii=False,indent=2),encoding="utf-8")
            print("Populated Studio acceptance passed: 24 viewport checks + 4 drawer screenshots")
        finally:
            context.close()
            browser.close()

if __name__=="__main__":
    main()
