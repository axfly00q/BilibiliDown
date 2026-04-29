param([string]$pw='78955ee80943')
$base='http://127.0.0.1:8787'
$sess=New-Object Microsoft.PowerShell.Commands.WebRequestSession
$script:fail=0
function Check($name,$expected,$actual){
  if($actual -eq $expected){ Write-Host "[OK]   $name => $actual" -ForegroundColor Green }
  else { Write-Host "[FAIL] $name expected=$expected actual=$actual" -ForegroundColor Red; $script:fail++ }
}

# 1) unauth
try{ $r=Invoke-WebRequest "$base/api/download/list" -UseBasicParsing -ErrorAction Stop; Check 'unauth /api' 401 $r.StatusCode }
catch { Check 'unauth /api' 401 $_.Exception.Response.StatusCode.value__ }

# 2) wrong login
try{ Invoke-WebRequest "$base/api/auth/login" -Method POST -Body '{"username":"admin","password":"wrong"}' -ContentType 'application/json' -WebSession $sess -UseBasicParsing -ErrorAction Stop | Out-Null; Check 'wrong login' 401 200 }
catch { Check 'wrong login' 401 $_.Exception.Response.StatusCode.value__ }

# 3) login correct
$r=Invoke-WebRequest "$base/api/auth/login" -Method POST -Body "{`"username`":`"admin`",`"password`":`"$pw`"}" -ContentType 'application/json' -WebSession $sess -UseBasicParsing
Check 'login' 200 $r.StatusCode

# 4) me
$r=Invoke-WebRequest "$base/api/auth/me" -WebSession $sess -UseBasicParsing
Check 'me' 200 $r.StatusCode

# 5) quality list contains 800/801
$q=(Invoke-WebRequest "$base/api/quality/list" -WebSession $sess -UseBasicParsing).Content
if($q -match '"qn":800' -and $q -match '"qn":801'){ Write-Host "[OK]   quality list has 800/801" -ForegroundColor Green }
else { Write-Host "[FAIL] quality list missing 800/801" -ForegroundColor Red; $script:fail++ }

# 6) parse
$p=(Invoke-WebRequest "$base/api/parse?input=BV1GJ411x7h7" -WebSession $sess -UseBasicParsing).Content
if($p -match '"avId"' -and $p -match '"clips"'){ Write-Host "[OK]   parse" -ForegroundColor Green }
else { Write-Host "[FAIL] parse" -ForegroundColor Red; $script:fail++ }

# 7) submit qn=16 (低清晰度，确保不在 RepoUtil 内存去重列表里)
$r=Invoke-WebRequest "$base/api/download/submit" -Method POST -ContentType 'application/json' -Body '{"avId":"BV1GJ411x7h7","cid":"137649199","qn":"16"}' -WebSession $sess -UseBasicParsing
Check 'submit qn=16' 200 $r.StatusCode
# 等任务面板加入 (queryThreadPool 异步)
$found=$false
for($i=0;$i -lt 20;$i++){
  Start-Sleep -Milliseconds 500
  $tmp=(Invoke-WebRequest "$base/api/download/list" -WebSession $sess -UseBasicParsing).Content
  if($tmp -match '"id":"BV1GJ411x7h7-16-p1"'){ $found=$true; break }
}

# 8) list
$l=(Invoke-WebRequest "$base/api/download/list" -WebSession $sess -UseBasicParsing).Content
if($l -match '"id":"BV1GJ411x7h7-16-p1"'){ Write-Host "[OK]   list has task" -ForegroundColor Green }
else { Write-Host "[FAIL] list missing task: $l" -ForegroundColor Red; $script:fail++ }

# 9) single pause
$r=Invoke-WebRequest "$base/api/download/BV1GJ411x7h7-16-p1/pause" -Method POST -WebSession $sess -UseBasicParsing
Check 'single pause' 200 $r.StatusCode

# 10) all/pause/resume
$r=Invoke-WebRequest "$base/api/download/all/pause" -Method POST -WebSession $sess -UseBasicParsing
Check 'all/pause' 200 $r.StatusCode
$r=Invoke-WebRequest "$base/api/download/all/resume" -Method POST -WebSession $sess -UseBasicParsing
Check 'all/resume' 200 $r.StatusCode

# 11) cover save
$r=Invoke-WebRequest "$base/api/cover/save?avId=BV1GJ411x7h7" -WebSession $sess -UseBasicParsing
Check 'cover save' 200 $r.StatusCode
$j=$r.Content | ConvertFrom-Json
if($j.data.size -gt 1000){ Write-Host "[OK]   cover size=$($j.data.size)" -ForegroundColor Green }
else { Write-Host "[FAIL] cover size=$($j.data.size)" -ForegroundColor Red; $script:fail++ }

# 12) /files
$r=Invoke-WebRequest "$base$($j.data.url)" -WebSession $sess -UseBasicParsing
Check 'cover via /files' 200 $r.StatusCode

# 13) console pages
foreach($pg in 'login.html','index.html','parse.html','downloads.html'){
  $r=Invoke-WebRequest "$base/console/$pg" -WebSession $sess -UseBasicParsing
  Check "console/$pg" 200 $r.StatusCode
}

# 14) console assets
$r=Invoke-WebRequest "$base/console/js/api.js" -UseBasicParsing
Check 'console/js/api.js' 200 $r.StatusCode

# 15) SSE quick read
$req=[System.Net.HttpWebRequest]::Create("$base/sse/tasks")
$req.Method='GET'; $req.CookieContainer=$sess.Cookies; $req.Timeout=5000
$resp=$req.GetResponse()
$reader=New-Object System.IO.StreamReader($resp.GetResponseStream())
$buf=''; $st=[DateTime]::Now
while(([DateTime]::Now-$st).TotalSeconds -lt 3){
  if($reader.Peek() -ge 0){ $buf+=[char]$reader.Read() } else { Start-Sleep -Milliseconds 50 }
}
$reader.Close(); $resp.Close()
if($buf -match 'event: tasks'){ Write-Host "[OK]   SSE event: tasks" -ForegroundColor Green }
else { Write-Host "[FAIL] SSE no event: $buf" -ForegroundColor Red; $script:fail++ }

# 16) cleanup
$r=Invoke-WebRequest "$base/api/download/all/done" -Method POST -WebSession $sess -UseBasicParsing
Check 'all/done' 200 $r.StatusCode
$r=Invoke-WebRequest "$base/api/auth/logout" -Method POST -WebSession $sess -UseBasicParsing
Check 'logout' 200 $r.StatusCode

Write-Host ""
if($script:fail -eq 0){ Write-Host "========= ALL PASS =========" -ForegroundColor Green }
else { Write-Host "========= FAILURES: $script:fail =========" -ForegroundColor Red }
exit $script:fail
