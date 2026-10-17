const fs=require('node:fs');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const root=path.resolve(__dirname,'..');
const packageRoot=process.env.MYLIFE_NODE_MODULES;
if(!packageRoot)throw new Error('Set MYLIFE_NODE_MODULES to the existing browser automation package directory.');
const {chromium}=require(path.join(packageRoot,'playwright'));
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
 const page=await browser.newPage({viewport:{width:1250,height:1000},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')console.log('Browser console:',m.text())});
 const url=pathToFileURL(path.join(root,'design/index.html')).href;
 const source=fs.readFileSync(path.join(root,'design/index.html'),'utf8');
 const load=async(query='')=>page.setContent(source.replace("const query=new URLSearchParams(location.search)",`const query=new URLSearchParams(${JSON.stringify(query)})`));
 await page.goto(url);console.log('Local file loaded; buttons:',await page.locator('button').count());if(!await page.locator('[data-theme="forest"]').count()){console.log('Initialization errors:',errors);await browser.close();throw new Error('Gallery did not initialize');}await page.locator('[data-theme="forest"]').click();
 if(!(await page.locator('.phone').getAttribute('aria-label')).includes('Хвоя'))throw new Error('Theme control failed');
 await page.locator('.screens [data-screen="product"]').click();
 if(!await page.getByRole('heading',{name:'Наушники',exact:true}).count())throw new Error('Screen control failed');
 await page.locator('.primary').click();if(!await page.locator('.toast').isVisible())throw new Error('Demo action failed');
 let checked=0;
 for(const width of [320,390,768,1250]){
  await page.setViewportSize({width,height:1000});
  for(const t of ['ink','forest','plum','night','paper'])for(const s of ['home','purchases','product','places','tasks','notes','vault']){
   await page.evaluate(([t,s])=>window.designTest.set(t,s),[t,s]);
   const result=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,phoneOverflow:document.querySelector('.phone').scrollWidth>document.querySelector('.phone').clientWidth,labels:[...document.querySelectorAll('button')].every(b=>b.textContent.trim()||b.getAttribute('aria-label'))}));
   if(result.overflow||result.phoneOverflow||!result.labels)throw new Error(JSON.stringify({width,t,s,result}));checked++;
  }
 }
 fs.mkdirSync(path.join(root,'design/previews'),{recursive:true});
 await page.setViewportSize({width:1700,height:1050});await page.goto('about:blank');await load('?compare=1');await page.screenshot({path:path.join(root,'design/previews/all-five.png'),fullPage:true});
 await page.setViewportSize({width:1200,height:1000});
 for(const t of ['ink','forest','plum','night','paper']){await page.goto('about:blank');await load('?theme='+t+'&board=1');await page.screenshot({path:path.join(root,'design/previews',t+'.png'),fullPage:true});}
 await browser.close();if(errors.length)throw new Error(errors.join('\n'));
 console.log(`PASS: ${checked} theme/screen/width combinations, theme/screen/demo controls, no JS errors; six preview PNGs written. Android UI not tested.`);
})().catch(e=>{console.error(e);process.exit(1)});
