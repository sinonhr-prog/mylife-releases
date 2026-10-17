import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';

const idPattern=/^[a-z0-9][a-z0-9-]{2,79}$/;
const hashPattern=/^[a-f0-9]{64}$/;
const requireThat=(condition,message)=>{if(!condition)throw new Error(message);};
const hashBytes=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const text=value=>typeof value==='string'&&value.trim().length>0;
const relativeAllowed=(rel,prefixes)=>prefixes.some(p=>p.endsWith('/')?rel.startsWith(p):rel===p);

export function safePath(root,relative,{mustExist=true,prefix=null}={}){
 requireThat(text(relative)&&!relative.includes('\\')&&!path.isAbsolute(relative)&&!relative.includes(':')&&!relative.split('/').includes('..'),'Unsafe repository-relative path');
 requireThat(!prefix||relative.startsWith(prefix),'Path outside required evidence directory');
 const abs=path.resolve(root,relative),base=fs.realpathSync(root);
 requireThat(abs!==path.resolve(root)&&abs.startsWith(path.resolve(root)+path.sep),'Path escapes project');
 if(mustExist){requireThat(fs.existsSync(abs),`Missing file: ${relative}`);const real=fs.realpathSync(abs);requireThat(real.startsWith(base+path.sep),'Symlink escapes project');}
 return abs;
}
const readJson=(root,rel)=>JSON.parse(fs.readFileSync(safePath(root,rel),'utf8'));
export const fileHash=(root,rel)=>hashBytes(fs.readFileSync(safePath(root,rel)));

export function route(root,model){const config=readJson(root,'harness/phases.json');requireThat(Object.hasOwn(config.models,model),`Unsupported or unconfirmed model ID: ${model}`);safePath(root,config.models[model]);return config.models[model];}

export function digests(root){
 const roots=['AGENTS.md','README.md','GEMINI.md','apps/android','docs','design/dashboard.html','design/DASHBOARD.md','design/tokens.json','harness','scripts'];
 const skipDirs=new Set(['.git','.gradle','build','node_modules','.idea','scratch','private']);
 const files=[];
 const walk=rel=>{const abs=path.join(root,rel);if(!fs.existsSync(abs))return;const stat=fs.lstatSync(abs);requireThat(!stat.isSymbolicLink(),`Source symlink requires review: ${rel}`);
  if(stat.isDirectory()){for(const e of fs.readdirSync(abs,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name))){if(!skipDirs.has(e.name))walk(rel+'/'+e.name);}return;}
  if(rel.startsWith('harness/runs/')||['harness/state.json','harness/CURRENT.md','harness/runtime.lock'].includes(rel)||rel.endsWith('.tmp'))return;
  const name=path.basename(rel);if(name==='local.properties'||name.startsWith('.env')||/\.(jks|keystore|p12|db|sqlite)(-|$)/.test(name))return;
  files.push([rel,hashBytes(fs.readFileSync(abs))]);
 };
 roots.forEach(walk);files.sort((a,b)=>a[0].localeCompare(b[0]));
 return {planDigest:fileHash(root,'harness/phases.json'),sourceDigest:hashBytes(files.map(([p,h])=>p+'\0'+h+'\n').join('')),sourceFiles:files.length};
}

function parseProject(root){
 const manifest=readJson(root,'harness/phases.json'),state=readJson(root,'harness/state.json'),decisions=readJson(root,'harness/decisions.json');
 requireThat(manifest.schemaVersion===1&&state.schemaVersion===1&&decisions.schemaVersion===1,'Unsupported schema version');
 requireThat(Number.isInteger(manifest.revision)&&manifest.revision>0,'Missing plan revision');
 requireThat(Array.isArray(manifest.phases)&&manifest.phases.length>0&&Array.isArray(state.completed)&&Array.isArray(decisions.decisions),'Invalid project registry');
 requireThat(manifest.models&&Object.keys(manifest.models).length===2&&Object.hasOwn(manifest.models,'gemini-3.8-flash')&&Object.hasOwn(manifest.models,'gpt-6.1-sol'),'Model routing must include exactly the two approved IDs');
 for(const profile of Object.values(manifest.models))safePath(root,profile);
 const decisionIds=new Set();for(const d of decisions.decisions){requireThat(text(d.id)&&!decisionIds.has(d.id),'Duplicate or invalid decision');decisionIds.add(d.id);requireThat(['pending','approved','rejected'].includes(d.status),'Invalid decision status');if(d.status==='approved')requireThat(text(d.sourceRef)&&Number.isFinite(Date.parse(d.recordedAt)),'Approved decision lacks human evidence');}
 const phaseIds=new Set();for(const p of manifest.phases){
  requireThat(/^H\d{2}$/.test(p.id)&&!phaseIds.has(p.id),'Invalid or duplicate phase ID');safePath(root,p.card);
  for(const name of ['dependsOn','entryDecisions','exitDecisions','skills','steps','requiredChecks','scopePrefixes'])requireThat(Array.isArray(p[name]),`Missing array ${p.id}.${name}`);
  requireThat(p.dependsOn.every(id=>phaseIds.has(id)),`Missing, cyclic or out-of-order dependency: ${p.id}`);phaseIds.add(p.id);
  for(const d of [...p.entryDecisions,...p.exitDecisions])requireThat(decisionIds.has(d),`Unknown decision: ${d}`);
  for(const skill of p.skills){requireThat(/^[a-z0-9-]+$/.test(skill),'Invalid skill ID');const content=fs.readFileSync(safePath(root,`harness/skills/${skill}/SKILL.md`),'utf8');requireThat(content.startsWith('---\n')&&content.includes(`name: ${skill}\n`),'Invalid skill frontmatter');}
  requireThat(p.steps.length>0&&new Set(p.steps).size===p.steps.length&&p.steps.every(s=>s.startsWith(p.id+'.')),'Invalid phase steps');
  requireThat(p.requiredChecks.length>0&&new Set(p.requiredChecks).size===p.requiredChecks.length,'Invalid gate check IDs');
  requireThat(p.scopePrefixes.length>0,'Missing allowed scope');
 }
 return {manifest,state,decisions};
}

function reportData(root,reportPath,manifest){
 safePath(root,reportPath,{prefix:'harness/runs/'});const report=readJson(root,reportPath);
 requireThat(report.schemaVersion===1&&idPattern.test(report.runId),'Invalid run identity/schema');
 requireThat(reportPath===`harness/runs/${report.runId}.json`,'Run report filename must match runId');
 const phase=manifest.phases.find(p=>p.id===report.phase);requireThat(phase,'Unknown phase');route(root,report.model);
 requireThat(['runtime-metadata','operator-confirmed'].includes(report.identitySource)&&text(report.identityRef),'Missing trustworthy identity source/reference');
 requireThat(text(report.authorizationRef),'Missing owner authorization reference');
 const started=Date.parse(report.startedAt),completed=Date.parse(report.completedAt);requireThat(Number.isFinite(started)&&Number.isFinite(completed)&&completed>=started,'Invalid run timestamps');
 requireThat(report.status==='READY_FOR_GATE','Report is not ready for gate');
 requireThat(hashPattern.test(report.sourceDigest)&&hashPattern.test(report.planDigest),'Missing source/plan fingerprint');
 requireThat(Array.isArray(report.completedSteps)&&report.completedSteps.length===phase.steps.length&&new Set(report.completedSteps).size===phase.steps.length&&phase.steps.every(s=>report.completedSteps.includes(s)),'Not all phase steps completed');
 requireThat(Array.isArray(report.changedPaths),'Missing changedPaths');
 for(const changed of report.changedPaths){safePath(root,changed,{mustExist:false});requireThat(relativeAllowed(changed,phase.scopePrefixes),`Declared change exceeds scope: ${changed}`);}
 requireThat(Array.isArray(report.checks)&&report.checks.length===phase.requiredChecks.length&&new Set(report.checks.map(c=>c.id)).size===phase.requiredChecks.length,'Missing or duplicate gate checks');
 for(const check of report.checks){
  requireThat(phase.requiredChecks.includes(check.id)&&check.status==='PASS',`Gate check not PASS: ${check.id}`);
  requireThat(['automated','manual'].includes(check.kind)&&text(check.command)&&text(check.cwd),'Missing check method/command/cwd');
  if(check.kind==='automated')requireThat(check.exitCode===0,'Automated check failed or exit code unknown');
  const at=Date.parse(check.observedAt);requireThat(Number.isFinite(at)&&at>=started&&at<=completed,'Check timestamp outside run');
  const artifact=safePath(root,check.evidencePath,{prefix:'harness/runs/'});requireThat(fs.statSync(artifact).isFile()&&fs.statSync(artifact).size>0,'Empty/non-file evidence');
  requireThat(hashPattern.test(check.sha256)&&fileHash(root,check.evidencePath)===check.sha256,'Evidence hash mismatch');
 }
 const handoff=safePath(root,report.handoffPath,{prefix:'harness/runs/'});requireThat(fs.statSync(handoff).isFile()&&fs.statSync(handoff).size>0,'Missing handoff');
 return {report,phase};
}

export function validate(root){
 const project=parseProject(root),closed=new Set(),runs=new Set();
 for(const c of project.state.completed){
  requireThat(!closed.has(c.phase)&&!runs.has(c.runId),'Duplicate completed phase/run');
  requireThat(fileHash(root,c.reportPath)===c.reportSha256,'Closed report was modified');
  const {report,phase}=reportData(root,c.reportPath,project.manifest);
  requireThat(report.runId===c.runId&&phase.id===c.phase&&report.sourceDigest===c.sourceDigest&&report.planDigest===c.planDigest,'Closed state/report mismatch');
  requireThat(phase.dependsOn.every(p=>closed.has(p)),'Completed phase lacks predecessor');
  requireThat(Number.isFinite(Date.parse(c.closedAt)),'Invalid closed timestamp');closed.add(c.phase);runs.add(c.runId);
 }
 return project;
}

function decisionCheck(project,ids,phaseId){for(const id of ids){const d=project.decisions.decisions.find(d=>d.id===id);requireThat(d?.status==='approved'&&text(d.sourceRef)&&Number.isFinite(Date.parse(d.recordedAt)),`Pending/rejected decision: ${id}`);if(id==='implementation-start')requireThat(Array.isArray(d.approvedPhases)&&d.approvedPhases.includes(phaseId),`Phase outside owner-authorized range: ${phaseId}`);}}
export function ready(root,phaseId){const p=validate(root),phase=p.manifest.phases.find(p=>p.id===phaseId);requireThat(phase,'Unknown phase');requireThat(!p.state.completed.some(c=>c.phase===phaseId),'Phase already closed');requireThat(phase.dependsOn.every(dep=>p.state.completed.some(c=>c.phase===dep)),'Predecessor not closed');decisionCheck(p,phase.entryDecisions,phaseId);return {project:p,phase};}
export function gate(root,reportPath){const project=validate(root),{report,phase}=reportData(root,reportPath,project.manifest);ready(root,phase.id);decisionCheck(project,phase.exitDecisions);const current=digests(root);requireThat(current.planDigest===report.planDigest,'Plan changed after evidence');requireThat(current.sourceDigest===report.sourceDigest,'Source changed after evidence');return {report,phase};}

export function lock(root,runId,phase,model){requireThat(idPattern.test(runId),'Invalid runId');route(root,model);ready(root,phase);const p=path.join(root,'harness/runtime.lock');try{fs.writeFileSync(p,JSON.stringify({runId,phase,model,acquiredAt:new Date().toISOString()},null,2)+'\n',{flag:'wx'});}catch(e){if(e.code==='EEXIST')throw new Error('Another writer owns the workspace lock; do not auto-delete it');throw e;}return {runId,phase,model};}
export function unlock(root,runId){const owner=readJson(root,'harness/runtime.lock');requireThat(owner.runId===runId,'Cannot release another run lock');fs.unlinkSync(safePath(root,'harness/runtime.lock'));return {released:runId};}
export function close(root,reportPath){
 const project=validate(root),closed=project.state.completed.find(c=>c.reportPath===reportPath);
 if(closed){requireThat(closed.reportSha256===fileHash(root,reportPath),'Closed report changed');if(fs.existsSync(path.join(root,'harness/runtime.lock'))){const owner=readJson(root,'harness/runtime.lock');if(owner.runId===closed.runId)unlock(root,closed.runId);}return {alreadyClosed:closed.phase};}
 const {report,phase}=gate(root,reportPath),owner=readJson(root,'harness/runtime.lock');
 requireThat(owner.runId===report.runId&&owner.phase===phase.id&&owner.model===report.model,'Lock does not belong to this report/model/phase');
 project.state.completed.push({phase:phase.id,runId:report.runId,reportPath,reportSha256:fileHash(root,reportPath),sourceDigest:report.sourceDigest,planDigest:report.planDigest,closedAt:new Date().toISOString()});
 const target=safePath(root,'harness/state.json'),temp=path.join(root,`harness/.state-${crypto.randomUUID()}.tmp`);
 fs.writeFileSync(temp,JSON.stringify(project.state,null,2)+'\n',{flag:'wx'});fs.renameSync(temp,target);unlock(root,report.runId);return {closed:phase.id};
}

function cli(){const root=path.resolve(import.meta.dirname,'..'),[command,...args]=process.argv.slice(2);let result;
 if(command==='validate'){const p=validate(root);result={status:'PASS',phases:p.manifest.phases.length,completed:p.state.completed.length};}
 else if(command==='route')result={model:args[0],profile:route(root,args[0])};
 else if(command==='digest')result=digests(root);
 else if(command==='ready'){const {phase}=ready(root,args[0]);result={ready:phase.id};}
 else if(command==='next'){const p=validate(root),next=p.manifest.phases.find(x=>!p.state.completed.some(c=>c.phase===x.id));result={next:next?.id||null};if(next)try{ready(root,next.id);result.entry='READY';}catch(e){result.entry='BLOCKED';result.reason=e.message;}}
 else if(command==='lock')result=lock(root,...args);
 else if(command==='unlock')result=unlock(root,args[0]);
 else if(command==='gate'){const {phase}=gate(root,args[0]);result={gate:'PASS',phase:phase.id};}
 else if(command==='close')result=close(root,args[0]);
 else throw new Error('Usage: harness.mjs validate | route MODEL | next | ready Hxx | digest | lock RUN Hxx MODEL | unlock RUN | gate REPORT | close REPORT');
 console.log(JSON.stringify(result,null,2));
}
if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){try{cli();}catch(e){console.error('HARNESS FAIL: '+e.message);process.exitCode=1;}}
