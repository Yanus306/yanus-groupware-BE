$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$root = Join-Path $env:LOCALAPPDATA 'YanusBackup\Recovery'
$file = Join-Path $root 'identity.dpapi'
$scope = [System.Security.Cryptography.DataProtectionScope]::CurrentUser
$entropy = [Text.Encoding]::UTF8.GetBytes('yanus-db-backup-key-recovery-v1')
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
try {
    if ($Action -eq 'store') {
        $inputValue = [Console]::In.ReadToEnd().Trim()
        if ($inputValue.Length -gt 8192) { throw 'INPUT_TOO_LARGE' }
        $plain = [Convert]::FromBase64String($inputValue)
        $text = [Text.Encoding]::UTF8.GetString($plain)
        if ($text -notmatch '(?m)^AGE-SECRET-KEY-1[0-9A-Z]+\r?$') { throw 'INVALID_IDENTITY' }
        New-Item -ItemType Directory -Path $root -Force | Out-Null
        if ((Get-Item $root).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'REPARSE_POINT' }
        $acl = New-Object Security.AccessControl.DirectorySecurity
        $acl.SetOwner($sid)
        $acl.SetAccessRuleProtection($true, $false)
        foreach ($principal in @($sid, (New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))) {
            $rule = New-Object Security.AccessControl.FileSystemAccessRule($principal, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
            $acl.AddAccessRule($rule)
        }
        Set-Acl -Path $root -AclObject $acl
        if (Test-Path $file) {
            if ((Get-Item $file).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'REPARSE_POINT' }
            $previous = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($file), $entropy, $scope)
            if ([Convert]::ToBase64String($previous) -cne [Convert]::ToBase64String($plain)) { throw 'DIFFERENT_KEY_EXISTS' }
        } else {
            $protected = [Security.Cryptography.ProtectedData]::Protect($plain, $entropy, $scope)
            $temporary = Join-Path $root ([Guid]::NewGuid().ToString() + '.tmp')
            try {
                [IO.File]::WriteAllBytes($temporary, $protected)
                [IO.File]::Move($temporary, $file)
            } finally { if (Test-Path $temporary) { Remove-Item -LiteralPath $temporary -Force } }
        }
        $restored = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($file), $entropy, $scope)
        if ([Convert]::ToBase64String($restored) -cne [Convert]::ToBase64String($plain)) { throw 'ROUNDTRIP_FAILED' }
        @{ status = 'STORED'; protection = 'DPAPI_CURRENT_USER'; bytes = (Get-Item $file).Length } | ConvertTo-Json -Compress
    } elseif ($Action -eq 'recover') {
        foreach ($path in @($root, $file)) {
            if ((Get-Item $path).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'REPARSE_POINT' }
        }
        $plain = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($file), $entropy, $scope)
        [Console]::Out.Write([Convert]::ToBase64String($plain))
    } else { throw 'INVALID_ACTION' }
} catch {
    [Console]::Error.WriteLine('WINDOWS_KEY_RECOVERY_FAILED')
    exit 1
} finally {
    foreach ($value in @($plain, $previous, $restored)) {
        if ($value -is [byte[]]) { [Array]::Clear($value, 0, $value.Length) }
    }
}
