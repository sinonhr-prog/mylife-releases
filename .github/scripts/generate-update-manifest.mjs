import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

/**
 * Generate update.json manifest and SHA256SUMS for GitHub Releases.
 * Complies with ADR 0003 (Schema Version 1).
 */
const apkPath = process.argv[2];
if (!apkPath || !fs.existsSync(apkPath)) {
  console.error('Usage: node generate-update-manifest.mjs <path-to-apk> [versionName] [versionCode] [channel] [applicationId]');
  process.exit(1);
}

const versionName = process.argv[3] || '0.1.0';
const versionCode = parseInt(process.argv[4] || '1', 10);
const channel = process.argv[5] || 'stable';
const applicationId = process.argv[6] || 'local.mylife';

const apkStats = fs.statSync(apkPath);
const apkBytes = fs.readFileSync(apkPath);
const sha256 = crypto.createHash('sha256').update(apkBytes).digest('hex');
const assetName = path.basename(apkPath);

const updateManifest = {
  schemaVersion: 1,
  applicationId,
  versionCode,
  versionName,
  minSdk: 29,
  assetName,
  sizeBytes: apkStats.size,
  sha256,
  channel,
};

const outputDir = path.dirname(apkPath);
const manifestPath = path.join(outputDir, 'update.json');
const checksumsPath = path.join(outputDir, 'SHA256SUMS');

fs.writeFileSync(manifestPath, JSON.stringify(updateManifest, null, 2) + '\n');
fs.writeFileSync(checksumsPath, `${sha256}  ${assetName}\n`);

console.log(`Generated manifest at: ${manifestPath}`);
console.log(`Generated checksums at: ${checksumsPath}`);
console.log(JSON.stringify(updateManifest, null, 2));
