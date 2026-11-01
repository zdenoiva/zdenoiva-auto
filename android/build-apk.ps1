# Zostaví Dochádzka.apk bez Gradle — len nástrojmi z Android SDK.
#   aapt2 (resources) -> javac -> d8 (dex) -> zipalign -> apksigner
$ErrorActionPreference = "Stop"

$sdk        = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$buildTools = Join-Path $sdk "build-tools\36.0.0"
$androidJar = Join-Path $sdk "platforms\android-34\android.jar"
$proj       = $PSScriptRoot
$out        = Join-Path $proj "build"
$apk        = Join-Path $proj "Dochadzka.apk"

foreach ($nastroj in @("aapt2.exe", "d8.bat", "zipalign.exe", "apksigner.bat")) {
    if (-not (Test-Path (Join-Path $buildTools $nastroj))) {
        throw "Chýba $nastroj v $buildTools"
    }
}

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out, "$out\gen", "$out\classes", "$out\dex" | Out-Null

Write-Host "1/6 resources (aapt2 compile)"
& "$buildTools\aapt2.exe" compile --dir (Join-Path $proj "res") -o "$out\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile zlyhal" }

Write-Host "2/6 balík a R.java (aapt2 link)"
& "$buildTools\aapt2.exe" link `
    -o "$out\base.apk" `
    -I $androidJar `
    --manifest (Join-Path $proj "AndroidManifest.xml") `
    --java "$out\gen" `
    --min-sdk-version 26 `
    --target-sdk-version 34 `
    "$out\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link zlyhal" }

Write-Host "3/6 preklad (javac)"
$zdroje = @()
$zdroje += Get-ChildItem -Recurse -Filter *.java (Join-Path $proj "java") | ForEach-Object { $_.FullName }
$zdroje += Get-ChildItem -Recurse -Filter R.java "$out\gen" | ForEach-Object { $_.FullName }
& javac -source 8 -target 8 -nowarn -bootclasspath $androidJar -d "$out\classes" $zdroje
if ($LASTEXITCODE -ne 0) { throw "javac zlyhal" }

Write-Host "4/6 dex (d8)"
$triedy = Get-ChildItem -Recurse -Filter *.class "$out\classes" | ForEach-Object { $_.FullName }
& "$buildTools\d8.bat" --lib $androidJar --min-api 23 --output "$out\dex" $triedy
if ($LASTEXITCODE -ne 0) { throw "d8 zlyhal" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\base.apk", "Update")
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, "$out\dex\classes.dex", "classes.dex", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally {
    $zip.Dispose()
}

Write-Host "5/6 zarovnanie (zipalign)"
& "$buildTools\zipalign.exe" -f 4 "$out\base.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign zlyhal" }

Write-Host "6/6 podpis (apksigner)"
$ks = Join-Path $proj "dochadzka.jks"
if (-not (Test-Path $ks)) {
    $keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
    if (-not $keytool) {
        $kandidati = @()
        if ($env:JAVA_HOME) { $kandidati += (Join-Path $env:JAVA_HOME "bin\keytool.exe") }
        $kandidati += (Get-ChildItem "C:\Program Files\Java\jdk-*\bin\keytool.exe" -ErrorAction SilentlyContinue |
                        Sort-Object FullName -Descending | ForEach-Object { $_.FullName })
        $kandidati += "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
        $keytool = $kandidati | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    }
    if (-not $keytool) { throw "Nenašiel som keytool (stačí nainštalovať JDK alebo použiť Android Studio)." }

    & $keytool -genkeypair -keystore $ks -alias dochadzka -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass dochadzka -keypass dochadzka `
        -dname "CN=Dochadzka, O=Zdenoiva, C=SK" -noprompt
    if ($LASTEXITCODE -ne 0) { throw "keytool zlyhal" }
}
& "$buildTools\apksigner.bat" sign --ks $ks --ks-pass pass:dochadzka --key-pass pass:dochadzka `
    --out $apk "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner zlyhal" }

& "$buildTools\apksigner.bat" verify --print-certs $apk
Write-Host ""
Write-Host "Hotovo: $apk ($([math]::Round((Get-Item $apk).Length / 1KB)) kB)"
