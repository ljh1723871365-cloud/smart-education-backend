# 测试登录和题库列表API
$loginBody = @{
    username = "admin"
    password = "1234"
} | ConvertTo-Json

try {
    Write-Host "??????..."
    $loginResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $loginBody -ContentType "application/json"
    Write-Host "?????Token: $($loginResponse.token.Substring(0,30))..."
    
    $token = $loginResponse.token
    $headers = @{
        "Authorization" = "Bearer $token"
    }
    
    Write-Host "`n???? /api/admin/questions ??..."
    $questionsResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/questions" -Method GET -Headers $headers
    
    Write-Host "SUCCESS! ??? $($questionsResponse.Count) ???"
    if ($questionsResponse.Count -gt 0) {
        Write-Host "?????ID: $($questionsResponse[0].id), ??: $($questionsResponse[0].subject)"
    }
} catch {
    Write-Host "ERROR: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "HTTP Status: $statusCode"
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $responseBody = $reader.ReadToEnd()
            Write-Host "Response Body: $responseBody"
        } catch {
            Write-Host "???????"
        }
    }
}
