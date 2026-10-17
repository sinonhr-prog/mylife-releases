# Signing channel follow-up — 2026-10-17

RunId 20261017-sol-signing-followup; model gpt-6.1-sol, identitySource operator-confirmed in this chat. Baseline HEAD 96b560a. Separate signing request serialized using H10 lock; H10/H11 not closed. Existing uncommitted signing configuration preserved and corrected.

Owner requested to use updated signing secrets, confirmed TXT values matched GitHub and confirmed model. Supplied password failed to open supplied Base64. Owner then requested keystore creation. A new JKS container imports the existing local debug key using desired passwords/alias from TXT. Original key and certificate preserved; original source untouched. TXT Base64 replaced after certificate equality check; other three values unchanged. Secret material remains only ignored private/, never report/Git. This is a compatible test key container, not production-key rotation.

Changes: explicit root-relative/absolute KEYSTORE_PATH, no standard password fallback; CI temporary restricted keystore and cleanup; release .debug suffix retains current test package; apksigner verification before assets; signed-channel flag prevents publishing runner debug fallback. Candidate has debuggable=false.

Current verification: see signing-followup-evidence.txt (same run prefix): 8 manifest tests, 15 harness tests, 43 release app unit tests, release lint0 errors, offline unsigned and signed assemble PASS; signed candidate20/0.1.20 package local.mylife.debug / minSdk29, original certificate preserved; actual APK metadata/hash generated. Public artifact hash in evidence. Source/plan hashes are saved separately. Local logs and secret material are not evidence.

Remaining: repaired Base64 has NOT been copied to GitHub, because browser clipboard is isolated and installation of API encryption dependency was rejected earlier (ENV-012). Owner should replace only KEYSTORE_BASE64 using the updated local TXT, or separately authorize the previously rejected dependency installation. Do not claim remote values match after this repair. No push: it triggers publication and remote key is still stale. No release/device install performed. Real-secrets-review/personal-release remain pending; do not alter gates or claim H10/H11 completion.

Open finding: SIGN-003 existing ApkVerifier package-prefix allowance, outside this signing-only change; record/review before full release acceptance. ENV-014 sandbox cache write and ENV-015 redirected LOCALAPPDATA corrected for local checks. Dependency R8 warnings BUILD-008 remain warnings.

Next: sync regenerated Base64 to GitHub, verify remote signed candidate before publication, and compare against actual installed device certificate/package/version. Do not weaken verifier or uninstall/clear data if identity differs. Current changes uncommitted, local TXT/private keystore ignored. Lock released after checkpoint by harness unlock. Read CURRENT, this handoff, evidence, ERRORS SIGN-001/002/003 and actual diff.
