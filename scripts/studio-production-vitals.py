#!/usr/bin/env python3
"""Measure the BUILT ChairX frontend, authenticated via the real Spring API.

Requires ephemeral CI database, Vite preview at 4173 and backend proxy.
The document LCP/FCP include the LOGIN screen and are not protected-page vitals.
SPA transitions are measured by heading visibility and JS/API requests instead.
INP requires representative user interactions/field sampling; event durations are
reported only as diagnostic interactions, not asserted field INP.
"""
import json
import os
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE="http://127.0.0.1:4173"
ART=Path("artifacts/studio-production")
ART.mkdir(parents=True, exist_ok=True)
JOURNEY=[
    ("Финансы", "Финансы"),
    ("Продажи", "Продажи"),
    ("Финансы", "Финансы"),
    ("Администрирование", "Сотрудники"),
    ("Каталог товаров", "Каталог товаров"),
]
OBSERVER=r"""(() => {
  const metrics = {cls:0, layoutShifts:0, lcp:null, fcp:null,
                   longTasks:[], events:[], observers:[]};
  window.__chairxPerf = metrics;
  const watch = (type, cb, options={type, buffered:true}) => {
    try {
      const observer = new PerformanceObserver(list => {
        for (const entry of list.getEntries()) cb(entry);
      });
      observer.observe(options);
      metrics.observers.push(observer);
    } catch (_) {}
  };
  watch('layout-shift', e => {
    if (!e.hadRecentInput) { metrics.cls+=e.value; metrics.layoutShifts++; }
  });
  watch('largest-contentful-paint', e => { metrics.lcp=e.startTime; });
  watch('paint', e => {
    if (e.name==='first-contentful-paint') metrics.fcp=e.startTime;
  });
  watch('longtask', e => {
    metrics.longTasks.push({start:e.startTime,duration:e.duration});
  });
  watch('event', e => {
    if (e.interactionId) metrics.events.push({
      interactionId:e.interactionId, name:e.name, duration:e.duration
    });
  }, {type:'event', buffered:true, durationThreshold:16});
})()"""

def login(page):
    page.goto(BASE+"/sales",wait_until="domcontentloaded")
    page.get_by_label("Логин").fill("admin")
    page.get_by_label("Пароль",exact=True).fill(
        os.environ["CHAIRX_BOOTSTRAP_ADMIN_PASSWORD"]
    )
    page.get_by_role("button",name="Войти",exact=True).click()
    page.get_by_role("heading",name="Продажи",exact=True).wait_for(timeout=30000)

def measure(context_name, viewport, browser):
    context=browser.new_context(
        viewport=viewport, locale="ru-RU", reduced_motion="reduce",
        device_scale_factor=1
    )
    context.add_init_script(OBSERVER)
    page=context.new_page()
    requests=[]
    response_errors=[]
    page.on("request",lambda req: requests.append({
        "path":req.url.split("?",1)[0].replace(BASE,""),
        "type":req.resource_type
    }) if req.url.startswith(BASE) else None)
    page.on("response",lambda resp:response_errors.append({
        "path":resp.url.split("?",1)[0].replace(BASE,""),
        "status":resp.status
    }) if resp.url.startswith(BASE+"/api/") and resp.status>=400 else None)
    try:
        login(page)
        results=[]
        for idx,(nav_label,heading) in enumerate(JOURNEY):
            before=len(requests)
            start=time.perf_counter()
            if context_name=="mobile":
                opener=page.get_by_role("button",name="Открыть меню")
                opener.click()
                panel=page.get_by_role("dialog",name="Мобильная навигация")
                panel.get_by_role("link",name=nav_label,exact=True).click()
            else:
                sidebar=page.get_by_role("navigation",name="Разделы ChairX")
                sidebar.get_by_role("link",name=nav_label,exact=True).click()
            page.get_by_role("heading",name=heading,exact=True).first.wait_for(
                timeout=30000
            )
            elapsed=round((time.perf_counter()-start)*1000,1)
            page.wait_for_timeout(250)
            new=requests[before:]
            results.append({
                "route":nav_label,"visible_heading_ms":elapsed,
                "scripts":len([r for r in new if r["type"]=="script"]),
                "api_requests":len([r for r in new if r["path"].startswith("/api/")]),
                "new_request_paths":list(dict.fromkeys(
                    r["path"] for r in new
                    if r["type"]=="script" or r["path"].startswith("/api/")
                ))[:30]
            })
            if idx in (0,3):
                page.screenshot(path=str(ART/f"{context_name}-{idx}-{nav_label}.png"),
                                full_page=False)
        assert not response_errors, f"Authenticated preview HTTP errors: {response_errors}"
        perf=page.evaluate("""() => {
          const x=window.__chairxPerf;
          const resources=performance.getEntriesByType('resource')
            .filter(e=>e.name.includes('/assets/'));
          const interactionDurations=Object.values(
            (x?.events||[]).reduce((acc,e)=>{
              acc[e.interactionId]=Math.max(acc[e.interactionId]||0,e.duration);
              return acc;
            },{})
          ).sort((a,b)=>b-a);
          return {
            document_lcp_ms:x?.lcp??null, document_fcp_ms:x?.fcp??null,
            document_cls:x?.cls??null,
            long_task_count:x?.longTasks.length??0,
            long_tasks_total_ms:(x?.longTasks||[]).reduce((v,e)=>v+e.duration,0),
            interaction_samples:interactionDurations.length,
            largest_interaction_duration_ms:interactionDurations[0]??null,
            asset_resource_count:resources.length,
            asset_encoded_body_bytes:resources.reduce((v,e)=>v+e.encodedBodySize,0),
          };
        }""")
        return {"environment":context_name,"viewport":viewport,
                "transitions":results,"document_metrics":perf}
    finally:
        context.close()

def main():
    assert os.environ.get("CI")=="true"
    assert os.environ.get("CHAIRX_F11_E2E_DISPOSABLE")==(
        "I_UNDERSTAND_DATABASE_IS_DISPOSABLE"
    )
    with sync_playwright() as pw:
        browser=pw.chromium.launch(headless=True)
        try:
            runs=[
                measure("desktop",{"width":1440,"height":900},browser),
                measure("mobile",{"width":375,"height":812},browser),
            ]
        finally:
            browser.close()
    report={
        "server":"Vite production preview, built minified assets / disposable Spring API",
        "scope":"Authenticated real app session; two viewport runs",
        "caveat":"LCP/FCP are login-document values, NOT metrics for protected route content. Interaction duration is NOT field INP. CI is not real-user data.",
        "runs":runs,
    }
    (ART/"production-vitals.json").write_text(
        json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8"
    )
    for run in runs:
        print("Production",run["environment"],"transitions:",
              [(x["route"],x["visible_heading_ms"]) for x in run["transitions"]])
        print("Production",run["environment"],"document:",run["document_metrics"])

if __name__=="__main__":
    main()
