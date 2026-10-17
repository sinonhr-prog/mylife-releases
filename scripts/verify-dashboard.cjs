const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const root=path.resolve(__dirname,'..');
if(!process.env.MYLIFE_NODE_MODULES)throw new Error('Set MYLIFE_NODE_MODULES to the installed Playwright package directory');
const {chromium}=require(path.join(process.env.MYLIFE_NODE_MODULES,'playwright'));
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
 try{
 const page=await browser.newPage({viewport:{width:390,height:1000},reducedMotion:'reduce'});
 const errors=[],network=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url()))network.push(r.url())});
 const url=pathToFileURL(path.join(root,'design/dashboard.html')).href;
 await page.goto(url+'?layout=blocks');
 let combinations=0,transitions=0;
 const layouts=['blocks','tiles','focus','timeline'];
 for(const width of [320,390,768,1530]){
  await page.setViewportSize({width,height:1000});
  for(const layout of layouts){
   await page.evaluate(l=>window.dashboardTest.set(l),layout);
   assert.equal(await page.locator('.phone').count(),1);
   const state=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth||document.querySelector('.content').scrollWidth>document.querySelector('.content').clientWidth,labels:[...document.querySelectorAll('button')].every(b=>b.textContent.trim()||b.getAttribute('aria-label')),hasBottomNav:!!document.querySelector('.bottom')}));
   assert.equal(state.overflow,false,`${width}/${layout} overflow`);assert(state.labels);assert(!state.hasBottomNav);
   await page.locator('[data-menu]').click();assert.equal(await page.locator('[data-menu]').getAttribute('aria-expanded'),'true');
   assert(await page.locator('.drawer').isVisible());assert(await page.locator('.content').evaluate(e=>e.inert));
   const bounds=await page.locator('.drawer').boundingBox(),phone=await page.locator('.phone').boundingBox();
   assert(Math.abs(bounds.x-phone.x)<3);assert(bounds.width<phone.width);
   await page.keyboard.press('Shift+Tab');assert.equal(await page.locator('.drawer button').last().evaluate(e=>e===document.activeElement),true);
   await page.keyboard.press('Tab');assert.equal(await page.locator('.drawer button').first().evaluate(e=>e===document.activeElement),true);
   await page.keyboard.press('Escape');assert.equal(await page.locator('[data-menu]').getAttribute('aria-expanded'),'false');assert(await page.locator('[data-menu]').evaluate(e=>e===document.activeElement));
   await page.locator('[data-menu]').click();await page.locator('.drawer [data-close]').click();assert.equal(await page.locator('[data-menu]').getAttribute('aria-expanded'),'false');
   await page.locator('[data-menu]').click();await page.mouse.click(phone.x+phone.width-12,phone.y+200);assert.equal(await page.locator('[data-menu]').getAttribute('aria-expanded'),'false');
   combinations++;
  }
 }
 await page.setViewportSize({width:390,height:1000});
 for(const layout of layouts){
  await page.evaluate(l=>window.dashboardTest.set(l),layout);
  const items=await page.evaluate(()=>window.dashboardTest.items);
  for(const route of items){await page.locator('[data-menu]').click();const entry=page.locator(`.drawer [data-route="${route}"]`);const name=await entry.innerText();await entry.click();assert.equal(await page.locator('[data-menu]').getAttribute('aria-expanded'),'false');assert.equal(await page.locator('.appbar strong').innerText(),route==='home'?'Моя жизнь':name);transitions++;}
 }
 await page.locator('[data-layout-choice="tiles"]').click();assert.equal(await page.locator('.sample').getAttribute('data-layout'),'tiles');
 await page.evaluate(()=>window.dashboardTest.set('blocks'));await page.locator('.content [data-route="recent"]').click();assert.equal(await page.locator('.routeheading').innerText(),'Последние записи');assert.equal(await page.locator('.record').count(),2);
 await page.locator('.backhome').click();await page.locator('.content [data-route="recentplaces"]').click();assert.equal(await page.locator('.routeheading').innerText(),'Недавние места');assert.equal(await page.locator('.placecard').count(),2);
 await page.locator('[data-compare]').click();assert.equal(await page.locator('.phone').count(),4);
 assert.equal((await page.locator('body').innerText()).includes('Выберите категорию'),true);
 await page.locator('.footerbar .add').first().click();assert(await page.locator('.toast').isVisible());
 fs.mkdirSync(path.join(root,'design/previews'),{recursive:true});
 await page.setViewportSize({width:1560,height:1020});await page.goto(url+'?export=1');await page.screenshot({path:path.join(root,'design/previews/paper-dashboards.png'),fullPage:true});
 for(const layout of layouts){await page.setViewportSize({width:430,height:1000});await page.goto(url+'?layout='+layout+'&export=1');await page.screenshot({path:path.join(root,'design/previews',`paper-${layout}.png`),fullPage:true});}
 await page.goto(url+'?layout=blocks&menu=1&export=1');await page.screenshot({path:path.join(root,'design/previews/paper-menu.png'),fullPage:true});
 assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
 console.log(`PASS: ${combinations} layout/width combinations; ${transitions} category transitions; recent records/places lists; drawer close/Escape/scrim/focus trap; comparison/demo; no network or JS errors; six PNGs.`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
