import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';

// The finished APK is the source of truth, not a tag or commit count.
export function parseApkBadging(badging) {
  const pkg = /^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'/m.exec(badging);
  const sdk = /^(?:sdkVersion|minSdkVersion):'(\d+)'/m.exec(badging);
  if (!pkg || !sdk) throw new Error('APK package/version/minSdk metadata is missing');
  const metadata = {applicationId: pkg[1], versionCode: Number(pkg[2]), versionName: pkg[3], minSdk: Number(sdk[1])};
  if (!Number.isSafeInteger(metadata.versionCode) || metadata.versionCode < 1 ||
      metadata.versionCode > 2100000000 || !metadata.versionName || metadata.minSdk < 29) {
    throw new Error('Invalid APK version or minSdk');
  }
  if (!['local.mylife', 'local.mylife.debug'].includes(metadata.applicationId)) throw new Error('Unexpected APK applicationId');
  return metadata;
}

export function generateManifest(apkPath, badging, expected = {}) {
  const metadata = parseApkBadging(badging);
  for (const field of ['versionCode', 'versionName', 'applicationId']) {
    if (expected[field] !== undefined && String(expected[field]) !== String(metadata[field])) {
      throw new Error(`APK ${field} does not match requested release metadata`);
    }
  }
  const channel = expected.channel ?? 'preview';
  if (!['stable', 'preview'].includes(channel)) throw new Error('Invalid update channel');
  const bytes = fs.readFileSync(apkPath);
  if (!bytes.length) throw new Error('APK is empty');
  const assetName = path.basename(apkPath);
  const sha256 = crypto.createHash('sha256').update(bytes).digest('hex');
  const manifest = {schemaVersion: 1, ...metadata, assetName, sizeBytes: bytes.length, sha256, channel};
  const dir = path.dirname(apkPath);
  // Validate everything before writing either release file.
  fs.writeFileSync(path.join(dir, 'update.json'), JSON.stringify(manifest, null, 2) + '\n');
  fs.writeFileSync(path.join(dir, 'SHA256SUMS'), `${sha256}  ${assetName}\n`);
  return manifest;
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try {
    const [, , apkPath, versionName, versionCode, channel, applicationId] = process.argv;
    if (!apkPath || !fs.existsSync(apkPath)) throw new Error('Usage: generate-update-manifest.mjs APK [versionName] [versionCode] [channel] [applicationId]');
    const aapt2 = process.env.AAPT2_PATH;
    if (!aapt2 || !fs.existsSync(aapt2)) throw new Error('Set AAPT2_PATH to the installed Android build-tools aapt2 executable');
    const badging = execFileSync(aapt2, ['dump', 'badging', path.resolve(apkPath)], {encoding: 'utf8', maxBuffer: 1024 * 1024});
    console.log(JSON.stringify(generateManifest(apkPath, badging, {versionName, versionCode, channel, applicationId}), null, 2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
