import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {parseApkBadging, generateManifest} from './generate-update-manifest.mjs';

const badging = "package: name='local.mylife.debug' versionCode='8' versionName='0.1.8'\nsdkVersion:'29'\n";
const fixture = (body) => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'mylife-manifest-'));
  try { const apk = path.join(dir, 'mylife.apk'); fs.writeFileSync(apk, 'synthetic fixture'); body(dir, apk); }
  finally { fs.rmSync(dir, {recursive: true, force: true}); }
};

test('extracts the actual binary package/version/minSdk', () => {
  assert.deepEqual(parseApkBadging(badging), {applicationId: 'local.mylife.debug', versionCode: 8, versionName: '0.1.8', minSdk: 29});
});
test('accepts current aapt2 minSdkVersion output', () => {
  assert.equal(parseApkBadging(badging.replace('sdkVersion:', 'minSdkVersion:')).minSdk, 29);
});
for (const [field, value] of [['versionCode', 9], ['versionName', '0.1.9'], ['applicationId', 'local.mylife']]) {
  test(`rejects mismatched ${field} before creating release files`, () => fixture((dir, apk) => {
    assert.throws(() => generateManifest(apk, badging, {[field]: value}), /does not match/);
    assert.equal(fs.existsSync(path.join(dir, 'update.json')), false);
    assert.equal(fs.existsSync(path.join(dir, 'SHA256SUMS')), false);
  }));
}
test('requires complete supported APK metadata', () => {
  for (const invalid of ['', badging.replace("sdkVersion:'29'", ''), badging.replace("'8'", "'0'"), badging.replace('local.mylife.debug', 'other.app'), badging.replace("sdkVersion:'29'", "sdkVersion:'28'")]) {
    assert.throws(() => parseApkBadging(invalid));
  }
});
test('emits binary metadata and checksum of the actual bytes', () => fixture((dir, apk) => {
  const manifest = generateManifest(apk, badging, {versionCode: 8, versionName: '0.1.8', applicationId: 'local.mylife.debug', channel: 'preview'});
  assert.equal(manifest.versionCode, 8);
  assert.equal(manifest.sizeBytes, fs.statSync(apk).size);
  assert.equal(manifest.sha256, 'ab94ec319d9f7574e41c10b15b3fdfeb39cf322877ec00d5b269c15c853c7b7a');
  assert.deepEqual(JSON.parse(fs.readFileSync(path.join(dir, 'update.json'))), manifest);
  assert.equal(fs.readFileSync(path.join(dir, 'SHA256SUMS'), 'utf8'), `${manifest.sha256}  mylife.apk\n`);
}));
test('rejects unknown channels', () => fixture((dir, apk) => {
  assert.throws(() => generateManifest(apk, badging, {channel: 'other'}), /channel/);
  assert.equal(fs.existsSync(path.join(dir, 'update.json')), false);
}));
