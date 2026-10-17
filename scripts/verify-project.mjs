import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
const root=path.resolve(import.meta.dirname,'..');
const failures=[];let links=0;
function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.name==='scratch'||e.name==='.git'?[]:e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);}
const files=walk(root);
// Project skills deliberately use a restricted, dependency-free YAML subset.
// This does not replace the external skill-creator validator.
const skillRoot=path.join(root,'harness/skills');
const skills=fs.readdirSync(skillRoot,{withFileTypes:true}).filter(e=>e.isDirectory());
if(skills.length!==5)failures.push('expected five project skills');
for(const skill of skills){
 const file=path.join(skillRoot,skill.name,'SKILL.md');
 if(!fs.existsSync(file)){failures.push(`missing skill ${skill.name}`);continue;}
 const content=fs.readFileSync(file,'utf8');
 const front=content.match(/^---\r?\nname: ([a-z0-9-]+)\r?\ndescription: ([^\r\n]+)\r?\n---\r?\n/);
 if(!front){failures.push(`invalid restricted skill frontmatter: ${skill.name}`);continue;}
 if(front[1]!==skill.name||front[1].length>64||/(^\-|\-$|--)/.test(front[1]))failures.push(`invalid skill name: ${skill.name}`);
 if(!front[2].trim()||front[2].length>1024||/[<>]|: | #|^[\[\]{}&*!|>'"%@`]/.test(front[2]))failures.push(`invalid plain skill description: ${skill.name}`);
 if(!content.slice(front[0].length).trim()||/\[TODO:/.test(content))failures.push(`unfinished skill: ${skill.name}`);
}
for(const file of files.filter(f=>f.endsWith('.md'))){const text=fs.readFileSync(file,'utf8');for(const m of text.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)){const link=m[1].split('#')[0];if(!link||/^https?:/.test(link))continue;links++;if(!fs.existsSync(path.resolve(path.dirname(file),link)))failures.push(`${path.relative(root,file)}: broken link ${link}`);}}
for(const p of ['app','core/model','core/security','core/storage','core/designsystem'])for(const f of ['README.md','AGENTS.md'])if(!fs.existsSync(path.join(root,'apps/android',p,f)))failures.push(`missing ${p}/${f}`);
const tokens=JSON.parse(fs.readFileSync(path.join(root,'design/tokens.json'),'utf8'));
if(Object.keys(tokens).length!==5)failures.push('must have five themes');
const linear=x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4;
function lum(hex){const rgb=hex.slice(1).match(/../g).map(x=>linear(parseInt(x,16)/255));return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722;}
function ratio(a,b){const [x,y]=[lum(a),lum(b)].sort((a,b)=>b-a);return (x+.05)/(y+.05);}
for(const [id,t]of Object.entries(tokens)){for(const k of ['background','foreground','accent'])if(!/^#[0-9A-F]{6}$/i.test(t[k]))failures.push(`invalid color ${id}.${k}`);const r=ratio(t.foreground,t.background),a=ratio(t.accent,t.background);if(r<4.5||a<4.5)failures.push(`contrast ${id}: ${r}, ${a}`);console.log(`${id}: text ${r.toFixed(2)}:1, accent ${a.toFixed(2)}:1`);}
const html=fs.readFileSync(path.join(root,'design/dashboard.html'),'utf8');
for(const match of html.matchAll(/<script>([\s\S]*?)<\/script>/g))new vm.Script(match[1]);
if(/(?:src|href)=["']https?:/i.test(html)||/\b(?:fetch|XMLHttpRequest|WebSocket)\s*\(/.test(html))failures.push('external resource/network code in mockup');
console.log(`Checked ${files.filter(f=>f.endsWith('.md')).length} Markdown files; ${links} local links; five themes; five project skills; gallery script syntax.`);
if(failures.length){console.error(failures.join('\n'));process.exitCode=1;}else console.log('PASS: project package static checks. Android runtime not tested.');
