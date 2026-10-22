# Requires JDK 21 and the project's existing Android SDK / pnpm store configuration.
[CmdletBinding()]
param(
    [ValidateSet('Focused', 'Full', 'Device')]
    [string]$Mode = 'Focused',
    [string]$Serial,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$logRoot = Join-Path $repoRoot 'build/architecture-verification'
$gradleArgs = @('--no-daemon', '--max-workers=1', '--console=plain')
$heap = '-Dorg.gradle.jvmargs=-Xmx3072m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=256m -XX:NonNMethodCodeHeapSize=32m -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true'
$deviceClasses = @(
    'me.rerere.zeroagent.ui.pages.chat.ChatDrawerLayoutTest',
    'me.rerere.zeroagent.ui.components.ai.ComposerControlsTest',
    'me.rerere.zeroagent.ui.pages.setting.components.ProviderConnectionFieldsTest'
) -join ','

if ($Mode -eq 'Device' -and $Serial -notmatch '^emulator-[0-9]+$') {
    throw 'Device mode requires an explicit emulator serial: -Serial emulator-5558. Physical devices are not supported.'
}
if ($Mode -ne 'Device' -and $Serial) {
    throw '-Serial is only used with -Mode Device.'
}

function Invoke-Logged {
    param([string]$Executable, [string[]]$Arguments, [string]$LogName)
    Write-Host "$Executable $($Arguments -join ' ')"
    if ($DryRun) { return }
    & $Executable @Arguments 2>&1 | Tee-Object -FilePath (Join-Path $logRoot $LogName) | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed with exit code $LASTEXITCODE. See $LogName." }
}

Push-Location $repoRoot
try {
    if (!$DryRun) { New-Item -ItemType Directory -Force -Path $logRoot | Out-Null }
    switch ($Mode) {
        'Focused' {
            $testArgs = @(':app:testDebugUnitTest')
            foreach ($pattern in @(
                '*ChatCommandHandlerTest', '*ChatSlashCommandTest', '*ChatToolFactoryTest',
                '*ToolSurfaceResolverTest', '*ToolInvocationContext*', '*SubAgent*SurfaceTest',
                '*ToolCatalogTest', '*McpToolNameTest', '*HeadlessToolApprovalPolicyTest',
                '*ProviderSettingEditorTest', '*ProviderConfigureConvertToTest', '*CodexProviderConfigureTest',
                '*GenerationPlatformEffectsTest', '*AgentRunControlTest', '*AgentTurnTrackerTest',
                '*ScreenshotLedgerTest', '*AutomationRecorderTest', '*GenerationLoop*Test',
                '*GenerationHandler*Test', '*GenerationToolExecutorTest', '*LoopGuard*Test',
                '*StreamingConversationPersistenceTest', '*ChatService*Test', '*Compaction*Test',
                '*ConversationToolApprovalTest', '*ToolReviewAgentTest', '*AssistedApprovalPolicyTest',
                '*ConversationAccessTest', '*ToolExecutionRetry*Test'
            )) { $testArgs += @('--tests', $pattern) }
            Invoke-Logged '.\gradlew.bat' ($testArgs + @(':app:compileDebugKotlin', $heap) + $gradleArgs) 'focused.log'
        }
        'Full' {
            Invoke-Logged '.\gradlew.bat' (@(
                'testDebugUnitTest', ':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:lintDebug', $heap
            ) + $gradleArgs) 'full.log'
            if (!$DryRun) {
                foreach ($abi in @('arm64-v8a', 'x86_64')) {
                    if (!(Test-Path "app/build/outputs/apk/debug/app-$abi-debug.apk")) {
                        throw "Missing Debug APK for $abi."
                    }
                }
            }
        }
        'Device' {
            if ($DryRun) {
                Write-Host "Validate $Serial is an online emulator; select APK from its CPU ABI."
            } else {
                $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
                if (!$sdk) { throw 'Set ANDROID_HOME (or ANDROID_SDK_ROOT) before Device mode.' }
                $adb = Join-Path $sdk 'platform-tools/adb.exe'
                if (!(Test-Path $adb)) { throw "adb not found: $adb" }
                $state = & $adb -s $Serial get-state 2>&1
                if ($LASTEXITCODE -ne 0 -or ($state -join '').Trim() -ne 'device') { throw "$Serial is not online." }
                $isEmulator = & $adb -s $Serial shell getprop ro.kernel.qemu
                if ($LASTEXITCODE -ne 0 -or ($isEmulator -join '').Trim() -ne '1') { throw "$Serial is not an emulator." }
                $abi = (& $adb -s $Serial shell getprop ro.product.cpu.abi) -join ''
                if ($LASTEXITCODE -ne 0) { throw 'Could not query emulator ABI.' }
                $abi = $abi.Trim()
                if ($abi -notin @('arm64-v8a', 'x86_64')) { throw "Unsupported emulator ABI: $abi" }
            }
            Invoke-Logged '.\gradlew.bat' (@(':app:assembleDebug', ':app:assembleDebugAndroidTest') + $gradleArgs) 'device-build.log'
            if ($DryRun) { $adb = 'adb'; $abi = '<emulator-abi>' }
            # Fixed Debug artifacts and package: no release install, data reset or live model request.
            Invoke-Logged $adb @('-s', $Serial, 'install', '-r', "app/build/outputs/apk/debug/app-$abi-debug.apk") 'device-install.log'
            Invoke-Logged $adb @('-s', $Serial, 'install', '-r', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk') 'device-install-test.log'
            Invoke-Logged $adb @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-e', 'class', $deviceClasses,
                'app.zeroagent.debug.test/androidx.test.runner.AndroidJUnitRunner') 'device.log'
            if (!$DryRun) {
                $result = Get-Content (Join-Path $logRoot 'device.log') -Raw
                # Instrumentation may exit zero even when a test failed or the runner crashed.
                if ($result -notmatch 'OK \([1-9][0-9]* tests?\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
                    throw 'Device tests did not report success. See device.log.'
                }
            }
        }
    }
} finally {
    Pop-Location
}
