# Vygeneruje launcher ikony (PNG) pre staršie Androidy z rovnakého dizajnu,
# aký má vektorová adaptívna ikona.
Add-Type -AssemblyName System.Drawing

function RoundedRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $cesta = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $cesta.AddArc($x, $y, $d, $d, 180, 90)
    $cesta.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $cesta.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $cesta.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $cesta.CloseFigure()
    return $cesta
}

function Vykresli([int]$velkost, [string]$ciel) {
    $bmp = New-Object System.Drawing.Bitmap($velkost, $velkost)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    $k = $velkost / 108.0
    $zelena = [System.Drawing.ColorTranslator]::FromHtml("#0B7A3B")
    $biela = [System.Drawing.Color]::White
    $stitok = New-Object System.Drawing.SolidBrush($zelena)
    $papier = New-Object System.Drawing.SolidBrush($biela)

    $g.FillPath($stitok, (RoundedRect 0 0 $velkost $velkost (22 * $k)))
    $g.FillPath($papier, (RoundedRect (36 * $k) (26 * $k) (12 * $k) (16 * $k) (4 * $k)))
    $g.FillPath($papier, (RoundedRect (60 * $k) (26 * $k) (12 * $k) (16 * $k) (4 * $k)))
    $g.FillPath($papier, (RoundedRect (25 * $k) (38 * $k) (58 * $k) (48 * $k) (6 * $k)))

    $pero = New-Object System.Drawing.Pen($zelena, (6 * $k))
    $pero.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pero.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pero.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $body = @(
        (New-Object System.Drawing.PointF((40 * $k), (62 * $k))),
        (New-Object System.Drawing.PointF((49 * $k), (71 * $k))),
        (New-Object System.Drawing.PointF((68 * $k), (52 * $k)))
    )
    $g.DrawLines($pero, [System.Drawing.PointF[]]$body)

    $bmp.Save($ciel, [System.Drawing.Imaging.ImageFormat]::Png)
    $pero.Dispose(); $papier.Dispose(); $stitok.Dispose(); $g.Dispose(); $bmp.Dispose()
}

$zaklad = $PSScriptRoot
$hustoty = @{ "mdpi" = 48; "hdpi" = 72; "xhdpi" = 96; "xxhdpi" = 144; "xxxhdpi" = 192 }
foreach ($h in $hustoty.Keys) {
    $priecinok = Join-Path $zaklad "res\mipmap-$h"
    New-Item -ItemType Directory -Force -Path $priecinok | Out-Null
    Vykresli $hustoty[$h] (Join-Path $priecinok "ic_launcher.png")
    "OK $h ($($hustoty[$h]) px)"
}
